"""
PromptEngine LLM 추출 — LangChain ChatOpenAI + bind_tools.
mute/allow 는 LLM tool 선택만으로 결정 (rule-based keyword hint 없음).
"""
from __future__ import annotations

import json
from typing import Any

from langchain_core.messages import AIMessage, HumanMessage, SystemMessage, ToolMessage
from langchain_openai import ChatOpenAI
from langsmith import traceable

from app.pending import resolve_extract_prompt

# 통합 툴: condition + mode(mute|allow) + name/content 를 한 번에 받음.
# (구) extract_notification_condition / extract_mute_target / extract_allow_target
TOOL_RULE = "extract_notification_rule"
TOOL_CONDITION = "extract_notification_condition"  # legacy parse 호환
TOOL_MUTE = "extract_mute_target"  # legacy parse 호환
TOOL_ALLOW = "extract_allow_target"  # legacy parse 호환

# 시스템 프롬프트 few shot learning 관련 코드
def system_few_shot(now: str) -> str:
    return f"""당신은 알림 규칙 추출기다. 사용자 한국어 명령을 구조화한다.
mute/allow target은 한 명령에 하나만. exceptions 필드는 없다.

## 필수: 시간 조건 + 대상 조건 (둘 다)
- 성공하려면 **시간 조건**과 **대상 조건(mute|allow)** 을 문장에서 모두 읽을 수 있어야 한다.
- 둘 다 명확할 때만 **extract_notification_rule** 을 **한 번** 호출한다.
  - condition(시간) + mode(mute|allow) + name/content 를 한 호출에 모두 넣는다.
- 둘 중 하나라도 없으면 **tool을 호출하지 말고**, 부족한 정보(시간 또는 대상)를 짧게 다시 묻는다.
- 기간·시각을 임의로 추정하지 말 것. (예: 기본 12시간 금지)
- 회차(delivery/expires)를 계산할 수 있으면 사용자에게 확인하지 말고 tool에 바로 넣는다.

## 툴 선택 (mode)
- 받지마/차단/뮤트/조용/끄/보류 → mode=mute
- …만 받아/허용/수신 (받지마 없음) → mode=allow
- "카톡 빼고 다 받아" = 카톡 받지마 → mode=mute, name=["카카오톡"]
- 앱 이름 없이 조용히/모든 알림 받지마 → mode=mute, name=[]

## 시간 규칙 (현재={now}) — 문장에 시간이 있을 때만 적용
- 구간은 항상 delivery < expires. delivery==expires 절대 금지.
- "지금부터 N분(간) …" → delivery=현재, expires=현재+N분
- **"N분(만/동안/간) …" 처럼 기간만 있고 시작 시각이 없으면 → 지금부터. delivery=현재, expires=현재+N분**
  - 예: "카톡 2분만 받지마", "카톡 5분동안 받지마" 모두 현재부터.
- "N분 후부터 M분동안 …" → delivery=현재+N, expires=현재+N+M
- "N분 후부터 …까지" → delivery=현재+N, expires=끝 시각
- 반복(매일/요일) → recurrence + window_start/window_end (HH:mm) + **아래 회차 규칙으로 delivery/expires absolute 계산**

## 반복 회차 absolute (현재={now} 기준, 필수)
매일/요일 반복이면 window_start~window_end가 본체이고, delivery/expires는 **지금 기준 해당 회차 한 구간**만 넣는다.
- window_end ≤ window_start(시각 비교)면 자정 넘김: 끝은 시작일+1일.
- **아직 구간 전** (예: 오후 3시, 매일 22:00~04:00) → 가장 가까운 **다음** 회차.
  - delivery=오늘 window_start, expires=다음날 window_end
- **이미 구간 안** (예: 오후 11시, 매일 22:00~04:00) → **진행 중** 회차.
  - delivery=그 회차 window_start(과거여도 OK), expires=그 회차 window_end
  - delivery를 "지금"으로 바꾸지 말 것.
- **이미 구간 끝남** (예: 오전 10시, 매일 22:00~04:00) → 다음 밤 회차.
- 지난 회차(이미 끝난 delivery/expires)를 출력하지 말 것.

## Few-shot (현재={now} 가정)

User: 카톡 5분동안 받지마
→ extract_notification_rule: mode=mute,
   condition: delivery={now}, expires=({now}+5분), recurrence=none,
   name=["카카오톡"], content=[]

User: 카톡 2분만 받지마
→ extract_notification_rule: mode=mute,
   condition: delivery={now}, expires=({now}+2분), recurrence=none,
   name=["카카오톡"], content=[]
(시작 시각 없어도 "N분만/동안"이면 현재부터)

User: 지금부터 30분 모든 알림 받지마
→ extract_notification_rule: mode=mute,
   condition: delivery={now}, expires=({now}+30분),
   name=[], content=[]

User: 3분후부터 5분동안 카톡 받지마
→ extract_notification_rule: mode=mute,
   condition: delivery=({now}+3분), expires=({now}+8분),
   name=["카카오톡"], content=[]

User: 카톡만 받아줘
→ tool 호출 없음 (시간 없음). "몇 분 동안인지, 또는 몇 시까지인지 알려주세요."

User: 지금부터 1시간 카톡만 받아줘
→ extract_notification_rule: mode=allow,
   condition: delivery={now}, expires=({now}+1시간),
   name=["카카오톡"], content=[]

User: 지금부터 1시간 카톡이랑 인스타만 받아
→ extract_notification_rule: mode=allow,
   condition: delivery={now}, expires=({now}+1시간),
   name=["카카오톡","인스타그램"], content=[]

User: 게임관련 카톡이랑 인스타만 받아줘
→ tool 호출 없음 (시간 없음). 시간 재질문.

User: 지금부터 2시간 게임관련 카톡이랑 인스타만 받아줘
→ extract_notification_rule: mode=allow,
   condition: delivery={now}, expires=({now}+2시간),
   name=["카카오톡","인스타그램"], content=["게임"]

User: 광고 알림 받지마
→ tool 호출 없음 (시간 없음). 시간 재질문.

User: 지금부터 30분 광고 알림 받지마
→ extract_notification_rule: mode=mute,
   condition: delivery={now}, expires=({now}+30분),
   name=[], content=["광고"]

User: 매일 밤 10시부터 새벽 4시까지 조용히
(가정: 현재=2026-08-14T15:00:00+09:00 → 아직 구간 전)
→ extract_notification_rule: mode=mute,
   condition: recurrence=daily, window_start=22:00, window_end=04:00, days_of_week=[],
   delivery=2026-08-14T22:00:00+09:00, expires=2026-08-15T04:00:00+09:00,
   name=[], content=[]
  (사용자에게 회차 확인 묻지 말 것. tool에 바로 넣는다.)

User: 매일 밤 10시부터 새벽 4시까지 조용히
(가정: 현재=2026-08-14T23:00:00+09:00 → 이미 구간 안)
→ extract_notification_rule: mode=mute,
   condition: recurrence=daily, window_start=22:00, window_end=04:00, days_of_week=[],
   delivery=2026-08-14T22:00:00+09:00, expires=2026-08-15T04:00:00+09:00,
   name=[], content=[]
  (delivery=지금 23:00 금지. 회차 시작 22:00 유지)

User: 매일 밤 11시부터 아침 6시까지 조용히
→ extract_notification_rule: mode=mute,
   condition: recurrence=daily, window_start=23:00, window_end=06:00, days_of_week=[],
   delivery/expires=현재({now}) 기준 진행 중 또는 다음 회차 absolute,
   name=[], content=[]

User: 월수금 오후 2시부터 5시까지 카톡만 받아
→ extract_notification_rule: mode=allow,
   condition: recurrence=weekly, days_of_week=[1,3,5], window_start=14:00, window_end=17:00,
   delivery/expires=현재({now}) 기준 해당 회차(구간 전이면 다음 월·수·금 14:00~17:00),
   name=["카카오톡"], content=[]

## 앱 이름 (name)
- name에는 사용자에게 보이는 앱 표시 이름을 넣는다.
- 약어는 일반 표기로: 카톡→카카오톡, 인스타→인스타그램, 페북→페이스북, 유튜브→YouTube
- packageName은 넣지 않는다. 패키지 매핑은 서버가 기기 설치 목록으로 한다."""

