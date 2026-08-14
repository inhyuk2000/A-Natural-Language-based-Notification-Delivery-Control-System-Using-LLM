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

# 리스트를 집합(set) 으로 바꿉니다.
# ["카카오톡", "인스타그램"]과 ["인스타그램", "카카오톡"]처럼 순서만 다른 경우도 같게 보려고 씁니다.
def _as_set(xs: Any) -> set[str]:
    """list → set. name/content 순서 무시 비교. 빈/비문자열 제외."""
    return {x for x in (xs or []) if isinstance(x, str) and x.strip()}

# {"absolute": "2026-08-14T15:00:00+09:00"} 형태에서 ISO 시각 문자열만 꺼냅니다.
def _abs(obj: Any) -> str | None:
    """condition.delivery|expires 형태 {"absolute": ISO} → 문자열."""
    if isinstance(obj, dict):
        v = obj.get("absolute")
        return v if isinstance(v, str) else None
    return None

# 본채점 함수. LangSmith가 케이스마다 호출합니다.
def extract_rule_correctness(
    outputs: dict, # Target(handle) 반환값
    reference_outputs: dict, # Dataset example.outputs
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

    # ok (성공/실패) — router 성격
    checks["ok"] = bool(pred.get("ok")) == bool(exp.get("ok"))

    # 정답이 실패면 구조 필드 없음 → ok만 채점
    # 예: "카톡만 받아줘" (시간 없음) → ok=false
    if not exp.get("ok"):
        return {
            "key": "extract_rule_correctness",
            "score": 1.0 if checks["ok"] else 0.0,
            "comment": str(checks),
        }

    # 성공 정답 → targetFixed / condition 필드 비교
    # 정답에 있는 키만 검사 (부분 reference 허용)
    pt = pred.get("targetFixed") or {}
    et = exp.get("targetFixed") or {}
    pc = pred.get("condition") or {}
    ec = exp.get("condition") or {}

    if "mute" in et:
        checks["mute"] = bool(pt.get("mute")) == bool(et.get("mute"))
    if "name" in et:
        checks["name"] = _as_set(pt.get("name")) == _as_set(et.get("name"))
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
