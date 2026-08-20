# PromptEngine 조건 추출을 위한 Code Based Evaluation (CBE) 평가기

"""
LangSmith Evaluation 개념 (최신 SDK):
- Target     : 평가 대상 앱 (여기선 prompt_engine.handle)
- outputs    : Target이 만든 예측 (Run outputs)
- reference_outputs : Dataset Example에 넣어 둔 정답 (Ground Truth)
- Code Evaluator : LLM 없이 규칙으로 score (LLM-as-Judge와 대비)
- Experiment : evaluate() 한 번 실행 결과가 UI에 쌓임
"""

from __future__ import annotations
from typing import Any


def _as_set(xs: Any) -> set[str]:
    """list → set. name/content 순서 무시 비교. 빈/비문자열 제외."""
    return {x for x in (xs or []) if isinstance(x, str) and x.strip()}


def _abs(obj: Any) -> str | None:
    """condition.delivery|expires 형태 {"absolute": ISO} → 문자열."""
    if isinstance(obj, dict):
        v = obj.get("absolute")
        return v if isinstance(v, str) else None
    return None


def extract_rule_correctness(
    outputs: dict,
    reference_outputs: dict,
) -> dict:
    """
    ExtractRule JSON 정답 일치도 (binary code metric).
    LangSmith evaluate()가 Example마다 호출:
      outputs            ← Target(handle) 반환값
      reference_outputs  ← Dataset example.outputs
    Returns:
      key/score 형태 dict — UI 지표명과 0~1 점수
      comment — 필드별 pass/fail (디버깅용)

    delivery/expires 는 currentTime이 고정이므로 ISO 문자열 exact match.
    """
    pred = outputs or {}
    exp = reference_outputs or {}
    checks: dict[str, bool] = {}

    checks["ok"] = bool(pred.get("ok")) == bool(exp.get("ok"))

    if not exp.get("ok"):
        return {
            "key": "extract_rule_correctness",
            "score": 1.0 if checks["ok"] else 0.0,
            "comment": str(checks),
        }

    pt = pred.get("targetFixed") or {}
    et = exp.get("targetFixed") or {}
    pc = pred.get("condition") or {}
    ec = exp.get("condition") or {}

    if "mute" in et:
        checks["mute"] = bool(pt.get("mute")) == bool(et.get("mute"))
    if "packages" in et:
        checks["packages"] = _as_set(pt.get("packages")) == _as_set(et.get("packages"))
    if "content" in et:
        checks["content"] = _as_set(pt.get("content")) == _as_set(et.get("content"))
    if "recurrence" in ec:
        checks["recurrence"] = pc.get("recurrence") == ec.get("recurrence")
    if "delivery" in ec:
        checks["delivery"] = _abs(pc.get("delivery")) == _abs(ec.get("delivery"))
    if "expires" in ec:
        checks["expires"] = _abs(pc.get("expires")) == _abs(ec.get("expires"))

    passed = all(checks.values()) if checks else False

    return {
        "key": "extract_rule_correctness",
        "score": 1.0 if passed else 0.0,
        "comment": str(checks),
    }
