"""
v3 evaluators — same two-layer metrics as v2.

v3 returns dialogIntent=reject for chitchat; treat reject ≡ chitchat for routing scores.
"""

from __future__ import annotations

from typing import Any

from evals.v1.evaluators import extract_rule_correctness as _extract_cbe

_ROUTE_LABELS = {"chitchat", "extract", "reject"}


def _norm_route(value: Any) -> str | None:
    if not isinstance(value, str):
        return None
    v = value.strip().lower().replace(" ", "")
    if v == "reject":
        return "chitchat"
    return v if v in {"chitchat", "extract"} else None


def expected_route(
    reference_outputs: dict | None,
    inputs: dict | None = None,
) -> str:
    ref = reference_outputs or {}
    for key in ("dialogIntent", "route", "expectedRoute"):
        got = _norm_route(ref.get(key))
        if got:
            return got
    if ref.get("failReason") == "chitchat":
        return "chitchat"
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
    return _extract_cbe(outputs, reference_outputs)


def extract_rule_correctness_if_routed(
    outputs: dict,
    reference_outputs: dict,
    inputs: dict | None = None,
) -> dict:
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
                {"route_miss": True, "expected": exp, "predicted": pred}
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
