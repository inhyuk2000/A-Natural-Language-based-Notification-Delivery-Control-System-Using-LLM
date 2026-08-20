# PromptEngine Evaluation — v2 (LangGraph routing, two-layer)

대상: `app.v2.prompt_engine`  
로컬 dataset: [`datasets/golden_v2.jsonl`](datasets/golden_v2.jsonl)  
LangSmith: `promptengine_golden_v2`

## 커버리지 기준 (단순)

- 총 **N ≥ 60**
- **intent** mute / allow / reject 각 ≥ 5
- **time** duration / until_absolute / relative_delay / daily / weekly 각 ≥ 5 (`ok` 케이스)
- **target** all / single_app / multi_app / content / app_and_content 각 ≥ 5 (`ok` 케이스)
- **route** chitchat ≥ 15, 나머지 extract

각 케이스 `metadata` + `outputs.dialogIntent` 포함.  
`inputs.installedApps`는 전 케이스에 `installed_apps_device.json` 스냅샷을 넣음.

## 평가 층

| 지표 | 의미 |
|------|------|
| `route_correctness` | mini 라우팅 정답률 |
| `extract_rule_correctness` | 전체 e2e |
| `extract_rule_correctness_if_routed` | extract로 맞은 뒤 추출 (route miss=0) |

## 실행 (backend/)

```powershell
python -m evals.v2.dataset_upload
python -m evals.v2.run_experiment
python -m evals.v2.run_route_experiment
```

## 파일

| 경로 | 역할 |
|------|------|
| `datasets/golden_v2.jsonl` | v2 golden |
| `datasets/_build_golden_v2.py` | 재생성 스크립트 |
| `dataset_upload.py` | LangSmith 업로드 |
| `run_experiment.py` | two-layer |
| `run_route_experiment.py` | route only |
| `evaluators.py` | CBE |
