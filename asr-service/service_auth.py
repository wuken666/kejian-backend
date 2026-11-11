"""Shared-secret protection for server-to-server ASR requests."""

import hmac
import os

from starlette.responses import JSONResponse


def validate_service_token() -> None:
    token = os.environ.get("ASR_SERVICE_TOKEN", "").strip()
    required = os.environ.get("ASR_REQUIRE_TOKEN", "false").lower() == "true"
    if required and len(token) < 32:
        raise RuntimeError("ASR_SERVICE_TOKEN must contain at least 32 characters")


class ServiceTokenMiddleware:
    def __init__(self, app):
        self.app = app

    async def __call__(self, scope, receive, send):
        if scope["type"] not in {"http", "websocket"}:
            return await self.app(scope, receive, send)
        if scope["type"] == "http" and scope["path"] == "/health":
            return await self.app(scope, receive, send)
        token = os.environ.get("ASR_SERVICE_TOKEN", "").strip()
        required = os.environ.get("ASR_REQUIRE_TOKEN", "false").lower() == "true"
        headers = dict(scope.get("headers", []))
        supplied = headers.get(b"authorization", b"")
        if (required and len(token) < 32) or (
            token and not hmac.compare_digest(supplied, ("Bearer " + token).encode("utf-8"))
        ):
            if scope["type"] == "websocket":
                await send({"type": "websocket.close", "code": 1008})
            else:
                await JSONResponse({"detail": "Unauthorized"}, status_code=401)(scope, receive, send)
            return
        await self.app(scope, receive, send)
