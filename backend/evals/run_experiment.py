"""
PromptEngine LangSmith Experiment runner
1. Dataset  - promptengine_golden_v1 (이미 업로드됨)
2. Target   - app.prompt_engine.handle
3. Evaluator- evals.evaluators.extract_rule_correctness
4. evaluate() → Experiment 결과를 LangSmith UI에 기록
실행 (backend/ 에서):
  python -m evals.run_experiment
"""

# 필요한 패키지 설치
from __future__ import annotations
from pathlib import Path
from typing import Any
from dotenv import load_dotenv
from langsmith import evaluate
from app.prompt_engine import handle
from evals.evaluators import extract_rule_correctness

# 환경 변수 로드 및 경로 설정
BACKEND_ROOT = Path(__file__).resolve().parents[1]
DATASET_NAME = "promptengine_golden_v1"
load_dotenv(BACKEND_ROOT / ".env")

# 실제 handle 함수 실행
def run_extract(inputs: dict[str, Any]) -> dict[str, Any]:

    """
    Target function.
    LangSmith가 Dataset example.inputs 를 이 함수에 넣는다.
    반환값이 evaluator 의 outputs 인자가 된다.
    """
    return handle(inputs["prompt"], inputs["currentTime"])

if __name__ == "__main__":

    results = evaluate(
        run_extract, # Target function
        data=DATASET_NAME,
        evaluators=[extract_rule_correctness], # Evaluator function
        experiment_prefix="baseline_v1",
        metadata={ # Experiment metadata
            "version": "v1",
            "model": "gpt-4o",
            "temperature": 0,
            "eval_type": "code_based",
        },
        max_concurrency=2, # 병렬 처리 수 (LangSmith UI에 표시되는 진행률과 관련)
    )

    print(results)