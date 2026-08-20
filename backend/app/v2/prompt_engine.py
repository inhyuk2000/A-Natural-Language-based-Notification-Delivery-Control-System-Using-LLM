"""
PromptEngine v2 — LangGraph 라우팅 + LangChain 추출.

흐름:
  pending=true  → resolve_pending (mini) → extract (4o)
  pending=false → route_intent (mini)
                    ├─ chitchat → mini 응답 (needsSupplement=false)
                    └─ extract  → extract (4o)

ok=false 시 failReason / needsSupplement 를 내려 Android pending 여부를 구분한다.
"""
from __future__ import annotations

import json
import re
from typing import Any, Literal, TypedDict

from langchain_core.messages import AIMessage, HumanMessage, SystemMessage, ToolMessage
from langchain_openai import ChatOpenAI
from langgraph.graph import END, START, StateGraph
from langsmith import traceable

from app.common.pending import classify_pending_turn
from app.v1.prompt_engine import (
    _parse_tool_calls,
    _string_list,
    build_openai_tools,
    system_few_shot,
)

DialogIntent = Literal["chitchat", "extract"]
FailReason = Literal[
    "none",
    "chitchat",
    "incomplete_rule",
    "missing_time",
    "missing_target",
    "empty_allow_target",
    "app_unresolved",
    "tool_conflict",
]

_ROUTE_SYSTEM = """당신은 알림 규칙 앱의 의도 분류기다. 사용자에게 말을 걸지 마라.
입력을 다음 중 하나로만 분류한다.

- chitchat: 알림 규칙과 무관한 인사/잡담/이모티콘 (예: 안녕, ㅎ2, ㅎㅎ, 고마워, ㅋㅋ)
- extract: 알림을 받거나 막는 규칙 등록/변경 시도
  (예: 카톡 받지마, 1시간 조용히, 카톡만 받아줘, 매일 밤 10시부터 …)

출력은 반드시 한 줄 소문자 토큰만: chitchat 또는 extract"""

_CHITCHAT_SYSTEM = """당신은 알림 규칙 앱의 짧은 안내 도우미다.
사용자가 인사/잡담을 하면 한두 문장으로 친근히 답하고,
알림 규칙을 등록하려면 예시를 알려준다.
예: "카톡 5분동안 받지마", "지금부터 1시간 카톡만 받아줘"
규칙을 임의로 등록했다고 말하지 마라."""


class GraphState(TypedDict, total=False):
    prompt: str
    current_time: str
    installed_apps: list[dict[str, Any]]
    pending: bool
    pending_original: str | None
    dialog_intent: str
    extract_prompt: str
    pending_classification: str | None
    result: dict[str, Any]


def _parse_label(raw: str, allowed: set[str], default: str) -> str:
    text = (raw or "").strip().lower().replace(" ", "")
    for a in allowed:
        if a in text:
            return a
    return default


def _fail(
    message: str,
    fail_reason: FailReason,
    *,
    needs_supplement: bool = False,
    pending_classification: str | None = None,
    dialog_intent: str | None = None,
    mapping_scores: list[dict[str, Any]] | None = None,
) -> dict[str, Any]:
    out: dict[str, Any] = {
        "ok": False,
        "assistantMessage": message,
        "failReason": fail_reason,
        "needsSupplement": needs_supplement,
        "pendingClassification": pending_classification,
        "dialogIntent": dialog_intent,
    }
    if mapping_scores is not None:
        out["mappingScores"] = mapping_scores
    return out


def _infer_incomplete_reason(
    condition: dict | None,
    raw_target: dict | None,
    assistant_content: str,
) -> FailReason:
    has_cond = bool(condition)
    has_target = raw_target is not None
    if has_target and not has_cond:
        return "missing_time"
    if has_cond and not has_target:
        return "missing_target"
    msg = assistant_content or ""
    if re.search(r"시간|몇\s*분|몇\s*시|언제", msg) and not re.search(
        r"대상|앱|키워드", msg
    ):
        return "missing_time"
    if re.search(r"대상|앱|키워드", msg) and not re.search(r"시간|몇\s*분|몇\s*시", msg):
        return "missing_target"
    return "incomplete_rule"


