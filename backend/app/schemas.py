from __future__ import annotations

from typing import Any

from pydantic import BaseModel, Field

# 클라이언트가 서버에 보내야 하는 요청 데이터 형식임.
class ExtractRuleRequest(BaseModel):
    prompt: str = Field(..., min_length=1)
    currentTime: str = Field(..., description="ISO-8601 datetime used as 'now' in prompts")

# 서버가 클라이언트에게 반환할 데이터 형식임.
class ExtractRuleResponse(BaseModel):
    ok: bool
    targetFixed: dict[str, Any] | None = None
    condition: dict[str, Any] | None = None
    assistantMessage: str = ""
