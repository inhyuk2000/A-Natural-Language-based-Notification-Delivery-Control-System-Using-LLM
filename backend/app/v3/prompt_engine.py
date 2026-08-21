"""
PromptEngine v3 — Pending-first + lightweight intent classifier + LLM extract.

Flow:
  pending? → pending_resolver (gpt-4o-mini: CONTINUE/UPDATE/CANCEL/NEW_REQUEST/UNRESOLVED)
  else     → intent_classifier (extract|reject; no LLM)
               reject → immediate template (no LLM)
               extract → gpt-4o tools + code rule_validator
"""
from __future__ import annotations

import json
from typing import Any

from langchain_core.messages import AIMessage, HumanMessage, SystemMessage, ToolMessage
from langchain_openai import ChatOpenAI
from langsmith import traceable

from app.common.intent_classifier import classify_intent
from app.common.pending_resolver import (
    merge_pending_prompt,
    resolve_pending_action,
)
from app.common.rule_validator import validate_extraction
from app.v1.prompt_engine import (
    _parse_tool_calls,
    build_openai_tools,
    system_few_shot,
)

_REJECT_REPLIES = [
    "안녕하세요! 알림 규칙을 등록하려면 "
    "‘카톡 5분동안 받지마’처럼 시간과 대상을 함께 말씀해주세요.",
]

# 실패 응답 JSON 생성
def _fail(
    message: str,
    fail_reason: str,
    *,
    needs_supplement: bool = False,
    pending_classification: str | None = None,
    dialog_intent: str | None = None,
    mapping_scores: list[dict[str, Any]] | None = None,
) -> dict[str, Any]:
    out: dict[str, Any] = {
        "ok": False,
        "assistantMessage": message,
        "failReason": fail_reason,
        "needsSupplement": needs_supplement,
        "pendingClassification": pending_classification,
        "dialogIntent": dialog_intent,
    }
    if mapping_scores is not None:
        out["mappingScores"] = mapping_scores
    return out


def _reject_response() -> dict[str, Any]:
    return _fail(
        _REJECT_REPLIES[0],
        "chitchat",
        needs_supplement=False,
        dialog_intent="reject",
    )

