"""Harness checks: secret patterns and the route gate baseline."""

from __future__ import annotations

from scripts.harness_secrets import (
    find_secrets,
    is_protected_env_path,
    shell_env_violation,
)
from evals.gate import main as gate_main


def test_placeholder_is_not_a_secret():
    assert find_secrets("OPENAI_API_KEY=your-openai-api-key") == []


def _sample_key() -> str:
    # Keep the literal out of the repo so the secret scan stays clean.
    return "sk-" + "proj-" + ("abc123" * 4)


def test_openai_key_is_a_secret():
    hits = find_secrets(f"OPENAI_API_KEY={_sample_key()}")
    assert hits and hits[0].startswith("openai:")


def test_env_example_is_editable():
    assert is_protected_env_path("backend/.env") is True
    assert is_protected_env_path("backend/.env.local") is True
    assert is_protected_env_path("backend/.env.example") is False


def test_shell_blocks_env_writes_and_allows_example_copy():
    assert shell_env_violation("git add backend/.env") == "git add of an env file"
    assert shell_env_violation(f"echo {_sample_key()} >> .env")
    assert shell_env_violation("copy .env.example .env") is None


def test_route_gate_meets_baseline():
    assert gate_main([]) == 0
