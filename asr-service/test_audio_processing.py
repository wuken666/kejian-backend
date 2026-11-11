import tempfile
import unittest
import wave
from unittest.mock import patch
from pathlib import Path

import numpy as np

from main import (
    StreamingNoiseGate,
    analyze_audio_quality,
    clean_model_text,
    combine_audio,
    decode_audio_pcm,
    decode_lesson_audio,
    normalize_pcm16_bytes,
    suppress_background_noise,
)


class AudioProcessingTests(unittest.TestCase):
    def test_joins_legacy_webm_continuations_before_decoding(self):
        with tempfile.TemporaryDirectory() as directory:
            first = Path(directory) / "first.pcm"
            continuation = Path(directory) / "tail.pcm"
            next_stream = Path(directory) / "next.pcm"
            first.write_bytes(b"\x1a\x45\xdf\xa3first")
            continuation.write_bytes(b"continuation")
            next_stream.write_bytes(b"\x1a\x45\xdf\xa3next")
            decoded_payloads = []
            def decode(path):
                decoded_payloads.append(path.read_bytes())
                return np.array([1, 2], dtype="<i2")
            with patch("main.decode_audio_pcm", side_effect=decode):
                result = list(decode_lesson_audio([first, continuation, next_stream]))
            self.assertEqual(2, len(result))
            self.assertEqual([first.read_bytes() + continuation.read_bytes(), next_stream.read_bytes()], decoded_payloads)
            self.assertTrue(first.exists() and continuation.exists())

    def test_decodes_container_even_when_filename_says_pcm(self):
        samples = (np.sin(np.arange(1600) * 0.08) * 3000).astype("<i2")
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "container.pcm"
            with wave.open(str(path), "wb") as output:
                output.setnchannels(1)
                output.setsampwidth(2)
                output.setframerate(16000)
                output.writeframes(samples.tobytes())
            np.testing.assert_array_equal(samples, decode_audio_pcm(path))

    def test_marks_silent_audio_without_sending_it_to_the_model(self):
        metrics = analyze_audio_quality(np.zeros(16_000, dtype="<i2"))

        self.assertEqual("SILENT", metrics.quality)
        self.assertLess(metrics.rms, 0.0015)

    def test_marks_sustained_clipped_noise_as_distorted(self):
        samples = np.resize(np.array([32767, -32768], dtype="<i2"), 16_000)

        metrics = analyze_audio_quality(samples)

        self.assertEqual("DISTORTED", metrics.quality)
        self.assertGreater(metrics.rms, 0.48)
        self.assertGreater(metrics.clipping_ratio, 0.012)

    def test_keeps_normal_speech_like_audio_available_for_recognition(self):
        timeline = np.arange(16_000, dtype=np.float32) / 16_000
        samples = (0.08 * np.sin(2 * np.pi * 220 * timeline) * 32768).astype("<i2")

        metrics = analyze_audio_quality(samples)

        self.assertEqual("NORMAL", metrics.quality)

    def test_discards_audio_event_without_speech(self):
        self.assertEqual("", clean_model_text("<|nospeech|><|BGM|><|NEUTRAL|>"))

    def test_keeps_actual_speech_content(self):
        text = clean_model_text("<|zh|><|NEUTRAL|><|Speech|><|withitn|>老师讲解了定语从句")

        self.assertEqual("老师讲解了定语从句", text)

    def test_removes_incomplete_model_control_tag(self):
        text = clean_model_text("<|withitn|英语课堂")

        self.assertEqual("英语课堂", text)

    def test_discards_single_character_noise_fragment(self):
        self.assertEqual("", clean_model_text("<|zh|><|Speech|><|withitn|>. . 我。"))

    def test_keeps_valid_single_character_answer(self):
        self.assertEqual("A.", clean_model_text("<|en|><|Speech|><|withitn|>A."))
        self.assertEqual("对。", clean_model_text("<|zh|><|Speech|><|withitn|>对。"))

    def test_discards_repetitive_noise_hallucination(self):
        text = clean_model_text(
            "<|zh|><|NEUTRAL|><|Speech|><|withitn|>"
            "啊好好嗯嗯啊啊啊嗯嗯对不不不不不对不不不不啊啊儿儿儿儿儿儿儿儿"
        )

        self.assertEqual("", text)

    def test_collapses_local_stutter_without_dropping_sentence(self):
        text = clean_model_text(
            "<|zh|><|NEUTRAL|><|Speech|><|withitn|>"
            "我我我插入一行，然后讲解定语从句。"
        )

        self.assertEqual("我插入一行，然后讲解定语从句。", text)

    def test_keeps_repeated_words_when_sentence_has_real_information(self):
        text = clean_model_text(
            "<|zh|><|NEUTRAL|><|Speech|><|withitn|>"
            "不对不对，这里应该先计算括号里面的内容。"
        )

        self.assertEqual("不对不对，这里应该先计算括号里面的内容。", text)

    def test_streaming_gate_silences_low_energy_background_noise(self):
        random = np.random.default_rng(11)
        noise = random.normal(0, 0.0018, 3_200).astype(np.float32)
        gate = StreamingNoiseGate()

        filtered = gate.process(noise)

        self.assertLess(float(np.sqrt(np.mean(filtered * filtered))), 0.0001)

    def test_streaming_gate_keeps_quiet_speech_like_signal(self):
        timeline = np.arange(3_200, dtype=np.float32) / 16_000
        quiet_speech = (
            0.009 * np.sin(2 * np.pi * 180 * timeline)
            + 0.004 * np.sin(2 * np.pi * 360 * timeline)
        ).astype(np.float32)
        gate = StreamingNoiseGate()

        filtered = gate.process(quiet_speech)

        original_rms = float(np.sqrt(np.mean(quiet_speech * quiet_speech)))
        filtered_rms = float(np.sqrt(np.mean(filtered * filtered)))
        self.assertGreater(filtered_rms, original_rms * 0.35)

    def test_streaming_gate_filters_noise_inside_a_large_recorder_frame(self):
        random = np.random.default_rng(23)
        noise = random.normal(0, 0.0018, 8_000).astype(np.float32)
        timeline = np.arange(8_000, dtype=np.float32) / 16_000
        speech = 0.05 * np.sin(2 * np.pi * 220 * timeline).astype(np.float32)
        gate = StreamingNoiseGate()

        filtered = gate.process(np.concatenate([noise, speech]))

        noise_rms = float(np.sqrt(np.mean(filtered[:8_000] * filtered[:8_000])))
        speech_rms = float(np.sqrt(np.mean(filtered[8_000:] * filtered[8_000:])))
        self.assertLess(noise_rms, 0.0001)
        self.assertGreater(speech_rms, 0.05 * 0.35 / np.sqrt(2))

    def test_normalizes_incomplete_pcm_sample_without_raising(self):
        samples = normalize_pcm16_bytes(b"\x01\x00\x02\x00\xff")

        np.testing.assert_array_equal(samples, np.array([1, 2], dtype="<i2"))

    def test_reduces_low_energy_background_noise(self):
        random = np.random.default_rng(7)
        low_noise = random.normal(0, 0.002, 16_000).astype(np.float32)
        timeline = np.arange(16_000, dtype=np.float32) / 16_000
        speech_like = 0.05 * np.sin(2 * np.pi * 220 * timeline)
        samples = np.concatenate([low_noise, speech_like + low_noise])
        pcm = np.clip(samples * 32768, -32768, 32767).astype("<i2")

        filtered = suppress_background_noise(pcm).astype(np.float32)

        original_noise_rms = np.sqrt(np.mean(pcm[:16_000].astype(np.float32) ** 2))
        filtered_noise_rms = np.sqrt(np.mean(filtered[:16_000] ** 2))
        original_speech_rms = np.sqrt(np.mean(pcm[16_000:].astype(np.float32) ** 2))
        filtered_speech_rms = np.sqrt(np.mean(filtered[16_000:] ** 2))
        self.assertLess(filtered_noise_rms, original_noise_rms * 0.5)
        self.assertGreater(filtered_speech_rms, original_speech_rms * 0.65)

    def test_combines_audio_without_container_timestamps(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            first = root / "first.wav"
            second = root / "second.wav"
            samples = (np.sin(np.arange(8_000) * 0.05) * 4_000).astype("<i2")
            for path in (first, second):
                with wave.open(str(path), "wb") as wav_file:
                    wav_file.setnchannels(1)
                    wav_file.setsampwidth(2)
                    wav_file.setframerate(16_000)
                    wav_file.writeframes(samples.tobytes())

            combined = combine_audio([first, second])
            try:
                with wave.open(str(combined), "rb") as wav_file:
                    self.assertEqual(16_000, wav_file.getframerate())
                    self.assertEqual(16_000, wav_file.getnframes())
            finally:
                combined.unlink(missing_ok=True)

    def test_reads_wechat_raw_pcm_segment(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "wechat-segment.pcm"
            samples = np.array([-1200, 0, 1600, 3200], dtype="<i2")
            path.write_bytes(samples.tobytes())

            decoded = decode_audio_pcm(path)

            np.testing.assert_array_equal(samples, decoded)

    def test_combines_wechat_pcm_segments(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            first = root / "first.pcm"
            second = root / "second.pcm"
            samples = (np.sin(np.arange(8_000) * 0.05) * 4_000).astype("<i2")
            first.write_bytes(samples.tobytes())
            second.write_bytes(samples.tobytes())

            combined = combine_audio([first, second])
            try:
                with wave.open(str(combined), "rb") as wav_file:
                    self.assertEqual(16_000, wav_file.getframerate())
                    self.assertEqual(16_000, wav_file.getnframes())
            finally:
                combined.unlink(missing_ok=True)


if __name__ == "__main__":
    unittest.main()
