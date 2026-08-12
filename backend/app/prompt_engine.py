"""
PromptEngine LLM 추출 — LangChain ChatOpenAI + bind_tools.
mute/allow 는 LLM tool 선택만으로 결정 (rule-based keyword hint 없음).
"""
from __future__ import annotations

import json
from typing import Any

from langchain_core.messages import AIMessage, HumanMessage, SystemMessage, ToolMessage
from langchain_openai import ChatOpenAI

TOOL_CONDITION = "extract_notification_condition"
TOOL_MUTE = "extract_mute_target"
TOOL_ALLOW = "extract_allow_target"

# 시스템 프롬프트 few shot learning 관련 코드
def system_few_shot(now: str) -> str:
    return f"""당신은 알림 규칙 추출기다. 사용자 한국어 명령을 tool call로만 구조화한다.
반드시 extract_notification_condition 과 (extract_mute_target | extract_allow_target) 중 하나를 함께 호출한다.
mute/allow target은 한 명령에 하나만. exceptions 필드는 없다.

## 툴 선택
- 받지마/차단/뮤트/조용/끄/보류 → extract_mute_target
- …만 받아/허용/수신 (받지마 없음) → extract_allow_target
- "카톡 빼고 다 받아" = 카톡 받지마 → extract_mute_target, name=["카카오톡"]

## 시간 규칙 (현재={now})
- 구간은 항상 delivery < expires. delivery==expires 절대 금지.
- "지금부터 N분(간) …" → delivery=현재, expires=현재+N분
- "N분 후부터 M분동안 …" → delivery=현재+N, expires=현재+N+M
- "N분 후부터 …까지" → delivery=현재+N, expires=끝 시각
- 반복(매일/요일) → recurrence + window_start/window_end (HH:mm)

## Few-shot (현재={now} 가정)

User: 카톡 5분동안 받지마
→ condition: delivery={now}, expires=({now}+5분), recurrence=none
→ extract_mute_target: name=["카카오톡"], content=[]

User: 지금부터 30분 모든 알림 받지마
→ condition: delivery={now}, expires=({now}+30분)
→ extract_mute_target: name=[], content=[]

User: 3분후부터 5분동안 카톡 받지마
→ condition: delivery=({now}+3분), expires=({now}+8분)
→ extract_mute_target: name=["카카오톡"], content=[]

User: 카톡만 받아줘
→ condition: delivery={now}, expires=({now}+12시간)  // 기간 미지정 시 12시간
→ extract_allow_target: name=["카카오톡"], content=[]

User: 지금부터 1시간 카톡이랑 인스타만 받아
→ condition: delivery={now}, expires=({now}+1시간)
→ extract_allow_target: name=["카카오톡","인스타그램"], content=[]

User: 게임관련 카톡이랑 인스타만 받아줘
→ condition: delivery={now}, expires=({now}+12시간)
→ extract_allow_target: name=["카카오톡","인스타그램"], content=["게임"]

User: 광고 알림 받지마
→ extract_mute_target: name=[], content=["광고"]

User: 매일 밤 11시부터 아침 6시까지 조용히
→ condition: recurrence=daily, window_start=23:00, window_end=06:00,
   delivery/expires는 해당 회차 구간으로 계산
→ extract_mute_target: name=[], content=[]

User: 월수금 오후 2시부터 5시까지 카톡만 받아
→ condition: recurrence=weekly, days_of_week=[1,3,5], window_start=14:00, window_end=17:00
→ extract_allow_target: name=["카카오톡"], content=[]

## 앱 이름 정규화
카톡→카카오톡, 인스타→인스타그램, 페북→페이스북, 유튜브→YouTube"""

