"""
PromptEngine v1 LangSmith Experiment runner
1. Dataset  - promptengine_golden_v1
2. Target   - app.v1.prompt_engine.handle (+ installedApps fixture)
3. Evaluator- evals.v1.evaluators.extract_rule_correctness
실행 (backend/ 에서):
  python -m evals.v1.run_experiment
"""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any

from dotenv import load_dotenv
from langsmith import evaluate

from app.v1.prompt_engine import handle
from evals.v1.evaluators import extract_rule_correctness

BACKEND_ROOT = Path(__file__).resolve().parents[2]
DATASET_NAME = "promptengine_golden_v1"
FIXTURE_PATH = Path(__file__).resolve().parent / "fixtures" / "installed_apps_device.json"
load_dotenv(BACKEND_ROOT / ".env")

_DEFAULT_APPS: list[dict[str, Any]] = json.loads(FIXTURE_PATH.read_text(encoding="utf-8"))


def run_extract(inputs: dict[str, Any]) -> dict[str, Any]:
    apps = inputs.get("installedApps")
    if not isinstance(apps, list) or not apps:
        apps = _DEFAULT_APPS
    return handle(
        inputs["prompt"],
        inputs["currentTime"],
        installed_apps=apps,
    )


if __name__ == "__main__":
    results = evaluate(
        run_extract,
        data=DATASET_NAME,
        evaluators=[extract_rule_correctness],
        experiment_prefix="baseline_v1",
        metadata={
            "prompt_engine": "v1",
            "model": "gpt-4o",
            "temperature": 0,
            "eval_type": "code_based",
            "app_mapper": "openai_embedding_cosine",
            "dataset": DATASET_NAME,
        },
        max_concurrency=2,
    )

    print(results)
