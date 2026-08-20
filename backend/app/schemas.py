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
    # 재질문 대기 중이면 true. 직전 미완성 명령을 pendingOriginal 에 담아 보낸다.
    pending: bool = Field(
        default=False,
        description="True when the client is waiting for a clarification follow-up",
    )
    pendingOriginal: str | None = Field(
        default=None,
        description="Original user command that triggered clarification (required when pending)",
    )


# 서버가 클라이언트에게 반환할 데이터 형식임.
class ExtractRuleResponse(BaseModel):
    ok: bool
    targetFixed: dict[str, Any] | None = None
    condition: dict[str, Any] | None = None
    assistantMessage: str = ""
    # 디버그용 cosine 매칭 상세. eval GT에 넣지 않으면 채점 대상 아님.
    mappingScores: list[dict[str, Any]] | None = None
    # pending 턴에서 분류 결과 (supplement|new_command). 디버그/로그용.
    pendingClassification: str | None = None
    # ok=false 사유. Android는 needsSupplement=true 일 때만 pending 저장.
    # incomplete_rule | missing_time | missing_target | empty_allow_target
    # | app_unresolved | tool_conflict | chitchat | cancelled | none
    failReason: str | None = None
    needsSupplement: bool = False
    # v2/v3: chitchat|extract|reject
    dialogIntent: str | None = None
