# PromptEngine v3

Pending-first + lightweight intent classifier (`extract`|`reject`) + existing LLM extractor.

## Flow

1. `pending` → `pending_resolver` (**gpt-4o-mini** 5액션; `PENDING_RESOLVER=rules`면 정규식)
2. else → `intent_classifier` (no LLM by default heuristic; optional SentenceTransformer+LR)
3. `extract` → gpt-4o tools + `rule_validator`

일반 요청은 classifier만, **재질문(pending) 턴에만** mini가 한 번 더 붙습니다.

## Run API

```env
PROMPT_ENGINE_VERSION=v3
INTENT_CLASSIFIER=heuristic   # or auto (needs sentence-transformers)
```

## Train embedding classifier (optional)

```powershell
pip install -r requirements.txt
python -m app.common.train_intent_classifier
```

## Tests

```powershell
pip install pytest
$env:INTENT_CLASSIFIER="heuristic"
python -m pytest tests/test_v3_flow.py -q
```
