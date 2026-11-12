import os
import unittest
import asyncio
import io
import wave
from unittest.mock import patch

import numpy as np
from fastapi.testclient import TestClient
from starlette.datastructures import UploadFile

import main


class ServiceRuntimeTests(unittest.TestCase):
    def test_health_reports_unready_before_models_load(self):
        with patch.object(main, "model", None):
            response = TestClient(main.app).get("/health")
        self.assertEqual(503, response.status_code)

    def test_health_reports_streaming_readiness_separately(self):
        with patch.object(main, "model", object()), patch.object(main, "dialogue_model", object()), patch.object(main, "streaming_model", None):
            response = TestClient(main.app).get("/health")
        self.assertEqual({"status": "ready", "streaming": False, "dialogue": True}, response.json())

    def test_dialogue_pipeline_must_be_ready(self):
        with patch.object(main, "model", object()), patch.object(main, "dialogue_model", None):
            self.assertEqual(503, TestClient(main.app).get("/health").status_code)
            with self.assertRaises(RuntimeError):
                main.get_dialogue_model()

    def test_first_whole_recording_uses_pipeline_loaded_during_startup(self):
        from unittest.mock import Mock
        offline, dialogue, streaming = Mock(), Mock(), Mock()
        dialogue.generate.return_value = [{"text": "This is a test lesson."}]
        payload = io.BytesIO()
        with wave.open(payload, "wb") as output:
            output.setnchannels(1)
            output.setsampwidth(2)
            output.setframerate(16000)
            output.writeframes((np.sin(np.arange(16000) * 0.08) * 3000).astype("<i2").tobytes())

        async def exercise():
            async with main.lifespan(main.app):
                self.assertIs(dialogue, main.get_dialogue_model())
                self.assertTrue(main.health()["dialogue"])
                upload = UploadFile(filename="test.wav", file=io.BytesIO(payload.getvalue()))
                try:
                    result = await main.transcribe_dialogue(audio=[upload], hotwords="[]")
                finally:
                    await upload.close()
                self.assertEqual("This is a test lesson.", result.text)

        with patch.dict(os.environ, {"ASR_REQUIRE_TOKEN": "false", "ASR_SERVICE_TOKEN": ""}), \
                patch.object(main, "AutoModel", side_effect=[offline, dialogue, streaming]) as constructor, \
                patch.object(main.torch, "set_num_threads"), \
                patch.object(main.torch.cuda, "is_available", return_value=False):
            asyncio.run(exercise())
        self.assertEqual(3, constructor.call_count)
        self.assertTrue(all(call.kwargs["ncpu"] == main.MODEL_THREADS for call in constructor.call_args_list))
        dialogue.generate.assert_called_once()
        self.assertFalse(main.Path(dialogue.generate.call_args.kwargs["input"]).exists())

    def test_normal_transcription_runs_with_service_authentication(self):
        token = "test-only-token-" + "x" * 32
        samples = (np.sin(np.arange(1600) * 0.08) * 3000).astype("<i2")
        with patch.dict(os.environ, {"ASR_REQUIRE_TOKEN": "true", "ASR_SERVICE_TOKEN": token}), \
                patch.object(main, "decode_audio_pcm", return_value=samples), \
                patch.object(main, "transcribe_file", return_value=main.TranscriptionResponse(
                    text="test", inference_seconds=0.1,
                    audio_quality="NORMAL", rms=0.05, clipping_ratio=0,
                )) as inference:
            response = TestClient(main.app).post(
                "/transcribe", headers={"Authorization": "Bearer " + token},
                files={"audio": ("sample.wav", b"test", "audio/wav")},
            )
        self.assertEqual(200, response.status_code)
        self.assertEqual("test", response.json()["text"])
        inference.assert_called_once()
        self.assertFalse(inference.call_args.args[0].exists())


if __name__ == "__main__":
    unittest.main()
