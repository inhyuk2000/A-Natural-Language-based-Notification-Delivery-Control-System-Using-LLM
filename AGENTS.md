# NotiLLM agent harness

코딩 에이전트가 만든 변경도 요구사항 단위로 남기고, 테스트와 라우팅 평가를 통과하기 전에는 `main`에 넣지 않는다.

## 폴더

- `backend/app/v1`, `v2`, `v3`: 비교용으로 병행하는 엔진. 새 로직은 `v3`와 `backend/app/common`에만 둔다.
- `backend/app/v1/prompt_engine.py`의 `system_few_shot`: 추출 프롬프트. v2/v3가 이 함수를 그대로 쓴다.
- `backend/evals/`: Golden Set, evaluator, 라우팅 게이트.
- `app/`: Android. 백엔드 작업과 한 변경에 섞지 않는다.

## 작업 순서

코드를 고치는 요청이면, 파일을 수정하기 전에 저장소 루트에서 아래를 실행한다. 질문만 한 경우에는 브랜치를 만들지 않는다. 이미 그 작업 브랜치에 있으면 다시 만들지 않는다.

```powershell
git checkout main
git pull origin main
git checkout -b 작업이름
```

작업이름 예: `eval/reject-unsafe`

1. GitHub Issue 하나로 요구사항과 완료 조건을 적는다.
2. 그 이슈 범위만 수정한다.
3. `backend`에서 `python -m pytest tests`를 돌린다.
4. 아래 파일이 바뀌면 `python -m evals.gate`도 돌린다.
   - `backend/app/v1/prompt_engine.py`
   - `backend/app/v2/prompt_engine.py`
   - `backend/app/v3/prompt_engine.py`
   - `backend/evals/**`
5. 수정이 끝나면 사용자에게 브랜치 이름을 알리고, 아래 명령을 그 이름으로 적어서 보여 준다. 사용자가 직접 푸시한다. 에이전트는 사용자가 시키기 전에는 `git push`를 실행하지 않는다.

```powershell
git add .
git commit -m "무엇을 왜 바꿨는지"
git push -u origin 작업이름
```

6. 푸시 출력에 나온 주소를 열어 Create pull request를 누르라고 안내한다. pytest, secret-scan, route-correctness가 초록일 때만 머지한다.

`main`에는 커밋하거나 푸시하지 않는다. `git push origin main`은 쓰지 않는다. 커밋 메시지에는 무엇을 바꿨는지와 왜 바꿨는지를 같이 쓴다.

## 금지

- `.env`와 `.env.*`를 수정하거나 커밋하지 않는다. 새 변수는 `backend/.env.example`에만 추가한다.
- API key, token을 소스에 넣지 않는다.
- Golden Set(`backend/evals/**/datasets/*.jsonl`)의 기대 출력을 점수를 올리려고 고치지 않는다.
- 단위 테스트에서 OpenAI를 실제로 호출하지 않는다.

## 게이트

- 매 PR: `.github/workflows/backend-tests.yml`이 `pytest tests`와 시크릿 스캔을 돌린다.
- 매 PR: `.github/workflows/eval-gate.yml`이 Golden Set 85개의 `route_correctness`를 기준선과 비교한다. 기준선보다 낮으면 실패한다.
- 기준선 파일: `backend/evals/baselines/v3_route.json`. 분류기는 API 없이 재현되는 `HeuristicIntentClassifier`다.
- 추출 정확도 전체 실험은 로컬에서 `python -m evals.v3.run_experiment`로 돌린다. OpenAI와 LangSmith 키가 필요하다.

로컬 커밋 훅은 한 번만 연결한다.

```powershell
git config core.hooksPath .githooks
```