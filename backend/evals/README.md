# PromptEngine Evaluation — v1 Spec

현재 [`app/prompt_engine.py`](../app/prompt_engine.py) + [`app/schemas.py`](../app/schemas.py) 기준 baseline이다.  
평가 진입점: `app.prompt_engine.handle(prompt, current_time)`.

---

## 1. Identity

| 항목 | 값 |
|------|-----|
| Version | **v1** |
| Entry point | `app.prompt_engine.handle(prompt, current_time)` |
| API | `POST /v1/extract-rule` |
| Stack | LangChain `ChatOpenAI` + `bind_tools` |
| Tracing | LangSmith `@traceable(name="handle")` |

Android [`PromptEngine.kt`](../../app/src/main/java/com/example/app/PromptEngine.kt)는 HTTP 클라이언트이므로 LLM eval 대상이 아니다.

---

## 2. Model / Inference

| 항목 | 값 |
|------|-----|
| model | `gpt-4o` |
| temperature | `0` |
| tool_choice | `auto` |
| Calls per request | 보통 **2회** — (1) tool extract (2) confirm `assistantMessage` |

---

## 3. Input

```json
{
  "prompt": "카톡 5분동안 받지마",
  "currentTime": "2026-08-14T15:00:00+09:00"
}
```

| 필드 | 설명 |
|------|------|
| `prompt` | 사용자 한국어 명령 |
| `currentTime` | ISO-8601. 프롬프트/툴의 “현재” 기준 시각 |

---

## 4. Tools (Function Calling)

**시간 조건과 대상 조건(mute|allow)이 둘 다** 문장에서 읽힐 때만 tool을 호출한다.  
하나라도 없으면 tool 호출 금지 → 후처리에서 `ok=false` + 재질문.

성공 시: **condition 1개 + (mute | allow) 1개**.

| Tool | 역할 |
|------|------|
| `extract_notification_condition` | 시간·반복 조건 |
| `extract_mute_target` | 블랙리스트 (받지마/차단/뮤트…) |
| `extract_allow_target` | 화이트리스트 (…만 받아) |

### condition 필드

- `delivery.absolute`, `expires.absolute` (ISO-8601)
- `activity`, `location` (거의 `null`)
- `recurrence`: `none` \| `daily` \| `weekly`
- `days_of_week`: weekly일 때 ISO 요일 `1=월 … 7=일`, 아니면 `[]`
- `window_start`, `window_end`: `HH:mm` 또는 `null`

### mute / allow 필드

- `name: string[]` — 앱 정규화 이름
- `content: string[]` — 키워드
- **exceptions 없음** (후처리에서 항상 `[]`)

### 라우팅 규칙 (시스템 프롬프트)

- 받지마/차단/뮤트/조용/끄/보류 → mute
- …만 받아/허용/수신 → allow
- “카톡 빼고 다 받아” → mute, `name=["카카오톡"]`

### 앱 정규화

- 카톡 → 카카오톡
- 인스타 → 인스타그램
- 페북 → 페이스북
- 유튜브 → YouTube

### 시간·대상 규칙

- 항상 `delivery < expires` (같으면 금지)
- 지금부터 N분 → `delivery=now`, `expires=now+N`
- N분 후부터 M분 → `delivery=now+N`, `expires=now+N+M`
- 매일/요일 → `recurrence` + `window_*`
- **시간 또는 대상 누락 시** tool 호출 금지 → `ok=false` (재질문)
- **기본 12시간 등 임의 기간 추정 금지**
  - 예: `카톡만 받아줘` → `ok=false` (golden: `allow_kakao_missing_time`)

---

## 5. Output

### 성공

```json
{
  "ok": true,
  "targetFixed": {
    "mute": true,
    "name": ["카카오톡"],
    "content": [],
    "exceptions": []
  },
  "condition": {
    "delivery": { "absolute": "..." },
    "expires": { "absolute": "..." },
    "activity": null,
    "location": null,
    "recurrence": "none",
    "days_of_week": [],
    "window_start": null,
    "window_end": null
  },
  "assistantMessage": "한두 문장 확인 문구"
}
```

### 실패

시간 또는 대상이 빠져 재질문하는 경우 예 (`카톡만 받아줘`):

```json
{
  "ok": false,
  "assistantMessage": "시간(언제부터 언제까지/몇 분)과 대상…을 함께 말씀해주세요. …"
}
```

(`targetFixed` / `condition` 없음)

---

## 6. Post-process (코드 규칙)

1. mute와 allow **둘 다** 호출 → `ok=false`
2. target 또는 condition 없음 → `ok=false` (시간/대상 누락·tool 미호출 포함)
3. allow인데 `name`·`content` 모두 빈 배열 → `ok=false`
4. 성공 시 `targetFixed.exceptions`는 **항상 `[]`**
5. `mute` 값은 **툴 선택으로만** 결정 (키워드 휴리스틱 없음)

---

## 7. Eval에서 볼 것 / 안 볼 것

**Ground truth로 비교할 것**

- `ok`
- `targetFixed.mute`
- `name` / `content` (집합 비교)
- `recurrence`, `days_of_week`, `window_*`
- `delivery` / `expires` (ISO **exact** — `currentTime` 고정)

**메인 지표로 두지 않을 것**

- `assistantMessage` 문체 (confirm 2차 호출 결과)

---

## 8. 관련 파일

| 역할 | 경로 |
|------|------|
| 프롬프트 / tools / handle | [`app/prompt_engine.py`](../app/prompt_engine.py) |
| Request / Response 스키마 | [`app/schemas.py`](../app/schemas.py) |
| FastAPI | [`app/main.py`](../app/main.py) |
| Android 클라이언트 | `app/src/main/java/com/example/app/PromptEngine.kt` |

---

## 다음 단계

1. `datasets/golden_v1.jsonl` — 유지·확장 (현재 5케이스; `allow_kakao_missing_time` = `ok:false`)
2. `datasets/golden_user_survey.jsonl` — 사용자 설문 문장 로컬 GT (미지원/`ok:false` 다수)
3. LangSmith Dataset 재업로드 (`dataset_upload.py`) — 스펙 변경 시 기존 dataset 삭제 후 업로드
4. `evaluators.py` / `run_experiment.py` — code-based evaluate
5. baseline experiment 결과 확인
