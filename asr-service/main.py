"""Local FunASR service for transient classroom-audio transcription."""

from __future__ import annotations

import os
import re
import tempfile
import time
import asyncio
import json
import logging
import wave
from contextlib import asynccontextmanager
from pathlib import Path

import torch
import av
import numpy as np
from fastapi import FastAPI, File, Form, HTTPException, UploadFile, WebSocket, WebSocketDisconnect
from funasr import AutoModel
from funasr.utils.postprocess_utils import rich_transcription_postprocess
from pydantic import BaseModel

MODEL_DIR = os.environ.get("FUNASR_MODEL_DIR", "iic/SenseVoiceSmall")
STREAMING_MODEL_DIR = os.environ.get(
    "FUNASR_STREAMING_MODEL_DIR",
    "iic/speech_paraformer_asr_nat-zh-cn-16k-common-vocab8404-online",
)
MAX_AUDIO_BYTES = int(os.environ.get("ASR_MAX_AUDIO_BYTES", str(50 * 1024 * 1024)))
DIALOGUE_MAX_AUDIO_BYTES = int(
    os.environ.get("ASR_DIALOGUE_MAX_AUDIO_BYTES", str(250 * 1024 * 1024))
)
TAG_PATTERN = re.compile(r"<\|[^|>\r\n]+\|>?")
CHINESE_PATTERN = re.compile(r"[\u3400-\u9fff]")
NO_SPEECH_PATTERN = re.compile(r"<\|nospeech\|>", re.IGNORECASE)
SPEECH_PATTERN = re.compile(r"<\|Speech\|>", re.IGNORECASE)
NON_SPEECH_EVENT_PATTERN = re.compile(
    r"<\|(?:BGM|Laughter|Applause|Cry|Sneeze|Breath|Cough|Sing|Speech_Noise|GBG)\|>",
    re.IGNORECASE,
)
REPEATED_CHARACTER_PATTERN = re.compile(r"([\u3400-\u9fffA-Za-z])\1{2,}")
model: AutoModel | None = None
streaming_model: AutoModel | None = None
dialogue_model: AutoModel | None = None
streaming_lock = asyncio.Lock()
offline_lock = asyncio.Lock()
dialogue_lock = asyncio.Lock()
logger = logging.getLogger("teacher_feedback_asr")


class StreamingNoiseGate:
    """压低流式字幕中的稳定底噪，不改变最终保存和课后识别使用的原始音频。"""

    def __init__(self, initial_noise_floor: float = 0.0015) -> None:
        self.noise_floor = initial_noise_floor

    def process(self, samples: np.ndarray) -> np.ndarray:
        """按 20ms 子帧处理大块音频，低声人声使用柔和增益而非整块截断。"""
        if samples.size == 0:
            return samples.astype(np.float32, copy=False)
        audio = samples.astype(np.float32, copy=False)
        frame_size = 320  # 16kHz 下 20ms，避免一整秒 PCM 中的语音掩盖背景噪声。
        padding = (-audio.size) % frame_size
        padded = np.pad(audio, (0, padding))
        frames = padded.reshape(-1, frame_size)
        rms_values = np.sqrt(np.mean(frames * frames, axis=1) + 1e-10)
        gains = np.zeros(rms_values.shape, dtype=np.float32)
        for index, rms in enumerate(rms_values):
            open_threshold = max(0.0025, self.noise_floor * 1.8)
            if rms < open_threshold:
                self.noise_floor = 0.96 * self.noise_floor + 0.04 * float(rms)
                continue
            # 只做保守衰减，避免把较轻的老师或学生声音当成噪声完全切掉。
            gains[index] = np.clip(
                (float(rms) - open_threshold) / max(float(rms), 1e-5),
                0.35,
                1.0,
            )
        filtered = (frames * gains[:, None]).reshape(-1)
        return filtered[:audio.size]


class TranscriptionResponse(BaseModel):
    text: str
    inference_seconds: float
    audio_quality: str
    rms: float
    clipping_ratio: float


class AudioQualityMetrics(BaseModel):
    quality: str
    rms: float
    clipping_ratio: float


