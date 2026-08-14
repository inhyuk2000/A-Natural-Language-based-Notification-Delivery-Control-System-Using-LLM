"""
NotiLLM extract-rule API
Android PromptEngine LLM 구간을 LangChain으로 수행.
"""
from __future__ import annotations

import os

from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware

from app.prompt_engine import handle
from app.schemas import ExtractRuleRequest, ExtractRuleResponse

load_dotenv()

app = FastAPI(title="NotiLLM Extract Rule API", version="1.0.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# health router는 status 확인용.
@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/v1/extract-rule", response_model=ExtractRuleResponse)
def extract_rule(body: ExtractRuleRequest):
    if not os.getenv("OPENAI_API_KEY"):
        raise HTTPException(status_code=500, detail="OPENAI_API_KEY is not set on the server")
    try:
        installed = [app.model_dump() for app in body.installedApps]
        result = handle(
            body.prompt.strip(),
            body.currentTime.strip(),
            installed_apps=installed,
        )
        return ExtractRuleResponse(**result)
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e)) from e
