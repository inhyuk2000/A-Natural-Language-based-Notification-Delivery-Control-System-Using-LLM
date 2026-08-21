"""Pending-first resolver.

Default: gpt-4o-mini (LangChain) classifies one of
  CONTINUE | UPDATE | CANCEL | NEW_REQUEST | UNRESOLVED

Fallback / tests: regex rules via PENDING_RESOLVER=rules

Non-pending traffic never calls this — v3 handle uses intent_classifier instead.
"""
from __future__ import annotations

import os
import re
from typing import Literal

from langchain_core.messages import AIMessage, HumanMessage, SystemMessage
from langchain_openai import ChatOpenAI
from langsmith import traceable

from app.common.intent_classifier import classify_intent

PendingAction = Literal[
    "CONTINUE",
    "UPDATE",
    "CANCEL",
    "NEW_REQUEST",
    "UNRESOLVED",
]

_ACTIONS = ("CONTINUE", "UPDATE", "CANCEL", "NEW_REQUEST", "UNRESOLVED")

_PENDING_SYSTEM = """당신은 알림 규칙 앱의 pending(재질문) 후속 입력을 분류한다.
사용자에게 말을 걸지 마라. 아래 다섯 토큰 중 하나만 출력한다.

이전 사용자 명령은 정보가 부족해 재질문된 상태다. 이번 입력을 분류하라.

- CONTINUE: 재질문에 대한 보충(시간·기간·대상 추가). 예: "2시간", "오후 6시까지", "카톡도"
- UPDATE: 이전 규칙의 일부를 수정. 예: "아 카톡 말고 인스타", "인스타로 바꿔"
- CANCEL: 진행 중이던 규칙 등록을 취소만 함. 예: "취소해", "됐어", "그만"
- NEW_REQUEST: 이전 pending을 버리고 새로운 알림 제어 명령을 시작.
  취소 말투와 새 규칙이 같이 있으면 NEW_REQUEST를 우선.
  예: "아 됐고 인스타 알림 받지마"
- UNRESOLVED: pending과 연결하기 어려움. 예: "뭐라고?", "무슨 말이야?"

출력은 반드시 한 줄, 대문자 토큰만: CONTINUE | UPDATE | CANCEL | NEW_REQUEST | UNRESOLVED"""

# ----- regex fallback -----

_CANCEL = re.compile(
    r"(취소|그만|됐어|됐어요|됐어용|안\s*할|하지\s*마|그냥\s*마|무시해|패스)",
    re.I,
)
_UPDATE = re.compile(
    r"(말고|아니라|대신에|바꿔|변경|아니\s*그거|아니야)",
    re.I,
)
_TIME_SUPPLEMENT = re.compile(
    r"(\d+\s*(분|시간|초)|오전|오후|저녁|아침|\d+\s*시|까지|동안|부터)",
    re.I,
)
_UNRESOLVED = re.compile(
    r"(뭐라고|무슨\s*말|다시\s*말|알아듣|무슨\s*뜻|헷갈|잘\s*모르)",
    re.I,
)
_RULE_VERB = re.compile(r"(받지\s*마|막아|뮤트|허용|받아|조용|끄|차단)", re.I)


def _looks_like_new_rule(text: str) -> bool:
    t = text.strip()
    if not t:
        return False
    if classify_intent(t) != "extract":
        return False
    return bool(_RULE_VERB.search(t))


def resolve_pending_action_rules(
    pending_original: str, current_prompt: str
) -> PendingAction:
    """Regex fallback (tests / PENDING_RESOLVER=rules)."""
    current = (current_prompt or "").strip()
    if not current:
        return "UNRESOLVED"

    has_cancel = bool(_CANCEL.search(current))
    has_update = bool(_UPDATE.search(current))
    has_unresolved = bool(_UNRESOLVED.search(current))
    is_new = _looks_like_new_rule(current)

    if is_new:
        return "NEW_REQUEST"
    if has_cancel and not is_new:
        return "CANCEL"
    if has_update:
        return "UPDATE"
    if has_unresolved and not _TIME_SUPPLEMENT.search(current) and not has_update:
        return "UNRESOLVED"
    if _TIME_SUPPLEMENT.search(current) or len(current) <= 20:
        return "CONTINUE"
    if classify_intent(current) == "extract":
        return "CONTINUE"
    return "UNRESOLVED"


def _parse_action(raw: str) -> PendingAction | None:
    text = (raw or "").strip().upper().replace(" ", "").replace("-", "_")
    for a in _ACTIONS:
        if a in text:
            return a  # type: ignore[return-value]
    # soft aliases
    if "SUPPLEMENT" in text or "CONTINUE" in text:
        return "CONTINUE"
    if "NEW_COMMAND" in text or "NEWREQUEST" in text:
        return "NEW_REQUEST"
    return None


@traceable(name="v3_pending_resolve_llm")
def resolve_pending_action_llm(
    pending_original: str, current_prompt: str
) -> PendingAction:
    """gpt-4o-mini LangChain classifier for pending follow-ups only."""
    original = (pending_original or "").strip()
    current = (current_prompt or "").strip()
    if not current:
        return "UNRESOLVED"

    llm = ChatOpenAI(model="gpt-4o-mini", temperature=0)
    ai: AIMessage = llm.invoke(
        [
            SystemMessage(content=_PENDING_SYSTEM),
            HumanMessage(
                content=(
                    f"이전 명령(재질문 대상):\n{original}\n\n"
                    f"이번 입력:\n{current}\n\n"
                    "위 다섯 토큰 중 하나만 출력."
                )
            ),
        ]
    )
    parsed = _parse_action(ai.content or "")
    if parsed:
        return parsed
    # LLM unclear → rules fallback
    return resolve_pending_action_rules(original, current)


@traceable(name="v3_pending_resolve")
def resolve_pending_action(
    pending_original: str, current_prompt: str
) -> PendingAction:
    mode = os.getenv("PENDING_RESOLVER", "llm").strip().lower()
    if mode in {"rules", "regex", "heuristic"}:
        return resolve_pending_action_rules(pending_original, current_prompt)
    try:
        return resolve_pending_action_llm(pending_original, current_prompt)
    except Exception:
        return resolve_pending_action_rules(pending_original, current_prompt)


def merge_pending_prompt(
    pending_original: str,
    current_prompt: str,
    *,
    action: PendingAction,
) -> str:
    original = pending_original.strip()
    current = current_prompt.strip()
    if action == "UPDATE":
        return (
            f"이전 사용자 명령: {original}\n"
            f"사용자가 수정·변경한 내용: {current}\n"
            "이전 명령을 바탕으로 대상을 수정하고, 아직 없는 필수 정보(시간 등)는 "
            "비운 채 하나의 알림 규칙으로 추출하라."
        )
    return (
        f"이전 사용자 명령: {original}\n"
        f"사용자가 보충한 내용: {current}\n"
        "위를 하나의 완전한 알림 규칙 명령으로 보고 추출하라."
    )
