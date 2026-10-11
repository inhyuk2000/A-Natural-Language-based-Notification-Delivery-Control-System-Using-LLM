#!/usr/bin/env python
"""Block .env writes and secret material in agent tool calls."""

from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "backend"))

from scripts.harness_secrets import (  # noqa: E402
    find_secrets,
    is_protected_env_path,
    shell_env_violation,
)


def _tool_input(payload: dict) -> dict:
    raw = payload.get("tool_input") or payload.get("toolInput") or {}
    return raw if isinstance(raw, dict) else {}


def _paths(payload: dict) -> list[str]:
    found: list[str] = []
    tool_input = _tool_input(payload)
    for key in ("path", "file_path", "target_file", "file"):
        value = tool_input.get(key) or payload.get(key)
        if isinstance(value, str) and value.strip():
            found.append(value)
    return found


def _texts(payload: dict) -> list[str]:
    found: list[str] = []
    tool_input = _tool_input(payload)
    for key in ("contents", "content", "new_string", "new_text", "command"):
        value = tool_input.get(key)
        if isinstance(value, str):
            found.append(value)
    command = payload.get("command")
    if isinstance(command, str):
        found.append(command)
    return found


def _commands(payload: dict) -> list[str]:
    found: list[str] = []
    command = payload.get("command")
    if isinstance(command, str):
        found.append(command)
    tool_command = _tool_input(payload).get("command")
    if isinstance(tool_command, str):
        found.append(tool_command)
    return found


def decide(payload: dict) -> dict:
    for path in _paths(payload):
        if is_protected_env_path(path):
            return {
                "permission": "deny",
                "user_message": ".env 파일은 에이전트가 수정할 수 없습니다. 예시는 .env.example만 고치세요.",
                "agent_message": "Blocked an edit to an env file. Update .env.example instead.",
            }
    for text in _texts(payload):
        secrets = find_secrets(text)
        if secrets:
            return {
                "permission": "deny",
                "user_message": "API key 또는 token 패턴이 있어 막았습니다.",
                "agent_message": "Blocked secret material: " + ", ".join(secrets),
            }
    for command in _commands(payload):
        reason = shell_env_violation(command)
        if reason:
            return {
                "permission": "deny",
                "user_message": "환경 파일 수정 또는 시크릿 커밋으로 보여 막았습니다.",
                "agent_message": "Blocked shell command: " + reason,
            }
    return {"permission": "allow"}


def main() -> int:
    raw = sys.stdin.read()
    if not raw.strip():
        json.dump({"permission": "allow"}, sys.stdout)
        return 0
    try:
        payload = json.loads(raw)
    except json.JSONDecodeError:
        json.dump({"permission": "allow"}, sys.stdout)
        return 0
    if not isinstance(payload, dict):
        json.dump({"permission": "allow"}, sys.stdout)
        return 0
    json.dump(decide(payload), sys.stdout, ensure_ascii=False)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