class SpeakerSegment(BaseModel):
    speaker: int
    start_ms: int
    end_ms: int
    text: str


class DialogueTranscriptionResponse(BaseModel):
    text: str
    segments: list[SpeakerSegment]
    inference_seconds: float
    speaker_separation_reliable: bool


def normalize_hotwords(values: list[str] | None) -> list[str]:
    """清理并限制热词数量，避免异常参数拖慢识别后处理。"""
    unique: dict[str, str] = {}
    for candidate in values or []:
        term = " ".join(str(candidate).strip().split())
        if not term or len(term) > 30:
            continue
        unique.setdefault(term.casefold(), term)
        if len(unique) == 50:
            break
    return list(unique.values())


def parse_hotwords(raw_value: str) -> list[str]:
    """解析 Java multipart 传来的 JSON 热词数组。"""
    try:
        payload = json.loads(raw_value or "[]")
    except json.JSONDecodeError as error:
        raise HTTPException(status_code=422, detail="Hotwords must be a JSON array") from error
    if not isinstance(payload, list):
        raise HTTPException(status_code=422, detail="Hotwords must be a JSON array")
    return normalize_hotwords(payload)


def correction_hotwords(hotwords: list[str] | None) -> list[str]:
    """只模糊纠正较长中文术语，避免“英语”等短词被错误插入普通句子。"""
    return [
        term
        for term in normalize_hotwords(hotwords)
        if len(term) >= 3 and CHINESE_PATTERN.search(term)
    ]


def clean_model_text(raw_text: str) -> str:
    """过滤纯噪声事件和无意义符号，避免模型把环境声展示成乱码。"""
    if NO_SPEECH_PATTERN.search(raw_text) and not SPEECH_PATTERN.search(raw_text):
        return ""
    cleaned = rich_transcription_postprocess(TAG_PATTERN.sub("", raw_text).strip())
    meaningful = re.sub(r"[\W_]+", "", cleaned, flags=re.UNICODE)
    if NON_SPEECH_EVENT_PATTERN.search(raw_text) and len(meaningful) < 2:
        return ""
    punctuation_count = len(re.findall(r"[.。!！?？，,]", TAG_PATTERN.sub("", raw_text)))
    if len(meaningful) == 1 and punctuation_count >= 2:
        return ""
    if is_repetitive_hallucination(meaningful):
        return ""
    cleaned = REPEATED_CHARACTER_PATTERN.sub(r"\1", cleaned)
    meaningful = re.sub(r"[\W_]+", "", cleaned, flags=re.UNICODE)
    return cleaned if meaningful else ""


def is_repetitive_hallucination(text: str) -> bool:
    """识别长串低多样性重复字符，避免把持续环境声误当成课堂内容。"""
    characters = [character.casefold() for character in text if character.isalnum()]
    if len(characters) < 16:
        return False
    unique_ratio = len(set(characters)) / len(characters)
    most_common_ratio = max(characters.count(character) for character in set(characters)) / len(characters)
    return unique_ratio <= 0.35 and most_common_ratio >= 0.22


def transcribe_file(
    path: Path,
    hotwords: list[str] | None = None,
    audio_metrics: AudioQualityMetrics | None = None,
) -> TranscriptionResponse:
    if model is None:
        raise RuntimeError("FunASR model is not loaded")
    started = time.perf_counter()
    result = model.generate(
        input=str(path),
        language="auto",
        use_itn=True,
        batch_size_s=60,
        merge_vad=True,
        merge_length_s=15,
        postprocess_hotwords=correction_hotwords(hotwords),
        postprocess_hotword_threshold=0.92,
    )
    if torch.cuda.is_available():
        torch.cuda.synchronize()
    text = "\n".join(
        cleaned
        for item in result
        if item.get("text")
        and (cleaned := clean_model_text(str(item.get("text", ""))))
    ).strip()
    metrics = audio_metrics or AudioQualityMetrics(quality="UNKNOWN", rms=0, clipping_ratio=0)
    return TranscriptionResponse(
        text=text,
        inference_seconds=round(time.perf_counter() - started, 3),
        audio_quality=metrics.quality,
        rms=metrics.rms,
        clipping_ratio=metrics.clipping_ratio,
    )


