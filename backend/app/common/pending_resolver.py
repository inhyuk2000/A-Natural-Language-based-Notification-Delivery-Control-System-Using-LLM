"""Pending-first resolver (no LLM).

Actions: CONTINUE | UPDATE | CANCEL | NEW_REQUEST | UNRESOLVED

Priority when signals overlap:
  NEW_REQUEST > CANCEL > UPDATE > CONTINUE > UNRESOLVED
"""
from __future__ import annotations

import re
from typing import Literal

from langsmith import traceable

from app.common.intent_classifier import classify_intent

PendingAction = Literal[
    "CONTINUE",
    "UPDATE",
    "CANCEL",
    "NEW_REQUEST",
    "UNRESOLVED",
]

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
    # New control request must include a mute/allow-style verb.
    # ("아 카톡 말고 인스타" is UPDATE, not NEW_REQUEST)
    return bool(_RULE_VERB.search(t))


@traceable(name="v3_pending_resolve")
def resolve_pending_action(pending_original: str, current_prompt: str) -> PendingAction:
    original = (pending_original or "").strip()
    current = (current_prompt or "").strip()
    if not current:
        return "UNRESOLVED"

    has_cancel = bool(_CANCEL.search(current))
    has_update = bool(_UPDATE.search(current))
    has_unresolved = bool(_UNRESOLVED.search(current))
    is_new = _looks_like_new_rule(current)

    # Cancel phrase + new valid rule → NEW_REQUEST wins
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
    # CONTINUE (and default merge)
    return (
        f"이전 사용자 명령: {original}\n"
        f"사용자가 보충한 내용: {current}\n"
        "위를 하나의 완전한 알림 규칙 명령으로 보고 추출하라."
    )
