# PromptEngine Evaluation — v2 (LangGraph routing, two-layer)

대상: `app.v2.prompt_engine`

## 평가 층

| 지표 | 의미 | 실행 |
|------|------|------|
| `route_correctness` | mini 라우팅(chitchat/extract) 정답률 | 둘 다 |
| `extract_rule_correctness` | 전체 e2e 추출 정확도 | `run_experiment` |
| `extract_rule_correctness_if_routed` | route=extract로 맞은 뒤의 추출 품질 (route miss면 0) | `run_experiment` |

## 실행 (backend/ 에서)

```powershell
# 1) 전체 two-layer (handle = route + extract)
python -m evals.v2.run_experiment

# 2) 라우팅만 (route_intent만, 저렴)
python -m evals.v2.run_route_experiment
```

## Dataset 라벨 (권장)

`outputs`(또는 나중에 metadata)에 route 정답을 넣으면 정확해집니다.

```json
{
  "outputs": {
    "ok": true,
    "dialogIntent": "extract",
    "targetFixed": { "...": "..." },
    "condition": { "...": "..." }
  }
}
```

chitchat 예:

```json
{
  "outputs": {
    "ok": false,
    "dialogIntent": "chitchat",
    "failReason": "chitchat",
    "needsSupplement": false
  }
}
```

지금은 `promptengine_golden_v1`을 쓰며, `dialogIntent`가 없으면 **기대 route = extract** 로 둡니다.

## 파일

| 경로 | 역할 |
|------|------|
| `run_experiment.py` | v2 handle + 3 evaluators |
| `run_route_experiment.py` | route_intent only |
| `evaluators.py` | route / extract / extract_if_routed |
| `datasets/golden_v1.jsonl` | 로컬 복사본 (업로드는 아직 v1 dataset명) |
