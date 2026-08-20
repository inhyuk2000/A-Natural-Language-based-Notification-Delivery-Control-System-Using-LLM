# PromptEngine Evaluation — v1

대상 엔진: `app.v1.prompt_engine.handle`  
평가 dataset: **`promptengine_golden_v2`** (v2와 A/B 비교용으로 고정)

로컬 `datasets/golden_v1.jsonl`은 보관용이며, experiment는 v2 dataset을 씁니다.

## 실행 (backend/ 에서)

```powershell
# Dataset 업로드 (최초 1회, v2 jsonl → LangSmith)
python -m evals.v2.dataset_upload

# v1 엔진 + golden_v2
python -m evals.v1.run_experiment
```

## 파일

| 경로 | 역할 |
|------|------|
| `run_experiment.py` | LangSmith evaluate (engine=v1, data=golden_v2) |
| `dataset_upload.py` | `golden_v1.jsonl` → `promptengine_golden_v1` (레거시) |
| `evaluators.py` | code-based correctness |
| `datasets/golden_v1.jsonl` | 로컬 v1 golden (보관) |
| `fixtures/installed_apps_device.json` | installedApps fixture |
