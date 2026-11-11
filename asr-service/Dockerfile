FROM python:3.11-slim-bookworm

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    ASR_REQUIRE_TOKEN=true \
    OMP_NUM_THREADS=2 \
    MKL_NUM_THREADS=2 \
    FUNASR_MODEL_DIR=/opt/models/sensevoice \
    FUNASR_STREAMING_MODEL_DIR=/opt/models/streaming \
    FUNASR_VAD_MODEL_DIR=/opt/models/vad \
    FUNASR_SPEAKER_MODEL_DIR=/opt/models/speaker

WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates libgomp1 ffmpeg \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt requirements-cpu.txt ./
RUN python -m pip install -r requirements-cpu.txt \
    && python -m pip install -r requirements.txt \
    && python -m pip check

COPY prepare_models.py ./
RUN python prepare_models.py

COPY main.py service_auth.py ./
RUN useradd --create-home --uid 10001 asr \
    && chmod -R a+rX /opt/models
USER asr

EXPOSE 8090
HEALTHCHECK --interval=30s --timeout=5s --start-period=180s --retries=3 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8090/health', timeout=3)"
CMD ["python", "-m", "uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8090", "--workers", "1", "--limit-concurrency", "8", "--ws-max-size", "1048576", "--no-access-log"]