# 현재 시간을 지정해 function calling용 Tool Schema 리스트를 return함.
def build_openai_tools(now: str) -> list[dict[str, Any]]:
    cond_desc = f"""알림 규칙의 시간·반복 조건을 추출한다. 현재 시각={now}.
문장에 시작/끝(또는 N분, 매일/요일 구간)이 **명시**되어 있을 때만 이 툴을 호출한다.
기간을 추정하지 말 것(기본 12시간 금지). 시간 정보가 없으면 이 툴을 호출하지 말 것.
**절대 금지:** delivery와 expires를 같은 시각으로 두지 말 것. 구간은 반드시 delivery < expires.

반복(daily|weekly):
- window_start/window_end(HH:mm) + recurrence 필수. overnight면 expires 날짜 = delivery 날짜+1.
- delivery/expires = 현재({now}) 기준 **진행 중 회차**, 없으면 **가장 가까운 다음 회차**.
- 구간 안이어도 delivery를 지금으로 바꾸지 말고 window_start absolute를 쓴다.
- 이미 끝난 지난 회차를 넣지 말 것.
예: 매일 22:00~04:00, 현재=2026-08-14T15:00 → delivery=08-14T22:00, expires=08-15T04:00
예: 매일 22:00~04:00, 현재=2026-08-14T23:00 → delivery=08-14T22:00, expires=08-15T04:00 (동일 회차)

Few-shot:
- "카톡 5분동안 받지마" → delivery=현재({now}), expires=현재+5분
- "카톡 2분만 받지마" → delivery=현재({now}), expires=현재+2분 (시작 시각 없어도 N분만/동안이면 현재부터)
- "지금부터 30분 조용히" → delivery=현재, expires=현재+30분
- "3분후부터 5분동안 …" → delivery=현재+3분, expires=현재+8분
- "5분 뒤부터 10분 뒤까지" → delivery=현재+5분, expires=현재+10분
- "카톡만 받아줘" / "광고 알림 받지마" → 시간 없음 → 이 툴 호출 금지
- 매일/요일 반복 → recurrence + window_* + 위 회차 absolute"""

    mute_desc = """받지마/차단/뮤트/조용/끄 명령 전용 (블랙리스트, mode=mute).
시간 조건이 문장에 함께 있을 때만 extract_notification_rule 을 호출한다. 시간 없으면 호출 금지.
'…만 받아'에는 mode=mute 를 쓰지 말 것 → mode=allow.
exceptions 없음. "카톡 빼고 다 받아"도 mode=mute, name=["카카오톡"].

Few-shot:
(1) 지금부터 30분 모든 알림 받지마 → mode=mute, name=[], content=[]
(2) 카톡 5분동안 받지마 → mode=mute, name=["카카오톡"], content=[]
(3) 카톡 2분만 받지마 → mode=mute, name=["카카오톡"], content=[]
(4) 지금부터 1시간 카톡이랑 인스타 받지마 → mode=mute, name=["카카오톡","인스타그램"], content=[]
(5) 지금부터 30분 광고 알림 받지마 → mode=mute, name=[], content=["광고"]
(6) 지금부터 10분 카톡 광고만 받지마 → mode=mute, name=["카카오톡"], content=["광고"]
(7) 매일 밤 10시부터 새벽 4시까지 조용히 → mode=mute, name=[], content=[]
약어: 카톡→카카오톡, 인스타→인스타그램."""

    allow_desc = """'…만 받아/허용/수신' 명령 전용 (화이트리스트, mode=allow).
시간 조건이 문장에 함께 있을 때만 extract_notification_rule 을 호출한다. 시간 없으면 호출 금지.
받지마/차단/뮤트에는 mode=allow 금지 → mode=mute.
exceptions 없음. 허용 앱은 모두 name에.

Few-shot:
(1) 지금부터 1시간 카톡만 받아줘 → mode=allow, name=["카카오톡"], content=[]
(2) 지금부터 1시간 카톡이랑 인스타만 받아 → mode=allow, name=["카카오톡","인스타그램"], content=[]
(3) 지금부터 2시간 게임관련 카톡이랑 인스타만 받아줘 → mode=allow, name=["카카오톡","인스타그램"], content=["게임"]
(4) 지금부터 1시간 엄마 카톡만 받아 → mode=allow, name=["카카오톡"], content=["엄마"]
(5) "카톡만 받아줘" → 시간 없음 → 이 툴 호출 금지
약어: 카톡→카카오톡, 인스타→인스타그램."""

    # 기존 condition/mute/allow description 본문은 유지. 섹션 헤더·교차참조만 새 툴·mode에 맞춤.
    merged_desc = f"""extract_notification_rule 한 번 호출에 condition + mode(mute|allow) + name/content 를 모두 넣는다.
mode는 반드시 mute|allow 중 정확히 하나. condition·name·content는 필수.
회차 absolute를 계산할 수 있으면 확인 질문 없이 tool에 넣는다.

===== condition (시간·반복) =====
{cond_desc}

===== mode=mute =====
{mute_desc}

===== mode=allow =====
{allow_desc}"""

    condition_properties: dict[str, Any] = {
        "delivery": {
            "type": "object",
            "additionalProperties": False,
            "description": (
                f"규칙 시작(ISO 8601, Asia/Seoul). 현재={now}. "
                "'지금부터 … N분간' → delivery=현재. 'N분 뒤부터' → 현재+N분. "
                "반복이면 해당 회차 window_start absolute(구간 안이면 과거여도 OK; 지금으로 바꾸지 말 것). "
                "delivery=expires 금지."
            ),
            "properties": {
                "absolute": {
                    "type": "string",
                    "description": f"예: '2025-08-25T18:00:00+09:00'. 현재: {now}",
                }
            },
            "required": ["absolute"],
        },
        "activity": {
            "type": ["string", "null"],
            "description": "활동 조건(거의 미사용). 없으면 null.",
        },
        "location": {
            "type": ["string", "null"],
            "description": "위치 조건(거의 미사용). 없으면 null.",
        },
        "expires": {
            "type": "object",
            "additionalProperties": False,
            "description": (
                f"규칙 종료. **delivery보다 반드시 이후**. 현재={now}. "
                "'지금부터 5분간 받지마' → expires=현재+5분, delivery=현재. "
                "반복이면 해당 회차 window_end absolute(overnight면 delivery 다음날). "
                "지난 회차·delivery와 동일 시각 금지."
            ),
            "properties": {
                "absolute": {
                    "type": "string",
                    "description": f"예: '2025-08-25T18:00:00+09:00'. 현재: {now}",
                }
            },
            "required": ["absolute"],
        },
        "recurrence": {
            "type": "string",
            "description": "none|daily|weekly. 매일→daily, 월수금/평일→weekly. 기본 none.",
            "enum": ["none", "daily", "weekly"],
        },
        "days_of_week": {
            "type": "array",
            "description": "weekly일 때 ISO 요일 월=1…일=7. 그 외 [].",
            "items": {"type": "integer"},
        },
        "window_start": {
            "type": ["string", "null"],
            "description": "반복 벽시계 시작 HH:mm. none이면 null.",
        },
        "window_end": {
            "type": ["string", "null"],
            "description": "반복 벽시계 종료 HH:mm. none이면 null. overnight 가능(23:00–06:00).",
        },
    }

    return [
        {
            "type": "function",
            "function": {
                "name": TOOL_RULE,
                "description": merged_desc,
                "parameters": {
                    "type": "object",
                    "additionalProperties": False,
                    "properties": {
                        "condition": {
                            "type": "object",
                            "additionalProperties": False,
                            "description": (
                                "시간·반복 조건 (delivery/expires/recurrence/window_* 등)."
                            ),
                            "properties": condition_properties,
                            "required": [
                                "delivery",
                                "activity",
                                "location",
                                "expires",
                                "recurrence",
                                "days_of_week",
                                "window_start",
                                "window_end",
                            ],
                        },
                        "mode": {
                            "type": "string",
                            "enum": ["mute", "allow"],
                            "description": (
                                "mute 또는 allow. 정확히 하나. "
                                "받지마/차단/뮤트/조용/끄/보류 → mute. "
                                "…만 받아/허용/수신 (받지마 없음) → allow. "
                                "앱 없이 조용히/모든 알림 받지마 → mute + name=[]."
                            ),
                        },
                        "name": {
                            "type": "array",
                            "description": (
                                "[mute] 묵음할 앱 정규화 이름 목록. 비우면([]) 모든 앱 묵음. 카톡→카카오톡. "
                                "[allow] 수신 허용할 앱 정규화 이름 목록. 가능하면 비우지 말 것. 카톡→카카오톡."
                            ),
                            "items": {"type": ["string", "null"]},
                        },
                        "content": {
                            "type": "array",
                            "description": (
                                "[mute] 묵음할 키워드. 없으면 []. "
                                "[allow] 허용 키워드 필터. 없으면 []."
                            ),
                            "items": {"type": ["string", "null"]},
                        },
                    },
                    "required": ["condition", "mode", "name", "content"],
                },
            },
        },
    ]