@traceable(name="v2_route_intent")
def route_intent(prompt: str) -> DialogIntent:
    llm = ChatOpenAI(model="gpt-4o-mini", temperature=0)
    ai: AIMessage = llm.invoke(
        [
            SystemMessage(content=_ROUTE_SYSTEM),
            HumanMessage(content=prompt.strip()),
        ]
    )
    return _parse_label(ai.content or "", {"chitchat", "extract"}, "extract")  # type: ignore[return-value]


@traceable(name="v2_chitchat")
def run_chitchat(prompt: str) -> dict[str, Any]:
    llm = ChatOpenAI(model="gpt-4o-mini", temperature=0.4)
    ai: AIMessage = llm.invoke(
        [
            SystemMessage(content=_CHITCHAT_SYSTEM),
            HumanMessage(content=prompt.strip()),
        ]
    )
    msg = (ai.content or "").strip() or (
        "안녕하세요! 알림 규칙을 등록하려면 "
        "‘카톡 5분동안 받지마’처럼 시간과 대상을 함께 말씀해주세요."
    )
    return _fail(
        msg,
        "chitchat",
        needs_supplement=False,
        dialog_intent="chitchat",
    )


@traceable(name="v2_extract")
def run_extract(
    extract_prompt: str,
    current_time: str,
    installed_apps: list[dict[str, Any]] | None = None,
    *,
    pending_classification: str | None = None,
    dialog_intent: str | None = "extract",
) -> dict[str, Any]:
    """gpt-4o function calling 추출 + failReason."""
    llm = ChatOpenAI(model="gpt-4o", temperature=0).bind_tools(
        build_openai_tools(current_time),
        tool_choice="auto",
    )
    messages: list = [
        SystemMessage(content=system_few_shot(current_time)),
        HumanMessage(content=extract_prompt),
    ]
    ai: AIMessage = llm.invoke(messages)
    condition, mute_target, allow_target = _parse_tool_calls(ai)

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
        messages.append(
            SystemMessage(
                content="추출된 tool 결과를 바탕으로 사용자에게 규칙이 어떻게 적용되는지 한두 문장으로 확인해줘."
            )
        )
        confirm: AIMessage = llm.invoke(messages)
        assistant_content = (confirm.content or "").strip()
    else:
        assistant_content = (ai.content or "").strip()

    if mute_target is not None and allow_target is None:
        raw_target, resolved_mute = mute_target, True
    elif allow_target is not None and mute_target is None:
        raw_target, resolved_mute = allow_target, False
    elif mute_target is not None and allow_target is not None:
        return _fail(
            "mute/allow 도구가 동시에 선택되었습니다. "
            "‘카톡 받지마’ 또는 ‘카톡만 받아줘’처럼 다시 말씀해주세요.",
            "tool_conflict",
            needs_supplement=False,
            pending_classification=pending_classification,
            dialog_intent=dialog_intent,
        )
    else:
        raw_target, resolved_mute = None, False

    if raw_target is None or condition is None or condition == {}:
        reason = _infer_incomplete_reason(condition, raw_target, assistant_content)
        msg = assistant_content or (
            "시간(언제부터 언제까지/몇 분)과 대상(어떤 앱·키워드를 받을지·막을지)을 "
            "함께 말씀해주세요. 예: ‘카톡 5분동안 받지마’, ‘지금부터 1시간 카톡만 받아줘’."
        )
        return _fail(
            msg,
            reason,
            needs_supplement=True,
            pending_classification=pending_classification,
            dialog_intent=dialog_intent,
        )

    names = _string_list(raw_target, "name")
    contents = _string_list(raw_target, "content")

    if (not resolved_mute) and (not names) and (not contents):
        return _fail(
            "받을 앱이나 키워드가 없어요. ‘카톡만 받아줘’처럼 대상을 알려주세요.",
            "empty_allow_target",
            needs_supplement=True,
            pending_classification=pending_classification,
            dialog_intent=dialog_intent,
        )

    packages: list[str] = []
    mapping_scores: list[dict[str, Any]] = []
    if names:
        apps = installed_apps or []
        if apps:
            from app.common.app_mapper import resolve_packages

            packages, unresolved, mapping_scores = resolve_packages(names, apps)
            if unresolved:
                joined = ", ".join(f"‘{n}’" for n in unresolved)
                return _fail(
                    f"{joined} 앱을 기기에서 찾지 못했어요. "
                    "설치된 앱 이름에 가깝게 다시 말씀해주세요.",
                    "app_unresolved",
                    needs_supplement=False,
                    pending_classification=pending_classification,
                    dialog_intent=dialog_intent,
                    mapping_scores=mapping_scores,
                )

    return {
        "ok": True,
        "targetFixed": {
            "mute": resolved_mute,
            "name": names,
            "packages": packages,
            "content": contents,
            "exceptions": [],
        },
        "condition": condition,
        "assistantMessage": assistant_content
        or ("규칙을 등록했습니다." if resolved_mute else "허용 규칙을 등록했습니다."),
        "mappingScores": mapping_scores,
        "pendingClassification": pending_classification,
        "failReason": "none",
        "needsSupplement": False,
        "dialogIntent": dialog_intent,
    }


