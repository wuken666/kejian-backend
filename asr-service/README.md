# ASR cloud deployment

This service performs transcription; it does not store recordings or generate AI feedback.
The Docker image uses Python 3.11 and CPU-only PyTorch. Models are downloaded from
ModelScope during the image build, including SenseVoiceSmall, streaming Paraformer,
FSMN-VAD and CAM++. Expect a substantially larger image and longer build than the API.
Model files and runtime secrets must not be committed to Git.

## WeChat Cloud Hosting

- Create a separate service named `kejian-asr` in the same environment as `kejian-api`.
- Repository: `wuken666/kejian-backend`, branch: `main`.
- Build directory: `asr-service` (no leading slash).
- Dockerfile: `Dockerfile`; container port: `8090`.
- Enable internal network access. Leave public access disabled for this first deployment.
- Set `ASR_SERVICE_TOKEN` to a new random secret of at least 32 characters.
  Do not reuse the database password, WeChat AppSecret or admin token.
- The image requires this secret and fails startup when it is missing.
- Run one worker. Start with one instance; measure memory and latency with real audio
  before choosing production capacity. No GPU is required for this CPU image.
- Model loading completes before Uvicorn listens. Allow sufficient startup grace in
  the platform health check (start with 180 seconds and inspect startup logs).
  The Docker HEALTHCHECK does not necessarily override the platform's health checks.

After the ASR instance is healthy, add these variables to a new Java API version:

```text
ASR_BASE_URL=http://<ASR internal service host>:<actual internal service port>
ASR_SERVICE_TOKEN=<same secret as the ASR service>
```

Use the internal endpoint shown by the console, not a container instance IP or
`127.0.0.1`. The service must be reachable from the API's network. Publish the
latest API code as well: older versions do not send the shared secret.

## Verification and limits

- `GET /health` is unauthenticated and returns HTTP 200 only after the offline
  model is ready. Its `streaming` field indicates streaming model readiness.
- `POST /transcribe` and `/transcribe-dialogue` require
  `Authorization: Bearer <ASR_SERVICE_TOKEN>`.
- WebSocket `/ws/transcribe` requires the same header, not a query-string secret.
  Do not place this service secret in the miniapp. Direct miniapp realtime access
  is not part of this deployment: it still requires an authenticated gateway or
  short-lived, user-bound credentials. Do not publish this endpoint unprotected.
- First verify a short recording through the Java API, then a full lesson.
  Set platform request timeouts according to measured inference time. Whole-lesson
  transcription can exceed gateway timeouts; async jobs may be necessary later.
- A successful container build does not verify transcription accuracy, memory
  capacity, WebSocket routing, reliable audio storage or AI feedback generation.
- Existing API audio files are still container-local; object storage is required
  before treating recordings as durable across deployments or multiple instances.

## Local checks

```sh
python -m pip install -r requirements-cpu.txt
python -m pip install -r requirements.txt httpx==0.28.1
python -m unittest test_service_auth.py test_service_runtime.py test_audio_processing.py
```

For local development only, `ASR_REQUIRE_TOKEN=false` permits the previous
unauthenticated loopback workflow. Never expose that mode publicly.
