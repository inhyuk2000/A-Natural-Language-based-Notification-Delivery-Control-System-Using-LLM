# NotiLLM agent harness

코딩 에이전트가 만든 변경도 요구사항 단위로 남기고, 테스트와 라우팅 평가를 통과하기 전에는 `main`에 넣지 않는다.

## 폴더

- `backend/app/v1`, `v2`, `v3`: 비교용으로 병행하는 엔진. 새 로직은 `v3`와 `backend/app/common`에만 둔다.
- `backend/app/v1/prompt_engine.py`의 `system_few_shot`: 추출 프롬프트. v2/v3가 이 함수를 그대로 쓴다.
- `backend/evals/`: Golden Set, evaluator, 라우팅 게이트.
- `app/`: Android. 백엔드 작업과 한 변경에 섞지 않는다.

## 작업 순서

1. GitHub Issue 하나로 요구사항과 완료 조건을 적는다.
2. `main`에서 브랜치를 만든다. 이름 예: `fix/pending-cancel`.
3. 그 이슈 범위만 수정한다.
4. `backend`에서 `pytest tests`를 돌린다.
5. 아래 파일이 바뀌면 `python -m evals.gate`도 돌린다.
   - `backend/app/v1/prompt_engine.py`
   - `backend/app/v2/prompt_engine.py`
   - `backend/app/v3/prompt_engine.py`
   - `backend/evals/**`
6. PR 본문에 요구사항, 변경 내용, 테스트 결과, eval 결과를 채운다.

`main`에 직접 푸시하지 않는다. 커밋 메시지에는 무엇을 바꿨는지와 왜 바꿨는지를 같이 쓴다.

## 금지

- `.env`와 `.env.*`를 수정하거나 커밋하지 않는다. 새 변수는 `backend/.env.example`에만 추가한다.
- API key, token을 소스에 넣지 않는다.
- Golden Set(`backend/evals/**/datasets/*.jsonl`)의 기대 출력을 점수를 올리려고 고치지 않는다.
- 단위 테스트에서 OpenAI를 실제로 호출하지 않는다.

## 게이트

- 매 PR: `.github/workflows/backend-tests.yml`이 `pytest tests`와 시크릿 스캔을 돌린다.
- 프롬프트·엔진·eval이 바뀐 PR: `.github/workflows/eval-gate.yml`이 Golden Set 85개의 `route_correctness`를 기준선과 비교한다. 기준선보다 낮으면 실패한다.
- 기준선 파일: `backend/evals/baselines/v3_route.json`. 분류기는 API 없이 재현되는 `HeuristicIntentClassifier`다.
- 추출 정확도 전체 실험은 로컬에서 `python -m evals.v3.run_experiment`로 돌린다. OpenAI와 LangSmith 키가 필요하다.

로컬 커밋 훅은 한 번만 연결한다.

```powershell
git config core.hooksPath .githooks
```

연습: PR을 한 번 열어 본다.