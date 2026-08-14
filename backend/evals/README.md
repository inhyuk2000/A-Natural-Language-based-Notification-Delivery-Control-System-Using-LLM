# PromptEngine Evaluation — v1 Spec (auto app mapping)

현재 [`app/prompt_engine.py`](../app/prompt_engine.py) + [`app/schemas.py`](../app/schemas.py) + [`app/app_mapper.py`](../app/app_mapper.py) 기준.

평가 진입점: `handle(prompt, current_time, installed_apps=...)`.  
Experiment runner는 [`fixtures/installed_apps_v1.json`](fixtures/installed_apps_v1.json) 을 기본 주입한다.

---

## 1. Identity

| 항목 | 값 |
|------|-----|
| Version | **v1_auto_map** |
| Entry point | `app.prompt_engine.handle(prompt, current_time, installed_apps)` |
| API | `POST /v1/extract-rule` |
| App mapping | OpenAI embedding cosine (`text-embedding-3-small`, threshold≈0.45) |
| Stack | LangChain `ChatOpenAI` + `bind_tools` |
| Tracing | LangSmith `@traceable(name="handle")` |

Android [`PromptEngine.kt`](../../app/src/main/java/com/example/app/PromptEngine.kt)는 HTTP 클라이언트이므로 LLM eval 대상이 아니다.  
기기 매핑은 **수동 alias 없음** — PackageManager 라벨만 서버로 보낸다.

---

## 2. Model / Inference

| 항목 | 값 |
|------|-----|
| model | `gpt-4o` |
| temperature | `0` |
| tool_choice | `auto` |
| Calls per request | 보통 **2회** — (1) tool extract (2) confirm `assistantMessage` |
| Embeddings | name[] → packages[] (installedApps 있을 때) |

---

## 3. Input

```json
{
  "prompt": "카톡 5분동안 받지마",
  "currentTime": "2026-08-14T15:00:00+09:00",
  "installedApps": [
    { "packageName": "com.kakao.talk", "labels": ["카카오톡"] }
  ]
}
```

| 필드 | 설명 |
|------|------|
| `prompt` | 사용자 한국어 명령 |
| `currentTime` | ISO-8601. 프롬프트/툴의 “현재” 기준 시각 |
| `installedApps` | 기기 설치 앱 스냅샷. eval runner가 fixture로 채울 수 있음 |

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

- `name: string[]` — 표시용 앱 이름 (약어→일반 표기 권장)
- `content: string[]` — 키워드
- **exceptions 없음** (후처리에서 항상 `[]`)

### 라우팅 규칙 (시스템 프롬프트)

- 받지마/차단/뮤트/조용/끄/보류 → mute
- …만 받아/허용/수신 → allow
- “카톡 빼고 다 받아” → mute, `name=["카카오톡"]`

### 앱 이름 (LLM) / packages (서버)

- LLM `name`: 카톡→카카오톡, 인스타→인스타그램 등 **표시 이름**
- 서버: `installedApps` 라벨과 cosine → `targetFixed.packages`
- 매칭 실패(threshold 미달) → `ok=false` + 재질문
- `installedApps` 비어 있으면 `packages=[]` (name-only eval 호환)

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
    "packages": ["com.kakao.talk"],
    "content": [],
    "exceptions": []
  },
  "condition": { "...": "..." },
  "assistantMessage": "한두 문장 확인 문구"
}
```

Android는 **`packages`를 SQLite에 저장**하고 알림 매칭은 packageName 기준(기존과 동일).

### 실패

시간/대상 누락 또는 앱 cosine 매칭 실패 시 `ok=false` + `assistantMessage` (구조 필드 없음).

---

## 6. Post-process (코드 규칙)

1. mute와 allow **둘 다** 호출 → `ok=false`
2. target 또는 condition 없음 → `ok=false`
3. allow인데 `name`·`content` 모두 빈 배열 → `ok=false`
4. 성공 시 `targetFixed.exceptions`는 **항상 `[]`**
5. `mute` 값은 **툴 선택으로만** 결정
6. `installedApps` + non-empty `name` → cosine `packages`; 미매칭 → `ok=false`

---

## 7. Eval에서 볼 것

- `ok`, `mute`, `packages` / `content` (집합; reference 키만)
- `name`은 표시용 중간값이라 **채점하지 않음** (매핑은 `packages`로 평가)
- `recurrence`, `delivery` / `expires` (ISO exact)
- `assistantMessage` 문체는 메인 지표 아님

---

## 8. 관련 파일

| 역할 | 경로 |
|------|------|
| handle | [`app/prompt_engine.py`](../app/prompt_engine.py) |
| Cosine mapper | [`app/app_mapper.py`](../app/app_mapper.py) |
| Schema | [`app/schemas.py`](../app/schemas.py) |
| Fixture | [`fixtures/installed_apps_v1.json`](fixtures/installed_apps_v1.json) |

---

## 다음 단계

1. LangSmith Dataset 재업로드 (`dataset_upload.py`) — `packages` GT 반영
2. `python -m evals.run_experiment`
3. `APP_MAPPER_THRESHOLD` 튜닝
