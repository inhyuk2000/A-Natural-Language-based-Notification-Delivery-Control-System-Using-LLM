# NotiLLM FastAPI backend (PromptEngine v3)
# Build from backend/:
#   docker build -t notillm-api .
# Run:
#   docker run --rm -p 8000:8000 --env-file .env notillm-api

FROM python:3.12-slim-bookworm

WORKDIR /app

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    # v3 defaults (override with -e or --env-file)
    PROMPT_ENGINE_VERSION=v3 \
    INTENT_CLASSIFIER=auto \
    PENDING_RESOLVER=llm

# System deps for sentence-transformers / torch CPU wheels
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --upgrade pip \
    && pip install -r requirements.txt

# App code (includes app/common/models/intent_clf.joblib when present)
COPY app ./app

EXPOSE 8000

# Secrets (OPENAI_API_KEY, etc.) must come from env / --env-file — not baked into the image.
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
