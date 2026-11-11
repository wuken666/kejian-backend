"""Download model weights into the image, not during container startup."""

import os

from modelscope import snapshot_download


MODELS = (
    ("iic/SenseVoiceSmall", "FUNASR_MODEL_DIR"),
    ("iic/speech_paraformer_asr_nat-zh-cn-16k-common-vocab8404-online", "FUNASR_STREAMING_MODEL_DIR"),
    ("iic/speech_fsmn_vad_zh-cn-16k-common-pytorch", "FUNASR_VAD_MODEL_DIR"),
    ("iic/speech_campplus_sv_zh-cn_16k-common", "FUNASR_SPEAKER_MODEL_DIR"),
)


if __name__ == "__main__":
    for model_id, variable in MODELS:
        snapshot_download(model_id, local_dir=os.environ[variable])