def get_dialogue_model() -> AutoModel:
    """延迟加载说话人分离模型，避免影响实时字幕服务的启动速度。"""
    global dialogue_model
    if dialogue_model is None:
        device = "cuda" if torch.cuda.is_available() else "cpu"
        dialogue_model = AutoModel(
            model=MODEL_DIR,
            vad_model="fsmn-vad",
            vad_kwargs={"max_single_segment_time": 5000},
            spk_model="cam++",
            device=device,
            disable_update=True,
        )
    return dialogue_model


def transcribe_dialogue_file(
    path: Path,
    hotwords: list[str] | None = None,
) -> DialogueTranscriptionResponse:
    """对整节课做双人说话人分离，返回供 AI 判断师生角色的分段原文。"""
    started = time.perf_counter()
    result = get_dialogue_model().generate(
        input=str(path),
        language="auto",
        use_itn=True,
        batch_size_s=60,
        merge_vad=False,
        preset_spk_num=2,
        postprocess_hotwords=correction_hotwords(hotwords),
        postprocess_hotword_threshold=0.92,
    )
    if torch.cuda.is_available():
        torch.cuda.synchronize()

    segments: list[SpeakerSegment] = []
    for item in result:
        for sentence in item.get("sentence_info", []):
            text = clean_model_text(
                str(sentence.get("sentence") or sentence.get("text") or "")
            )
            if not text:
                continue
            segments.append(
                SpeakerSegment(
                    speaker=int(sentence.get("spk", 0)),
                    start_ms=int(sentence.get("start", 0)),
                    end_ms=int(sentence.get("end", 0)),
                    text=text,
                )
            )

    separation_reliable = speaker_separation_reliable(segments)
    dialogue_text = "\n".join(
        (
            f"[说话人{segment.speaker}] {segment.text}"
            if separation_reliable
            else f"[角色待判断] {segment.text}"
        )
        for segment in segments
    ).strip()
    if not dialogue_text:
        dialogue_text = "\n".join(
            cleaned
            for item in result
            if item.get("text")
            and (cleaned := clean_model_text(str(item.get("text", ""))))
        ).strip()
    return DialogueTranscriptionResponse(
        text=dialogue_text,
        segments=segments,
        inference_seconds=round(time.perf_counter() - started, 3),
        speaker_separation_reliable=separation_reliable,
    )


def speaker_separation_reliable(segments: list[SpeakerSegment]) -> bool:
    """过滤极端失衡的声纹结果，避免把偶发噪声误认为第二位说话人。"""
    counts: dict[int, int] = {}
    for segment in segments:
        counts[segment.speaker] = counts.get(segment.speaker, 0) + 1
    if len(counts) < 2:
        return False
    minority_count = min(counts.values())
    return minority_count >= 3 and minority_count / len(segments) >= 0.03


def decode_audio_pcm(path: Path) -> np.ndarray:
    """把单个浏览器音频分片解码为 16k 单声道 PCM，隔离不同容器的时间戳。"""
    # 微信小程序以 16k 单声道 PCM16 小端格式直接落盘，不需要经过容器解码。
    with path.open("rb") as source:
        header = source.read(4)
    is_container = header in {b"\x1a\x45\xdf\xa3", b"RIFF", b"OggS"}
    if path.suffix.lower() == ".pcm" and not is_container:
        return normalize_pcm16_bytes(path.read_bytes())
    chunks: list[np.ndarray] = []
    with av.open(str(path)) as input_container:
        input_stream = input_container.streams.audio[0]
        resampler = av.audio.resampler.AudioResampler(
            format="s16",
            layout="mono",
            rate=16_000,
        )
        for frame in input_container.decode(input_stream):
            for resampled_frame in resampler.resample(frame):
                chunks.append(resampled_frame.to_ndarray().reshape(-1).copy())
        for resampled_frame in resampler.resample(None):
            chunks.append(resampled_frame.to_ndarray().reshape(-1).copy())
    return np.concatenate(chunks).astype("<i2", copy=False) if chunks else np.empty(0, dtype="<i2")


