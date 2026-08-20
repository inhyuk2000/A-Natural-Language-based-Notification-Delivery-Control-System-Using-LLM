# PromptEngine Evaluation — v1

대상: `app.v1.prompt_engine.handle`

## 실행 (backend/ 에서)

```powershell
# Dataset 업로드 (최초 1회)
python -m evals.v1.dataset_upload

# LangSmith experiment
python -m evals.v1.run_experiment
```

## 파일

| 경로 | 역할 |
|------|------|
| `run_experiment.py` | LangSmith evaluate runner |
| `dataset_upload.py` | `golden_v1.jsonl` → LangSmith |
| `evaluators.py` | code-based correctness |
| `datasets/golden_v1.jsonl` | local golden |
| `fixtures/installed_apps_device.json` | installedApps fixture |
