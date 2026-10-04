# Kinfolk transcription worker for RunPod Serverless (ADR 0005). Built and pushed by scripts/go-live.sh.
FROM nvidia/cuda:12.4.1-cudnn-runtime-ubuntu22.04
RUN apt-get update && apt-get install -y --no-install-recommends python3 python3-pip ffmpeg && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY requirements.txt .
RUN pip3 install --no-cache-dir -r requirements.txt
# Weights in the image so a cold start doesn't download ~20 GB. pyannote's are gated (HF_TOKEN) and small: fetched at start.
ENV HF_HOME=/models
RUN python3 -c "from huggingface_hub import snapshot_download as d; d('Systran/faster-whisper-large-v3'); d('Qwen/Qwen2.5-32B-Instruct-AWQ')"
COPY handler.py .
CMD ["python3", "-u", "handler.py"]
