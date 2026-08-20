"""Code-based rule validation after LLM tool extraction."""
from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from typing import Any, Literal

FailReason = Literal[
    "none",
    "incomplete_rule",
    "missing_time",
    "missing_target",
    "empty_allow_target",
    "tool_conflict",
]


@dataclass
class ValidationResult:
    ok: bool
    fail_reason: FailReason
    needs_supplement: bool
    message: str
    mute: bool | None = None
    names: list[str] | None = None
    contents: list[str] | None = None
    condition: dict[str, Any] | None = None


def _parse_abs(obj: Any) -> datetime | None:
    if not isinstance(obj, dict):
        return None
    raw = obj.get("absolute")
    if not isinstance(raw, str) or not raw.strip():
        return None
    try:
        return datetime.fromisoformat(raw.replace("Z", "+00:00"))
    except ValueError:
        return None


def _string_list(obj: dict | None, key: str) -> list[str]:
    if not obj:
        return []
    xs = obj.get(key) or []
    return [x for x in xs if isinstance(x, str) and x.strip()]


def validate_extraction(
    condition: dict | None,
    mute_target: dict | None,
    allow_target: dict | None,
    *,
    assistant_hint: str = "",
) -> ValidationResult:
    if mute_target is not None and allow_target is not None:
        return ValidationResult(
            ok=False,
            fail_reason="tool_conflict",
            needs_supplement=False,
            message=(
                "mute/allow 도구가 동시에 선택되었습니다. "
                "‘카톡 받지마’ 또는 ‘카톡만 받아줘’처럼 다시 말씀해주세요."
            ),
        )

    if mute_target is not None:
        raw_target, mute = mute_target, True
    elif allow_target is not None:
        raw_target, mute = allow_target, False
    else:
        raw_target, mute = None, None

    has_cond = bool(condition) and condition != {}
    has_target = raw_target is not None

    if not has_cond and not has_target:
        return ValidationResult(
            ok=False,
            fail_reason="incomplete_rule",
            needs_supplement=True,
            message=assistant_hint
            or (
                "시간(언제부터 언제까지/몇 분)과 대상(어떤 앱·키워드를 받을지·막을지)을 "
                "함께 말씀해주세요. 예: ‘카톡 5분동안 받지마’."
            ),
        )
    if has_target and not has_cond:
        return ValidationResult(
            ok=False,
            fail_reason="missing_time",
            needs_supplement=True,
            message=assistant_hint
            or "얼마 동안 / 몇 시까지인지 시간을 알려주세요. 예: ‘30분’, ‘오후 6시까지’.",
        )
    if has_cond and not has_target:
        return ValidationResult(
            ok=False,
            fail_reason="missing_target",
            needs_supplement=True,
            message=assistant_hint
            or "어떤 앱·키워드를 받을지/막을지 대상을 알려주세요. 예: ‘카톡’, ‘광고’.",
        )

    assert condition is not None and raw_target is not None and mute is not None

    delivery = _parse_abs(condition.get("delivery"))
    expires = _parse_abs(condition.get("expires"))
    if delivery is None or expires is None or not (delivery < expires):
        return ValidationResult(
            ok=False,
            fail_reason="missing_time",
            needs_supplement=True,
            message=assistant_hint
            or "시작·종료 시간이 올바르지 않아요. 기간을 다시 말씀해주세요.",
        )

    rec = (condition.get("recurrence") or "none").strip()
    if rec in {"daily", "weekly"}:
        if not condition.get("window_start") or not condition.get("window_end"):
            return ValidationResult(
                ok=False,
                fail_reason="missing_time",
                needs_supplement=True,
                message="반복 구간의 시작·끝 시각(HH:mm)이 필요해요.",
            )
        if rec == "weekly" and not (condition.get("days_of_week") or []):
            return ValidationResult(
                ok=False,
                fail_reason="missing_time",
                needs_supplement=True,
                message="요일을 알려주세요. 예: ‘월수금’.",
            )

    names = _string_list(raw_target, "name")
    contents = _string_list(raw_target, "content")
    if (not mute) and (not names) and (not contents):
        return ValidationResult(
            ok=False,
            fail_reason="empty_allow_target",
            needs_supplement=True,
            message="받을 앱이나 키워드가 없어요. ‘카톡만 받아줘’처럼 대상을 알려주세요.",
        )

    return ValidationResult(
        ok=True,
        fail_reason="none",
        needs_supplement=False,
        message="",
        mute=mute,
        names=names,
        contents=contents,
        condition=condition,
    )
