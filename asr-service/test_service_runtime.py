import os
import unittest
from unittest.mock import patch

import numpy as np
from fastapi.testclient import TestClient

import main


class ServiceRuntimeTests(unittest.TestCase):
    def test_health_reports_unready_before_models_load(self):
        with patch.object(main, "model", None):
            response = TestClient(main.app).get("/health")
        self.assertEqual(503, response.status_code)

    def test_health_reports_streaming_readiness_separately(self):
        with patch.object(main, "model", object()), patch.object(main, "streaming_model", None):
            response = TestClient(main.app).get("/health")
        self.assertEqual({"status": "ready", "streaming": False}, response.json())

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