def _string_list(obj: dict[str, Any] | None, key: str) -> list[str]:
    if not obj:
        return []
    arr = obj.get(key) or []
    if not isinstance(arr, list):
        return []
    return [x for x in arr if isinstance(x, str) and x.strip()]

def _condition_only(args: dict[str, Any]) -> dict[str, Any]:
    """통합 툴 args 또는 flat condition dict → condition 필드만."""
    keys = (
        "delivery",
        "activity",
        "location",
        "expires",
        "recurrence",
        "days_of_week",
        "window_start",
        "window_end",
    )
    return {k: args[k] for k in keys if k in args}


# LLM의 추론으로 반환된 Function Calling 결과를 내 서비스에서 사용하기 쉽도록 후처리하는 함수.
def _parse_tool_calls(ai: AIMessage) -> tuple[dict | None, dict | None, dict | None]:
    condition = mute_target = allow_target = None
    for tc in ai.tool_calls or []:
        name = tc.get("name") if isinstance(tc, dict) else getattr(tc, "name", None)
        args = tc.get("args") if isinstance(tc, dict) else getattr(tc, "args", {})
        if isinstance(args, str):
            args = json.loads(args)
        if not isinstance(args, dict):
            continue
        if name == TOOL_RULE:
            raw_cond = args.get("condition")
            if isinstance(raw_cond, dict):
                condition = _condition_only(raw_cond)
            else:
                condition = _condition_only(args) or None
            target = {
                "name": args.get("name") if isinstance(args.get("name"), list) else [],
                "content": args.get("content") if isinstance(args.get("content"), list) else [],
            }
            mode = str(args.get("mode") or "").strip().lower()
            if mode == "mute":
                mute_target = target
            elif mode == "allow":
                allow_target = target
        elif name == TOOL_CONDITION:
            condition = args
        elif name == TOOL_MUTE:
            mute_target = args
        elif name == TOOL_ALLOW:
            allow_target = args
        elif name == "extract_notification_target":  # Legacy Code (레거시)
            el = args.get("mute", True)
            if isinstance(el, str):
                legacy_mute = el.lower() == "true"
            else:
                legacy_mute = bool(el) if el is not None else True
            if legacy_mute:
                mute_target = args
            else:
                allow_target = args
    return condition, mute_target, allow_target

