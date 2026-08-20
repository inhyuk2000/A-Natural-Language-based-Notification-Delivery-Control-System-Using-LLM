"""
PromptEngine v2 code-based evaluators (two-layer).

Layer 1 — route_correctness
  mini 라우팅(dialogIntent)이 정답 route와 일치하는지.

Layer 2 — extract_rule_correctness
  전체 end-to-end (ok / mute / packages / condition).

Layer 2b — extract_rule_correctness_if_routed
  정답 route가 extract 인 케이스만 추출 채점.
  라우팅이 틀리면 score=0 (원인: route_miss).
  정답 route가 chitchat 이면 추출 채점 생략(score=1, skipped).
"""

from __future__ import annotations

from typing import Any

from evals.v1.evaluators import extract_rule_correctness as _extract_cbe

_ROUTE_LABELS = {"chitchat", "extract"}


def _norm_route(value: Any) -> str | None:
    if not isinstance(value, str):
        return None
    v = value.strip().lower().replace(" ", "")
    return v if v in _ROUTE_LABELS else None


def expected_route(
    reference_outputs: dict | None,
    inputs: dict | None = None,
) -> str:
    """
    Dataset reference에서 기대 라우트를 읽는다.
    우선순위: dialogIntent | route | expectedRoute
    없으면 golden_v1 호환 — 규칙성 문항은 extract, chitchat GT만 chitchat.
    """
    ref = reference_outputs or {}
    for key in ("dialogIntent", "route", "expectedRoute"):
        got = _norm_route(ref.get(key))
        if got:
            return got
    if _norm_route(ref.get("failReason")) == "chitchat":
        return "chitchat"
    # pending 턴은 entry에서 extract 고정
    if isinstance(inputs, dict) and inputs.get("pending"):
        return "extract"
    return "extract"


def predicted_route(outputs: dict | None) -> str:
    out = outputs or {}
    got = _norm_route(out.get("dialogIntent"))
    if got:
        return got
    if out.get("failReason") == "chitchat":
        return "chitchat"
    return "extract"


def route_correctness(
    outputs: dict,
    reference_outputs: dict,
    inputs: dict | None = None,
) -> dict:
    """Layer 1: 라우팅 정답률."""
    exp = expected_route(reference_outputs, inputs)
    pred = predicted_route(outputs)
    ok = pred == exp
    return {
        "key": "route_correctness",
        "score": 1.0 if ok else 0.0,
        "comment": str({"expected": exp, "predicted": pred, "pass": ok}),
    }


def extract_rule_correctness(
    outputs: dict,
    reference_outputs: dict,
) -> dict:
    """Layer 2: 전체 end-to-end 추출 정확도 (v1 CBE와 동일)."""
    return _extract_cbe(outputs, reference_outputs)


def extract_rule_correctness_if_routed(
    outputs: dict,
    reference_outputs: dict,
    inputs: dict | None = None,
) -> dict:
    """
    Layer 2b: 라우팅이 맞은 뒤의 추출 품질.
    - expected=chitchat → 추출 지표 해당 없음 (skipped, score=1)
    - expected=extract & predicted!=extract → route_miss (score=0)
    - expected=extract & predicted=extract → 기존 CBE
    """
    exp = expected_route(reference_outputs, inputs)
    pred = predicted_route(outputs)

    if exp == "chitchat":
        return {
            "key": "extract_rule_correctness_if_routed",
            "score": 1.0,
            "comment": str({"skipped": True, "reason": "expected_chitchat"}),
        }

    if pred != "extract":
        return {
            "key": "extract_rule_correctness_if_routed",
            "score": 0.0,
            "comment": str(
                {
                    "route_miss": True,
                    "expected": exp,
                    "predicted": pred,
                }
            ),
        }

    base = _extract_cbe(outputs, reference_outputs)
    return {
        "key": "extract_rule_correctness_if_routed",
        "score": float(base.get("score") or 0.0),
        "comment": str({"route_ok": True, "extract": base.get("comment")}),
    }


__all__ = [
    "expected_route",
    "predicted_route",
    "route_correctness",
    "extract_rule_correctness",
    "extract_rule_correctness_if_routed",
]