def normalize_pcm16_bytes(payload: bytes | bytearray | memoryview) -> np.ndarray:
    """安全解码 PCM16 字节，避免网络尾包不完整时让识别请求直接抛异常。"""
    raw = bytes(payload)
    if len(raw) % 2:
        logger.warning("Discarding incomplete PCM16 byte from ASR input: bytes=%s", len(raw))
        raw = raw[:-1]
    if not raw:
        return np.empty(0, dtype="<i2")
    return np.frombuffer(raw, dtype="<i2")


def suppress_background_noise(samples: np.ndarray) -> np.ndarray:
    """按短时能量压低稳定底噪，保留较弱人声并避免激进降噪造成吞字。"""
    if samples.size < 640:
        return samples.astype("<i2", copy=False)
    audio = samples.astype(np.float32) / 32768.0
    audio -= float(np.mean(audio))
    frame_size = 320
    padding = (-audio.size) % frame_size
    padded = np.pad(audio, (0, padding))
    frames = padded.reshape(-1, frame_size)
    rms = np.sqrt(np.mean(frames * frames, axis=1) + 1e-10)
    noise_floor = float(np.percentile(rms, 20))
    speech_level = float(np.percentile(rms, 85))
    if speech_level < 0.0015:
        return np.zeros(samples.shape, dtype="<i2")

    threshold = max(0.0012, min(noise_floor * 1.8, speech_level * 0.55))
    denominator = max(speech_level - threshold, 1e-5)
    gains = np.clip((rms - threshold) / denominator, 0.0, 1.0)
    gains = 0.16 + 0.84 * np.sqrt(gains)
    gains = np.convolve(gains, np.ones(5, dtype=np.float32) / 5, mode="same")
    filtered = padded * np.repeat(gains, frame_size)
    filtered = filtered[:audio.size]
    return np.clip(filtered * 32768.0, -32768, 32767).astype("<i2")


def analyze_audio_quality(samples: np.ndarray) -> AudioQualityMetrics:
    """计算录音能量和削波比例，区分静音、明显失真与可继续识别的输入。"""
    if samples.size == 0:
        return AudioQualityMetrics(quality="SILENT", rms=0, clipping_ratio=0)
    normalized = samples.astype(np.float32) / 32768.0
    rms = float(np.sqrt(np.mean(normalized * normalized) + 1e-10))
    clipping_ratio = float(np.mean(np.abs(normalized) >= 0.98))
    if rms < 0.0015:
        quality = "SILENT"
    elif rms >= 0.48 and clipping_ratio >= 0.012:
        quality = "DISTORTED"
    else:
        quality = "NORMAL"
    return AudioQualityMetrics(
        quality=quality,
        rms=round(rms, 6),
        clipping_ratio=round(clipping_ratio, 6),
    )


def write_pcm_wav(path: Path, samples: np.ndarray) -> None:
    """把处理后的 PCM 直接写入 WAV，避免手机分片时间戳导致 PyAV 合并失败。"""
    with wave.open(str(path), "wb") as wav_file:
        wav_file.setnchannels(1)
        wav_file.setsampwidth(2)
        wav_file.setframerate(16_000)
        wav_file.writeframes(samples.astype("<i2", copy=False).tobytes())


def decode_lesson_audio(paths: list[Path]):
    # 早期开发工具把同一 WebM 流拆成多个 .pcm 文件，后续片段没有独立容器头。
    pending: list[Path] = []

    def decode_webm(parts: list[Path]):
        with tempfile.NamedTemporaryFile(suffix=".webm", delete=False) as output:
            merged = Path(output.name)
            for part in parts:
                output.write(part.read_bytes())
        try:
            return decode_audio_pcm(merged)
        finally:
            merged.unlink(missing_ok=True)

    for path in paths:
        with path.open("rb") as source:
            header = source.read(4)
        is_webm = header == b"\x1a\x45\xdf\xa3"
        if pending and not is_webm and header not in {b"RIFF", b"OggS"} and path.suffix.lower() in {".pcm", ".webm"}:
            pending.append(path)
            continue
        if pending:
            yield decode_webm(pending)
            pending = []
        if is_webm:
            pending = [path]
        else:
            yield decode_audio_pcm(path)
    if pending:
        yield decode_webm(pending)