# 메인 처리 로직 : (프롬프트, 현재 시간, 설치 앱) → targetFixed(+packages) / condition
@traceable(name="handle")
def handle(
    prompt: str,
    current_time: str,
    installed_apps: list[dict[str, Any]] | None = None,
    pending: bool = False,
    pending_original: str | None = None,
) -> dict[str, Any]:
    """
    PromptEngine.handle 대응.
    성공: ok=True, targetFixed{name, packages, ...}, condition, assistantMessage
    실패: ok=False, assistantMessage

    pending=True 이면 앞단 분류 LLM(app.pending)으로 supplement|new_command 를 가른 뒤 추출한다.
    installed_apps가 있고 name이 비어 있지 않으면 cosine으로 packages를 채운다.
    매칭 실패 시 ok=False. installed_apps가 비어 있으면 packages=[] (eval 호환).
    """
    extract_prompt, pending_classification = resolve_extract_prompt(
        prompt, pending, pending_original
    )

    llm = ChatOpenAI(model="gpt-4o", temperature=0).bind_tools( # temperature = 0 으로 출력 변동성을 없앰. 예상 가능한 값이 나올 수 있도록 처리.
        build_openai_tools(current_time), # tools 정의 목록 생성
        tool_choice="auto",
    )

    messages: list = [
        SystemMessage(content=system_few_shot(current_time)),
        HumanMessage(content=extract_prompt),
    ]

    # LangSmith 자동 Tracing
    ai: AIMessage = llm.invoke(messages) # Tool Calling으로 구조화된 조건 추출 수행
    condition, mute_target, allow_target = _parse_tool_calls(ai)

    # 앱과 동일: tool 결과를 대화에 넣고 한두 문장 확인 호출
    assistant_content = ""
    if ai.tool_calls:
        messages.append(ai)
        for tc in ai.tool_calls:
            tc_id = tc.get("id") if isinstance(tc, dict) else getattr(tc, "id", "")
            tc_name = tc.get("name") if isinstance(tc, dict) else getattr(tc, "name", "")
            args = tc.get("args") if isinstance(tc, dict) else getattr(tc, "args", {})
            if isinstance(args, str):
                args = json.loads(args)
            messages.append(
                ToolMessage(
                    content=json.dumps(args, ensure_ascii=False),
                    tool_call_id=tc_id or tc_name,
                )
            )
        # 사용자에게 보여줄 설명문 생성용 코드 -> 이 부분 역시 적절한 프롬프트 엔지니어링으로 Output이 잘 나오게 수정해야 할 거 같음.
        messages.append(
            SystemMessage(
                content="추출된 tool 결과를 바탕으로 사용자에게 규칙이 어떻게 적용되는지 한두 문장으로 확인해줘."
            )
        )

        # LangSmith 자동 Tracing
        confirm: AIMessage = llm.invoke(messages)
        assistant_content = (confirm.content or "").strip()
    else:
        assistant_content = (ai.content or "").strip()

    # mute/allow 는 LLM이 호출한 tool 만으로 결정 (rule-based hint 미사용)
    if mute_target is not None and allow_target is None:
        raw_target, resolved_mute = mute_target, True
    elif allow_target is not None and mute_target is None:
        raw_target, resolved_mute = allow_target, False
    elif mute_target is not None and allow_target is not None:
        return {
            "ok": False,
            "assistantMessage": (
                "mute/allow 도구가 동시에 선택되었습니다. "
                "‘카톡 받지마’ 또는 ‘카톡만 받아줘’처럼 다시 말씀해주세요."
            ),
            "pendingClassification": pending_classification,
        }
    else:
        raw_target, resolved_mute = None, False

    if raw_target is None or condition is None or condition == {}:
        msg = assistant_content or (
            "시간(언제부터 언제까지/몇 분)과 대상(어떤 앱·키워드를 받을지·막을지)을 "
            "함께 말씀해주세요. 예: ‘카톡 5분동안 받지마’, ‘지금부터 1시간 카톡만 받아줘’."
        )
        return {
            "ok": False,
            "assistantMessage": msg,
            "pendingClassification": pending_classification,
        }

    names = _string_list(raw_target, "name")
    contents = _string_list(raw_target, "content")

    if (not resolved_mute) and (not names) and (not contents):
        return {
            "ok": False,
            "assistantMessage": "받을 앱이나 키워드가 없어요. ‘카톡만 받아줘’처럼 대상을 알려주세요.",
            "pendingClassification": pending_classification,
        }

    packages: list[str] = []
    mapping_scores: list[dict[str, Any]] = []
    if names:
        apps = installed_apps or []
        if apps:
            from app.app_mapper import resolve_packages

            packages, unresolved, mapping_scores = resolve_packages(names, apps)
            if unresolved:
                joined = ", ".join(f"‘{n}’" for n in unresolved)
                return {
                    "ok": False,
                    "assistantMessage": (
                        f"{joined} 앱을 기기에서 찾지 못했어요. "
                        "설치된 앱 이름에 가깝게 다시 말씀해주세요."
                    ),
                    # 디버그 전용 — eval GT에 없으면 채점 안 함
                    "mappingScores": mapping_scores,
                    "pendingClassification": pending_classification,
                }
        # apps 없으면 packages=[] — LangSmith name-only eval 호환

    target_fixed = {
        "mute": resolved_mute,
        "name": names,
        "packages": packages,
        "content": contents,
        "exceptions": [],
    }

    return {
        "ok": True,
        "targetFixed": target_fixed,
        "condition": condition,
        "assistantMessage": assistant_content
        or ("규칙을 등록했습니다." if resolved_mute else "허용 규칙을 등록했습니다."),
        # 디버그 전용 — eval GT에 없으면 채점 안 함
        "mappingScores": mapping_scores,
        "pendingClassification": pending_classification,
    }
