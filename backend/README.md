# NotiLLM LangChain backend — PromptEngine LLM extraction

## Requirements

- **Python 3.11 or 3.12** (not 3.14 — many wheels missing, MSVC build errors)

## Setup

```bash
cd backend
uv python install 3.12
uv venv .venv --python 3.12
# Windows
.venv\Scripts\activate
uv pip install -r requirements.txt
# or: pip install -r requirements.txt

copy .env.example .env
# edit .env → OPENAI_API_KEY=...
```

## Run

```bash
cd backend
.venv\Scripts\activate
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

- Health: `GET http://127.0.0.1:8000/health`
- Extract: `POST http://127.0.0.1:8000/v1/extract-rule`

```json
{
  "prompt": "카톡 5분동안 받지마",
  "currentTime": "2026-08-12T15:00:00+09:00"
}
```

## Android

`local.properties`:

```
EXTRACT_RULE_API_BASE_URL=http://10.0.2.2:8000
```

- Emulator → host machine: `http://10.0.2.2:8000`
- Physical device → PC LAN IP, e.g. `http://192.168.0.10:8000`

App calls this API from `PromptEngine`; SQLite / `ContextManager` stay on device.