def combine_audio(paths: list[Path]) -> Path:
    """按上传顺序拼接课堂分片，并统一转成 16k 单声道 WAV。"""
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as temp_file:
        combined_path = Path(temp_file.name)
    with wave.open(str(combined_path), "wb") as wav_file:
        wav_file.setnchannels(1)
        wav_file.setsampwidth(2)
        wav_file.setframerate(16_000)
        for decoded in decode_lesson_audio(paths):
            samples = suppress_background_noise(decoded)
            wav_file.writeframes(samples.tobytes())
    return combined_path


@asynccontextmanager
async def lifespan(_: FastAPI):
    global model, streaming_model, dialogue_model
    device = "cuda" if torch.cuda.is_available() else "cpu"
    model = AutoModel(
        model=MODEL_DIR,
        vad_model="fsmn-vad",
        vad_kwargs={"max_single_segment_time": 30000},
        device=device,
        disable_update=True,
    )
    try:
        streaming_model = AutoModel(
            model=STREAMING_MODEL_DIR,
            device=device,
            disable_update=True,
            disable_pbar=True,
        )
    except Exception:
        logger.exception("Streaming ASR model failed to load")
        streaming_model = None
    yield
    model = None
    streaming_model = None
    dialogue_model = None


app = FastAPI(title="Teacher Feedback ASR", lifespan=lifespan)


@app.get("/health")
def health() -> dict[str, str | bool]:
    return {
        "status": "ready" if model is not None else "loading",
        "streaming": streaming_model is not None,
    }


def pcm_to_wav(pcm_bytes: bytes) -> Path:
    """将浏览器发送的 16k 单声道 PCM 写成临时 WAV，供句末高精度纠错。"""
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as temp_file:
        wav_path = Path(temp_file.name)
    samples = normalize_pcm16_bytes(pcm_bytes)
    write_pcm_wav(wav_path, suppress_background_noise(samples))
    return wav_path


async def correct_utterance(pcm_bytes: bytes, hotwords: list[str] | None = None) -> str:
    """对一句完整语音进行离线纠错，输出带标点的稳定文本。"""
    if len(pcm_bytes) < 3_200:
        return ""
    wav_path = pcm_to_wav(pcm_bytes)
    try:
        async with offline_lock:
            response = await asyncio.to_thread(transcribe_file, wav_path, hotwords)
        return response.text
    finally:
        wav_path.unlink(missing_ok=True)


@app.websocket("/ws/transcribe")
async def transcribe_stream(websocket: WebSocket) -> None:
    """接收实时 PCM 分片，返回临时字幕，并在句末用离线模型纠正。"""
    await websocket.accept()
    if streaming_model is None:
        await websocket.send_json({"type": "error", "message": "Streaming ASR model is unavailable"})
        await websocket.close(code=1013)
        return

    cache: dict = {}
    utterance = bytearray()
    partial_text = ""
    chunk_size = [0, 10, 5]
    noise_gate = StreamingNoiseGate()
    hotwords = normalize_hotwords(websocket.query_params.getlist("hotword"))
    try:
        while True:
            message = await websocket.receive()
            # 手机切后台或暂停录音会发送断开消息，正常结束连接而不是再次读取。
            if message.get("type") == "websocket.disconnect":
                return
            if message.get("bytes") is not None:
                pcm_bytes = message["bytes"]
                utterance.extend(pcm_bytes)
                samples = normalize_pcm16_bytes(pcm_bytes).astype(np.float32) / 32768.0
                if samples.size == 0:
                    continue
                filtered_samples = noise_gate.process(samples)
                if not np.any(filtered_samples):
                    continue
                async with streaming_lock:
                    result = await asyncio.to_thread(
                        streaming_model.generate,
                        input=filtered_samples,
                        cache=cache,
                        is_final=False,
                        chunk_size=chunk_size,
                        encoder_chunk_look_back=4,
                        decoder_chunk_look_back=1,
                    )
                text = "".join(str(item.get("text", "")) for item in result if item.get("text"))
                if text:
                    partial_text += text
                    await websocket.send_json({"type": "partial", "text": partial_text})
                continue

            payload = json.loads(message.get("text") or "{}")
            event = payload.get("event")
            if event in {"commit", "stop"}:
                final_text = await correct_utterance(bytes(utterance), hotwords)
                if final_text:
                    await websocket.send_json({"type": "final", "text": final_text})
                utterance.clear()
                partial_text = ""
                cache = {}
            if event == "stop":
                await websocket.close()
                return
    except WebSocketDisconnect:
        return


