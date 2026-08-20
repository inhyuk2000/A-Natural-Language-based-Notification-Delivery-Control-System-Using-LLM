"""
PromptEngine v2 — route-only LangSmith experiment

Target: app.v2.prompt_engine.route_intent (gpt-4o-mini)
평가: route_correctness 만 (extract LLM 호출 없음 → 저렴/빠름)

Dataset example.outputs 에 dialogIntent|route|expectedRoute 가 있으면 그걸 쓰고,
없으면 extract 로 간주.

Dataset: promptengine_golden_v2

실행 (backend/ 에서):
  python -m evals.v2.run_route_experiment
"""

from __future__ import annotations

from pathlib import Path
from typing import Any

from dotenv import load_dotenv
from langsmith import evaluate

from app.v2.prompt_engine import route_intent
from evals.v2.evaluators import route_correctness

BACKEND_ROOT = Path(__file__).resolve().parents[2]
DATASET_NAME = "promptengine_golden_v2"
load_dotenv(BACKEND_ROOT / ".env")


def run_route(inputs: dict[str, Any]) -> dict[str, Any]:
    """
    pending=true 이면 실제 그래프처럼 extract 로 고정
    (route_intent 를 호출하지 않음).
    """
    if inputs.get("pending") and (inputs.get("pendingOriginal") or "").strip():
        return {"dialogIntent": "extract", "pendingBypass": True}
    intent = route_intent(inputs["prompt"])
    return {"dialogIntent": intent, "pendingBypass": False}


if __name__ == "__main__":
    results = evaluate(
        run_route,
        data=DATASET_NAME,
        evaluators=[route_correctness],
        experiment_prefix="routing_v2_route_only",
        metadata={
            "prompt_engine": "v2",
            "eval_layers": ["route_correctness"],
            "model": "gpt-4o-mini",
            "eval_type": "code_based",
            "dataset": DATASET_NAME,
            "note": "route-only; dataset golden_v2",
        },
        max_concurrency=4,
    )
    print(results)
