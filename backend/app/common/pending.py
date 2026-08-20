"""pending 턴 전용: 보충 vs 새 명령 분류 (사용자 메시지 생성 안 함)."""
from __future__ import annotations

from langchain_core.messages import AIMessage, HumanMessage, SystemMessage
from langchain_openai import ChatOpenAI
from langsmith import traceable

_PENDING_CLASSIFY_SYSTEM = """당신은 알림 규칙 대화 분류기다. 사용자에게 말을 걸지 마라.
이전 명령은 정보가 부족해 재질문된 상태다. 이번 입력이 그 재질문에 대한 보충인지,
아니면 이전과 무관한 새 규칙 등록 명령인지 판별한다.

- supplement: 시간·기간·대상만 덧붙임 (예: "1시간", "저녁 10시까지", "카톡도")
- new_command: 새로운 알림 규칙 전체 (예: "인스타 30분 받지마", "매일 10시부터 조용히")

출력은 반드시 한 줄, 소문자 토큰만: supplement 또는 new_command"""


@traceable(name="classify_pending_turn")
def classify_pending_turn(pending_original: str, current_prompt: str) -> str:
    """pending 턴 전용. 분류 라벨만 반환."""
    llm = ChatOpenAI(model="gpt-4o-mini", temperature=0)
    ai: AIMessage = llm.invoke(
        [
            SystemMessage(content=_PENDING_CLASSIFY_SYSTEM),
            HumanMessage(
                content=(
                    f"이전 명령:\n{pending_original.strip()}\n\n"
                    f"이번 입력:\n{current_prompt.strip()}\n\n"
                    f"이번 입력({current_prompt.strip()})이 이전 명령({pending_original.strip()})에 추가로 필요한 보충 답변인지, 아니면 이전과 무관한 새 명령인지 판별하라."
                    "supplement 또는 new_command 중 하나만 출력."
                )
            ),
        ]
    )
    raw = (ai.content or "").strip().lower().replace(" ", "")
    if "new_command" in raw or raw == "new" or raw.startswith("new"):
        return "new_command"
    if "supplement" in raw:
        return "supplement"
    return "new_command"


def resolve_extract_prompt(
    prompt: str,
    pending: bool,
    pending_original: str | None,
) -> tuple[str, str | None]:
    """
    Returns (prompt_for_extract, pending_classification|None).
    pending=False 이면 분류 LLM을 호출하지 않는다.
    """
    original = (pending_original or "").strip()
    current = prompt.strip()
    if not pending or not original:
        return current, None

    kind = classify_pending_turn(original, current)
    if kind == "supplement":
        merged = (
            f"이전 사용자 명령: {original}\n"
            f"사용자가 보충한 내용: {current}\n"
            "위를 하나의 완전한 알림 규칙 명령으로 보고 추출하라."
        )
        return merged, kind
    return current, kind