@app.post("/transcribe", response_model=TranscriptionResponse)
async def transcribe(
    audio: UploadFile = File(...),
    hotwords: str = Form("[]"),
) -> TranscriptionResponse:
    suffix = Path(audio.filename or "segment.webm").suffix.lower() or ".webm"
    if suffix not in {".wav", ".webm", ".m4a", ".mp3", ".ogg", ".pcm"}:
        raise HTTPException(status_code=415, detail="Unsupported audio format")

    payload = await audio.read(MAX_AUDIO_BYTES + 1)
    if not payload or len(payload) > MAX_AUDIO_BYTES:
        raise HTTPException(status_code=413, detail="Audio segment is empty or too large")

    with tempfile.NamedTemporaryFile(suffix=suffix, delete=False) as temp_file:
        temp_file.write(payload)
        temp_path = Path(temp_file.name)
    try:
        decoded = decode_audio_pcm(temp_path)
        audio_metrics = analyze_audio_quality(decoded)
        if audio_metrics.quality in {"SILENT", "DISTORTED"}:
            return TranscriptionResponse(
                text="",
                inference_seconds=0,
                audio_quality=audio_metrics.quality,
                rms=audio_metrics.rms,
                clipping_ratio=audio_metrics.clipping_ratio,
            )
        with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as normalized_file:
            normalized_path = Path(normalized_file.name)
        write_pcm_wav(normalized_path, suppress_background_noise(decoded))
        return transcribe_file(normalized_path, parse_hotwords(hotwords), audio_metrics)
    except av.error.InvalidDataError as error:
        raise HTTPException(status_code=422, detail="Audio segment is unreadable") from error
    finally:
        if 'normalized_path' in locals() and normalized_path != temp_path:
            normalized_path.unlink(missing_ok=True)
        temp_path.unlink(missing_ok=True)


@app.post("/transcribe-dialogue", response_model=DialogueTranscriptionResponse)
async def transcribe_dialogue(
    audio: list[UploadFile] = File(...),
    hotwords: str = Form("[]"),
) -> DialogueTranscriptionResponse:
    """接收整节课音频，在课后执行高精度转写和双人说话人分离。"""
    temp_paths: list[Path] = []
    total_bytes = 0
    try:
        for item in audio:
            suffix = Path(item.filename or "segment.webm").suffix.lower() or ".webm"
            if suffix not in {".wav", ".webm", ".m4a", ".mp3", ".ogg", ".pcm"}:
                raise HTTPException(status_code=415, detail="Unsupported audio format")
            payload = await item.read(DIALOGUE_MAX_AUDIO_BYTES + 1)
            total_bytes += len(payload)
            if not payload or total_bytes > DIALOGUE_MAX_AUDIO_BYTES:
                raise HTTPException(status_code=413, detail="Lesson audio is empty or too large")
            with tempfile.NamedTemporaryFile(suffix=suffix, delete=False) as temp_file:
                temp_file.write(payload)
                temp_paths.append(Path(temp_file.name))
        if not temp_paths:
            raise HTTPException(status_code=422, detail="Lesson audio is empty")

        normalized_path = combine_audio(temp_paths)
        async with dialogue_lock:
            return await asyncio.to_thread(
                transcribe_dialogue_file,
                normalized_path,
                parse_hotwords(hotwords),
            )
    except av.error.InvalidDataError as error:
        raise HTTPException(status_code=422, detail="Lesson audio is unreadable") from error
    finally:
        if "normalized_path" in locals():
            normalized_path.unlink(missing_ok=True)
        for temp_path in temp_paths:
            temp_path.unlink(missing_ok=True)
