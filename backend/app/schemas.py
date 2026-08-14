from __future__ import annotations

from typing import Any

from pydantic import BaseModel, Field


class InstalledApp(BaseModel):
    """기기 PackageManager 라벨 스냅샷 (수동 alias 없음)."""

    packageName: str = Field(..., min_length=1)
    labels: list[str] = Field(default_factory=list)


# 클라이언트가 서버에 보내야 하는 요청 데이터 형식임.
class ExtractRuleRequest(BaseModel):
    prompt: str = Field(..., min_length=1)
    currentTime: str = Field(..., description="ISO-8601 datetime used as 'now' in prompts")
    installedApps: list[InstalledApp] = Field(
        default_factory=list,
        description="Device-installed apps: packageName + labels for cosine mapping",
    )


# 서버가 클라이언트에게 반환할 데이터 형식임.
class ExtractRuleResponse(BaseModel):
    ok: bool
    targetFixed: dict[str, Any] | None = None
    condition: dict[str, Any] | None = None
    assistantMessage: str = ""
    # 디버그용 cosine 매칭 상세. eval GT에 넣지 않으면 채점 대상 아님.
    mappingScores: list[dict[str, Any]] | None = None