# function calling 기능 + code rule_validator를 활용한 기존 LLM extractor
@traceable(name="v3_extract")
def run_extract(
    extract_prompt: str,
    current_time: str,
    installed_apps: list[dict[str, Any]] | None = None,
    *,
    pending_classification: str | None = None,
    dialog_intent: str | None = "extract",
) -> dict[str, Any]:
    """Existing LLM extractor (function calling) + code rule_validator."""
    llm = ChatOpenAI(model="gpt-4o", temperature=0).bind_tools(
        build_openai_tools(current_time),
        tool_choice="auto",
    )
    messages: list = [
        SystemMessage(content=system_few_shot(current_time)),
        HumanMessage(content=extract_prompt),
    ]
    ai: AIMessage = llm.invoke(messages)
    condition, mute_target, allow_target = _parse_tool_calls(ai)

    assistant_content = (ai.content or "").strip() if not ai.tool_calls else ""

    # Validate before expensive confirm call when incomplete
    preliminary = validate_extraction(
        condition, mute_target, allow_target, assistant_hint=assistant_content
    )
    if not preliminary.ok:
        # Optional short confirm only when we have tool calls but still incomplete
        if ai.tool_calls and preliminary.needs_supplement:
            messages.append(ai)
            for tc in ai.tool_calls:
                tc_id = tc.get("id") if isinstance(tc, dict) else getattr(tc, "id", "")
                tc_name = tc.get("name") if isinstance(tc, dict) else getattr(tc, "name", "")
                args = tc.get("args") if isinstance(tc, dict) else getattr(tc, "args", {})
                if isinstance(args, str):
                    args = json.loads(args)
                messages.append(
                    ToolMessage(
                        content=json.dumps(args, ensure_ascii=False),
                        tool_call_id=tc_id or tc_name,
                    )
                )
            messages.append(
                SystemMessage(
                    content=(
                        "정보가 부족합니다. 사용자에게 부족한 점(시간 또는 대상)만 "
                        "한 문장으로 재질문하세요."
                    )
                )
            )
            confirm: AIMessage = llm.invoke(messages)
            assistant_content = (confirm.content or "").strip() or preliminary.message
        else:
            assistant_content = preliminary.message

        return _fail(
            assistant_content,
            preliminary.fail_reason,
            needs_supplement=preliminary.needs_supplement,
            pending_classification=pending_classification,
            dialog_intent=dialog_intent,
        )

    # Complete → confirm message
    if ai.tool_calls:
        messages.append(ai)
        for tc in ai.tool_calls:
            tc_id = tc.get("id") if isinstance(tc, dict) else getattr(tc, "id", "")
            tc_name = tc.get("name") if isinstance(tc, dict) else getattr(tc, "name", "")
            args = tc.get("args") if isinstance(tc, dict) else getattr(tc, "args", {})
            if isinstance(args, str):
                args = json.loads(args)
            messages.append(
                ToolMessage(
                    content=json.dumps(args, ensure_ascii=False),
                    tool_call_id=tc_id or tc_name,
                )
            )
        messages.append(
            SystemMessage(
                content="추출된 tool 결과를 바탕으로 사용자에게 규칙이 어떻게 적용되는지 한두 문장으로 확인해줘."
            )
        )
        confirm2: AIMessage = llm.invoke(messages)
        assistant_content = (confirm2.content or "").strip()

    names = preliminary.names or []
    contents = preliminary.contents or []
    resolved_mute = bool(preliminary.mute)
    condition = preliminary.condition or {}

    packages: list[str] = []
    mapping_scores: list[dict[str, Any]] = []
    if names:
        apps = installed_apps or []
        if apps:
            from app.common.app_mapper import resolve_packages

            packages, unresolved, mapping_scores = resolve_packages(names, apps)
            if unresolved:
                joined = ", ".join(f"‘{n}’" for n in unresolved)
                return _fail(
                    f"{joined} 앱을 기기에서 찾지 못했어요. "
                    "설치된 앱 이름에 가깝게 다시 말씀해주세요.",
                    "app_unresolved",
                    needs_supplement=False,
                    pending_classification=pending_classification,
                    dialog_intent=dialog_intent,
                    mapping_scores=mapping_scores,
                )

    return {
        "ok": True,
        "targetFixed": {
            "mute": resolved_mute,
            "name": names,
            "packages": packages,
            "content": contents,
            "exceptions": [],
        },
        "condition": condition,
        "assistantMessage": assistant_content
        or ("규칙을 등록했습니다." if resolved_mute else "허용 규칙을 등록했습니다."),
        "mappingScores": mapping_scores,
        "pendingClassification": pending_classification,
        "failReason": "none",
        "needsSupplement": False,
        "dialogIntent": dialog_intent,
    }

# API 진입점 handle()는 v3_extract()를 호출하기 전에 pending-first + lightweight intent classifier를 수행합니다.
@traceable(name="handle_v3")
def handle(
    prompt: str,
    current_time: str,
    installed_apps: list[dict[str, Any]] | None = None,
    pending: bool = False,
    pending_original: str | None = None,
) -> dict[str, Any]:
    text = prompt.strip()
    now = current_time.strip()
    original = (pending_original or "").strip()

    # ----- Pending-first -----
    if pending and original:
        action = resolve_pending_action(original, text)

        if action == "CANCEL":
            return _fail(
                "알겠어요. 진행 중이던 규칙 등록을 취소했어요.",
                "cancelled",
                needs_supplement=False,
                pending_classification="CANCEL",
                dialog_intent="extract",
            )

        if action == "UNRESOLVED":
            return _fail(
                "이전 질문에 이어서 답해주세요. "
                "예: ‘2시간’, ‘오후 6시까지’, 또는 대상을 바꿔 말씀해주세요.",
                "incomplete_rule",
                needs_supplement=True,
                pending_classification="UNRESOLVED",
                dialog_intent="extract",
            )

        if action == "NEW_REQUEST":
            return run_extract(
                text,
                now,
                installed_apps,
                pending_classification="NEW_REQUEST",
                dialog_intent="extract",
            )

        # CONTINUE / UPDATE → merge then extract
        merged = merge_pending_prompt(original, text, action=action)
        return run_extract(
            merged,
            now,
            installed_apps,
            pending_classification=action,
            dialog_intent="extract",
        )

    # ----- Lightweight intent classifier (no LLM) -----
    intent = classify_intent(text)
    if intent == "reject":
        return _reject_response()

    return run_extract(
        text,
        now,
        installed_apps,
        pending_classification=None,
        dialog_intent="extract",
    )
