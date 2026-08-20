"""
NotiLLM extract-rule API
Android PromptEngine LLM 구간을 LangChain으로 수행.

PROMPT_ENGINE_VERSION=v1 (기본) → app.v1.prompt_engine.handle
PROMPT_ENGINE_VERSION=v2         → app.v2.prompt_engine.handle (LangGraph LLM 라우팅)
PROMPT_ENGINE_VERSION=v3         → app.v3.prompt_engine.handle (경량 classifier + pending-first)
"""
from __future__ import annotations

import os

from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware

from app.schemas import ExtractRuleRequest, ExtractRuleResponse

load_dotenv()

_VERSION = os.getenv("PROMPT_ENGINE_VERSION", "v1").strip().lower()
if _VERSION == "v3":
    from app.v3.prompt_engine import handle
elif _VERSION == "v2":
    from app.v2.prompt_engine import handle
else:
    from app.v1.prompt_engine import handle

app = FastAPI(title="NotiLLM Extract Rule API", version="1.0.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/health")
def health():
    return {"status": "ok", "promptEngine": _VERSION}


@app.post("/v1/extract-rule", response_model=ExtractRuleResponse)
def extract_rule(body: ExtractRuleRequest):
    if not os.getenv("OPENAI_API_KEY"):
        raise HTTPException(status_code=500, detail="OPENAI_API_KEY is not set on the server")
    try:
        installed = [app_item.model_dump() for app_item in body.installedApps]
        result = handle(
            body.prompt.strip(),
            body.currentTime.strip(),
            installed_apps=installed,
            pending=body.pending,
            pending_original=body.pendingOriginal,
        )
        return ExtractRuleResponse(**result)
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e)) from e
