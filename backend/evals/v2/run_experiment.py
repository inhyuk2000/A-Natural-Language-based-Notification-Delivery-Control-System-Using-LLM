"""
PromptEngine v2 LangSmith Experiment — two-layer eval

Layer 1: route_correctness
Layer 2: extract_rule_correctness (overall e2e)
Layer 2b: extract_rule_correctness_if_routed (route 맞은 뒤 추출)

Target: app.v2.prompt_engine.handle
Dataset: promptengine_golden_v1 (임시; golden_v2 전까지)

실행 (backend/ 에서):
  python -m evals.v2.run_experiment
"""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any

from dotenv import load_dotenv
from langsmith import evaluate

from app.v2.prompt_engine import handle
from evals.v2.evaluators import (
    extract_rule_correctness,
    extract_rule_correctness_if_routed,
    route_correctness,
)

BACKEND_ROOT = Path(__file__).resolve().parents[2]
DATASET_NAME = "promptengine_golden_v1"
FIXTURE_PATH = (
    Path(__file__).resolve().parents[1] / "v1" / "fixtures" / "installed_apps_device.json"
)
load_dotenv(BACKEND_ROOT / ".env")

_DEFAULT_APPS: list[dict[str, Any]] = json.loads(FIXTURE_PATH.read_text(encoding="utf-8"))


def run_v2(inputs: dict[str, Any]) -> dict[str, Any]:
    apps = inputs.get("installedApps")
    if not isinstance(apps, list) or not apps:
        apps = _DEFAULT_APPS
    return handle(
        inputs["prompt"],
        inputs["currentTime"],
        installed_apps=apps,
        pending=bool(inputs.get("pending") or False),
        pending_original=inputs.get("pendingOriginal"),
    )


if __name__ == "__main__":
    results = evaluate(
        run_v2,
        data=DATASET_NAME,
        evaluators=[
            route_correctness,
            extract_rule_correctness,
            extract_rule_correctness_if_routed,
        ],
        experiment_prefix="routing_v2_two_layer",
        metadata={
            "prompt_engine": "v2",
            "eval_layers": [
                "route_correctness",
                "extract_rule_correctness",
                "extract_rule_correctness_if_routed",
            ],
            "model": "gpt-4o + gpt-4o-mini routing",
            "temperature": 0,
            "eval_type": "code_based",
            "dataset": DATASET_NAME,
            "note": "temporary: uses golden_v1; expected route defaults to extract",
        },
        max_concurrency=2,
    )
    print(results)
