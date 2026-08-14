"""
PromptEngine LangSmith Experiment runner
1. Dataset  - promptengine_golden_v1 (이미 업로드됨)
2. Target   - app.prompt_engine.handle (+ installedApps fixture)
3. Evaluator- evals.evaluators.extract_rule_correctness
4. evaluate() → Experiment 결과를 LangSmith UI에 기록
실행 (backend/ 에서):
  python -m evals.run_experiment
"""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any

from dotenv import load_dotenv
from langsmith import evaluate

from app.prompt_engine import handle
from evals.evaluators import extract_rule_correctness

BACKEND_ROOT = Path(__file__).resolve().parents[1]
DATASET_NAME = "promptengine_golden_v1"
FIXTURE_PATH = Path(__file__).resolve().parent / "fixtures" / "installed_apps_device.json"
load_dotenv(BACKEND_ROOT / ".env")

_DEFAULT_APPS: list[dict[str, Any]] = json.loads(FIXTURE_PATH.read_text(encoding="utf-8"))


def run_extract(inputs: dict[str, Any]) -> dict[str, Any]:
    """
    Target function.
    LangSmith가 Dataset example.inputs 를 이 함수에 넣는다.
    installedApps가 없으면 실기기 스냅샷 fixture를 사용한다.
    """
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
        experiment_prefix="baseline_v2_synonym_time",
        metadata={
            "version": "v2_synonym_time",
            "model": "gpt-4o",
            "temperature": 0,
            "eval_type": "code_based",
            "app_mapper": "openai_embedding_cosine",
        },
        max_concurrency=2,
    )

    print(results)
