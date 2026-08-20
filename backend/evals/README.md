# PromptEngine Evals

| 폴더 | 대상 | Dataset | 실행 |
|------|------|---------|------|
| [`v1/`](v1/) | `app.v1.prompt_engine` | `promptengine_golden_v2` | `python -m evals.v1.run_experiment` |
| [`v2/`](v2/) | `app.v2.prompt_engine` | `promptengine_golden_v2` | `python -m evals.v2.run_experiment` |
| [`v3/`](v3/) | `app.v3.prompt_engine` | `promptengine_golden_v2` | `python -m evals.v3.run_experiment` |

`backend/`에서 실행. Dataset 업로드(최초 1회): `python -m evals.v2.dataset_upload`
