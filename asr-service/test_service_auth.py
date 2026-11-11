import os
import unittest
from unittest.mock import patch

from fastapi import FastAPI, WebSocket
from fastapi.testclient import TestClient
from starlette.websockets import WebSocketDisconnect

from service_auth import ServiceTokenMiddleware, validate_service_token


class ServiceAuthTests(unittest.TestCase):
    def setUp(self):
        app = FastAPI()
        app.add_middleware(ServiceTokenMiddleware)

        @app.get("/health")
        def health():
            return {"status": "ready"}

        @app.post("/transcribe")
        @app.post("/transcribe-dialogue")
        def transcribe():
            return {"text": "test"}

        @app.websocket("/ws/transcribe")
        async def stream(websocket: WebSocket):
            await websocket.accept()
            await websocket.send_text("ready")
            await websocket.close()

        self.client = TestClient(app)
        self.token = "test-only-token-" + "x" * 32
        self.environment = patch.dict(os.environ, {
            "ASR_REQUIRE_TOKEN": "true", "ASR_SERVICE_TOKEN": self.token,
        })
        self.environment.start()
        self.addCleanup(self.environment.stop)

    def test_health_is_public_but_inference_requires_token(self):
        self.assertEqual(200, self.client.get("/health").status_code)
        for path in ("/transcribe", "/transcribe-dialogue"):
            self.assertEqual(401, self.client.post(path).status_code)
            self.assertEqual(401, self.client.post(path, headers={"Authorization": "Bearer wrong"}).status_code)
            self.assertEqual(200, self.client.post(path, headers={"Authorization": "Bearer " + self.token}).status_code)

    def test_websocket_requires_header_not_query_token(self):
        with self.assertRaises(WebSocketDisconnect):
            with self.client.websocket_connect("/ws/transcribe?token=" + self.token):
                pass
        with self.client.websocket_connect("/ws/transcribe", headers={"Authorization": "Bearer " + self.token}) as ws:
            self.assertEqual("ready", ws.receive_text())

    def test_missing_cloud_secret_fails_closed(self):
        with patch.dict(os.environ, {"ASR_SERVICE_TOKEN": ""}):
            with self.assertRaises(RuntimeError):
                validate_service_token()
            self.assertEqual(401, self.client.post("/transcribe").status_code)

    def test_existing_local_mode_can_run_without_secret(self):
        with patch.dict(os.environ, {"ASR_REQUIRE_TOKEN": "false", "ASR_SERVICE_TOKEN": ""}):
            validate_service_token()
            self.assertEqual(200, self.client.post("/transcribe").status_code)


if __name__ == "__main__":
    unittest.main()
