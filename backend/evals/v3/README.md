# PromptEngine Evaluation — v3

대상: `app.v3.prompt_engine.handle`  
Dataset: **`promptengine_golden_v2`** (v1/v2와 동일)

## 실행 (backend/)

```powershell
.\.venv\Scripts\Activate.ps1

# Dataset 업로드 (최초 1회, 이미 있으면 skip)
python -m evals.v2.dataset_upload

# v3 LangSmith experiment
python -m evals.v3.run_experiment
```

`.env`에 `OPENAI_API_KEY`, `LANGSMITH_API_KEY` 필요.  
`INTENT_CLASSIFIER=auto` 권장 (학습된 LR 사용).

## 비교

```powershell
python -m evals.v1.run_experiment
python -m evals.v2.run_experiment
python -m evals.v3.run_experiment
```

LangSmith에서 `extract_rule_correctness` + latency/토큰 비교.  
v3 experiment prefix: `classifier_v3_on_golden_v2`