# ----- LangGraph nodes -----


def node_entry(state: GraphState) -> dict[str, Any]:
    """pending이면 extract 경로로, 아니면 의도 분류."""
    if state.get("pending") and (state.get("pending_original") or "").strip():
        return {"dialog_intent": "extract"}
    intent = route_intent(state["prompt"])
    return {"dialog_intent": intent}


def node_chitchat(state: GraphState) -> dict[str, Any]:
    return {"result": run_chitchat(state["prompt"])}


def node_resolve_pending(state: GraphState) -> dict[str, Any]:
    prompt = state["prompt"].strip()
    if state.get("pending") and (state.get("pending_original") or "").strip():
        original = state["pending_original"].strip()  # type: ignore[union-attr]
        kind = classify_pending_turn(original, prompt)
        if kind == "supplement":
            merged = (
                f"이전 사용자 명령: {original}\n"
                f"사용자가 보충한 내용: {prompt}\n"
                "위를 하나의 완전한 알림 규칙 명령으로 보고 추출하라."
            )
            return {
                "extract_prompt": merged,
                "pending_classification": kind,
            }
        return {
            "extract_prompt": prompt,
            "pending_classification": kind,
        }
    return {
        "extract_prompt": prompt,
        "pending_classification": None,
    }


def node_extract(state: GraphState) -> dict[str, Any]:
    result = run_extract(
        state.get("extract_prompt") or state["prompt"],
        state["current_time"],
        state.get("installed_apps"),
        pending_classification=state.get("pending_classification"),
        dialog_intent=state.get("dialog_intent") or "extract",
    )
    return {"result": result}


def _after_entry(state: GraphState) -> str:
    if state.get("dialog_intent") == "chitchat":
        return "chitchat"
    return "resolve_pending"


def build_graph():
    g: StateGraph = StateGraph(GraphState)
    g.add_node("entry", node_entry)
    g.add_node("chitchat", node_chitchat)
    g.add_node("resolve_pending", node_resolve_pending)
    g.add_node("extract", node_extract)

    g.add_edge(START, "entry")
    g.add_conditional_edges(
        "entry",
        _after_entry,
        {"chitchat": "chitchat", "resolve_pending": "resolve_pending"},
    )
    g.add_edge("chitchat", END)
    g.add_edge("resolve_pending", "extract")
    g.add_edge("extract", END)
    return g.compile()


_GRAPH = None


def get_graph():
    global _GRAPH
    if _GRAPH is None:
        _GRAPH = build_graph()
    return _GRAPH


@traceable(name="handle_v2")
def handle(
    prompt: str,
    current_time: str,
    installed_apps: list[dict[str, Any]] | None = None,
    pending: bool = False,
    pending_original: str | None = None,
) -> dict[str, Any]:
    """
    v2 진입점. API 시그니처는 v1 handle 과 동일 + 응답에 failReason/needsSupplement.
    """
    final: GraphState = get_graph().invoke(
        {
            "prompt": prompt.strip(),
            "current_time": current_time.strip(),
            "installed_apps": installed_apps or [],
            "pending": pending,
            "pending_original": pending_original,
        }
    )
    result = final.get("result") or _fail(
        "처리 중 오류가 발생했습니다.",
        "incomplete_rule",
        needs_supplement=False,
    )
    return result