# 현재 시간을 지정해 function calling용 Tool Schema 리스트를 return함.
def build_openai_tools(now: str) -> list[dict[str, Any]]:
    cond_desc = f"""알림 규칙의 시간·반복 조건을 추출한다. 현재 시각={now}.
**절대 금지:** delivery와 expires를 같은 시각으로 두지 말 것.
구간은 반드시 delivery < expires.

Few-shot:
- "카톡 5분동안 받지마" → delivery=현재({now}), expires=현재+5분 (둘 다 현재+5분 아님!)
- "지금부터 30분 조용히" → delivery=현재, expires=현재+30분
- "3분후부터 5분동안 …" → delivery=현재+3분, expires=현재+8분
- "5분 뒤부터 10분 뒤까지" → delivery=현재+5분, expires=현재+10분
- 기간 미지정(…만 받아줘) → delivery=현재, expires=현재+12시간
- 매일/요일 반복 → recurrence + window_start/window_end"""

    mute_desc = """받지마/차단/뮤트/조용/끄 명령 전용 (블랙리스트, mute default).
'…만 받아'에는 이 툴을 쓰지 말 것 → extract_allow_target.
exceptions 없음. "카톡 빼고 다 받아"도 이 툴로 name=["카카오톡"].

Few-shot:
(1) 모든 알림 받지마 → name=[], content=[]
(2) 카톡 받지마 / 카톡 5분동안 받지마 → name=["카카오톡"], content=[]
(3) 카톡이랑 인스타 받지마 → name=["카카오톡","인스타그램"], content=[]
(4) 광고 알림 받지마 → name=[], content=["광고"]
(5) 카톡 광고만 받지마 → name=["카카오톡"], content=["광고"]
약어: 카톡→카카오톡, 인스타→인스타그램."""

    allow_desc = """'…만 받아/허용/수신' 명령 전용 (화이트리스트, allow default).
받지마/차단/뮤트에는 이 툴 금지 → extract_mute_target.
exceptions 없음. 허용 앱은 모두 name에.

Few-shot:
(1) 카톡만 받아줘 → name=["카카오톡"], content=[]
(2) 카톡이랑 인스타만 받아 → name=["카카오톡","인스타그램"], content=[]
(3) 게임관련 카톡이랑 인스타만 받아줘 → name=["카카오톡","인스타그램"], content=["게임"]
(4) 엄마 카톡만 받아 → name=["카카오톡"], content=["엄마"]
약어: 카톡→카카오톡, 인스타→인스타그램."""

    return [
        {
            "type": "function",
            "function": {
                "name": TOOL_CONDITION,
                "description": cond_desc,
                "parameters": {
                    "type": "object",
                    "additionalProperties": False,
                    "properties": {
                        "delivery": {
                            "type": "object",
                            "additionalProperties": False,
                            "description": (
                                f"규칙 시작(ISO 8601, Asia/Seoul). 현재={now}. "
                                "'지금부터 … N분간' → delivery=현재. 'N분 뒤부터' → 현재+N분. "
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
                                "delivery와 동일 시각 금지."
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
                    },
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
            },
        },
        {
            "type": "function",
            "function": {
                "name": TOOL_MUTE,
                "description": mute_desc,
                "parameters": {
                    "type": "object",
                    "additionalProperties": False,
                    "properties": {
                        "name": {
                            "type": "array",
                            "description": "묵음할 앱 정규화 이름 목록. 비우면([]) 모든 앱 묵음. 카톡→카카오톡.",
                            "items": {"type": ["string", "null"]},
                        },
                        "content": {
                            "type": "array",
                            "description": "묵음할 키워드. 없으면 [].",
                            "items": {"type": ["string", "null"]},
                        },
                    },
                    "required": ["name", "content"],
                },
            },
        },
        {
            "type": "function",
            "function": {
                "name": TOOL_ALLOW,
                "description": allow_desc,
                "parameters": {
                    "type": "object",
                    "additionalProperties": False,
                    "properties": {
                        "name": {
                            "type": "array",
                            "description": "수신 허용할 앱 정규화 이름 목록. 가능하면 비우지 말 것. 카톡→카카오톡.",
                            "items": {"type": ["string", "null"]},
                        },
                        "content": {
                            "type": "array",
                            "description": "허용 키워드 필터. 없으면 [].",
                            "items": {"type": ["string", "null"]},
                        },
                    },
                    "required": ["name", "content"],
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

# LLM의 추론으로 반환된 Function Calling 결과를 내 서비스에서 사용하기 쉽도록 후처리하는 함수.
def _parse_tool_calls(ai: AIMessage) -> tuple[dict | None, dict | None, dict | None]:
    condition = mute_target = allow_target = None
    for tc in ai.tool_calls or []:
        name = tc.get("name") if isinstance(tc, dict) else getattr(tc, "name", None)
        args = tc.get("args") if isinstance(tc, dict) else getattr(tc, "args", {})
        if isinstance(args, str):
            args = json.loads(args)
        if name == TOOL_CONDITION:
            condition = args
        elif name == TOOL_MUTE:
            mute_target = args
        elif name == TOOL_ALLOW:
            allow_target = args
        elif name == "extract_notification_target": # Legacy Code (레거시)
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

# 메인 처리 로직 : (사용자 프롬프트, 현재 시간) 입력 -> (targetFixed, condition 포함한 Response Json) 반환
def handle(prompt: str, current_time: str) -> dict[str, Any]:
    """
    PromptEngine.handle 대응.
    성공: ok=True, targetFixed, condition, assistantMessage
    실패: ok=False, assistantMessage
    """
    llm = ChatOpenAI(model="gpt-4o", temperature=0).bind_tools( # temperature = 0 으로 출력 변동성을 없앰. 예상 가능한 값이 나올 수 있도록 처리.
        build_openai_tools(current_time), # tools 정의 목록 생성
        tool_choice="auto",
    )

    messages: list = [
        SystemMessage(content=system_few_shot(current_time)),
        HumanMessage(content=prompt),
    ]

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
        }
    else:
        raw_target, resolved_mute = None, False

    if raw_target is None or condition is None or condition == {}:
        msg = assistant_content or (
            "알림 대상이나 조건이 제대로 추출되지 않았습니다."
            "‘카톡 받지마’ 또는 ‘카톡만 받아줘’처럼 다시 말씀해주세요."
        )
        return {"ok": False, "assistantMessage": msg}

    names = _string_list(raw_target, "name")
    contents = _string_list(raw_target, "content")

    if (not resolved_mute) and (not names) and (not contents):
        return {
            "ok": False,
            "assistantMessage": "받을 앱이나 키워드가 없어요. ‘카톡만 받아줘’처럼 대상을 알려주세요.",
        }

    target_fixed = {
        "mute": resolved_mute,
        "name": names,
        "content": contents,
        "exceptions": [],
    }

    return {
        "ok": True,
        "targetFixed": target_fixed,
        "condition": condition,
        "assistantMessage": assistant_content
        or ("규칙을 등록했습니다." if resolved_mute else "허용 규칙을 등록했습니다."),
    }
