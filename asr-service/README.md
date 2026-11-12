# ASR cloud deployment

This service performs transcription; it does not store recordings or generate AI feedback.
The Docker image uses Python 3.11 and CPU-only PyTorch. Models are downloaded from
ModelScope during the image build, including SenseVoiceSmall, streaming Paraformer,
FSMN-VAD and CAM++. Expect a substantially larger image and longer build than the API.
Model files and runtime secrets must not be committed to Git.

## WeChat Cloud Hosting

### Recommended: build the image on GitHub

When the cloud source builder stalls on PyTorch downloads, open the repository's
Actions tab, choose **Build ASR Image**, then **Run workflow** on `main`.
This workflow is manual-only; pushing code does not launch it or deploy services.
GitHub Actions usage and package storage may consume the account's plan allowance.

The workflow builds `asr-service/Dockerfile` for Linux amd64, starts a container
with an ephemeral test secret, verifies offline/streaming model readiness and
authentication, then pushes the verified image to GitHub Container Registry.
It also submits a public FunASR sample recording to `/transcribe-dialogue` and
requires nonempty recognized text within 120 seconds before publishing the image.
It uses GitHub's automatic token; do not add production secrets to the workflow.
The final run summary contains the immutable `sha-...` image tag to deploy.

The workflow does not make packages public. New GHCR packages are private by
default. Before cloud deployment, configure authorized registry access or mirror
the image into an authorized Tencent registry. If using anonymous image-address
pulling, explicitly decide whether to make this image package public; do not
make the source repository public just to allow image pulls. Never put database,
WeChat, admin or ASR credentials in an image. Reachability of GHCR from the cloud
still needs verification; a Tencent registry mirror may be needed.

In the cloud console, use image deployment instead of source building, with the
verified image address, port `8090`, internal access and the runtime secret below.
Keep the previous working Java API and its database settings unchanged.

### Alternative: cloud source build

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
- Offline, dialogue (including speaker separation) and streaming pipelines are
  loaded before readiness. The first classroom request must not construct models.
  The dialogue pipeline still has its own recognition weights; this intentionally
  preserves existing inference behavior and does not claim to eliminate all copies.
  `ASR_MODEL_THREADS` defaults to 2; measure memory after all pipelines are loaded.
  Startup failure is preferable to reporting ready and hanging on the first request.
- Search `asr_startup` for model initialization stages and `asr_dialogue` for
  receive/decode/queue/inference timing. These logs do not include audio or text.

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
