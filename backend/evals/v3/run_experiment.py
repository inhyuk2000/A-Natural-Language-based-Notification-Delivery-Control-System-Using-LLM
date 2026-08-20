"""
PromptEngine v3 LangSmith Experiment — two-layer eval

Target: app.v3.prompt_engine.handle
  (pending-first + lightweight intent classifier + LLM extract)

Dataset: promptengine_golden_v2  (v1/v2 A/B와 동일)

실행 (backend/ 에서):
  python -m evals.v2.dataset_upload   # 최초 1회 (이미 있으면 skip)
  python -m evals.v3.run_experiment
"""

from __future__ import annotations

import json
import os
from pathlib import Path
from typing import Any

from dotenv import load_dotenv
from langsmith import evaluate

from app.v3.prompt_engine import handle
from evals.v3.evaluators import (
    extract_rule_correctness,
    extract_rule_correctness_if_routed,
    route_correctness,
)

BACKEND_ROOT = Path(__file__).resolve().parents[2]
DATASET_NAME = "promptengine_golden_v2"
FIXTURE_PATH = (
    Path(__file__).resolve().parents[1] / "v1" / "fixtures" / "installed_apps_device.json"
)
load_dotenv(BACKEND_ROOT / ".env")

# Prefer LR classifier for eval unless overridden
os.environ.setdefault("INTENT_CLASSIFIER", "auto")

_DEFAULT_APPS: list[dict[str, Any]] = json.loads(FIXTURE_PATH.read_text(encoding="utf-8"))


def run_v3(inputs: dict[str, Any]) -> dict[str, Any]:
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
        run_v3,
        data=DATASET_NAME,
        evaluators=[
            route_correctness,
            extract_rule_correctness,
            extract_rule_correctness_if_routed,
        ],
        experiment_prefix="classifier_v3_on_golden_v2",
        metadata={
            "prompt_engine": "v3",
            "eval_layers": [
                "route_correctness",
                "extract_rule_correctness",
                "extract_rule_correctness_if_routed",
            ],
            "model": "heuristic/ST+LR classifier + gpt-4o extract",
            "intent_classifier": os.getenv("INTENT_CLASSIFIER", "auto"),
            "temperature": 0,
            "eval_type": "code_based",
            "dataset": DATASET_NAME,
            "note": "same dataset as v1/v2 A/B; reject≡chitchat for route metric",
        },
        max_concurrency=2,
    )
    print(results)
