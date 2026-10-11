#!/usr/bin/env python
"""Run pytest and the route gate when prompt or eval files changed."""

from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
WATCHES = (
    "backend/app/v1/prompt_engine.py",
    "backend/app/v2/prompt_engine.py",
    "backend/app/v3/prompt_engine.py",
    "backend/evals/",
)


def _changed() -> list[str]:
    files: set[str] = set()
    commands = (
        ["git", "diff", "--name-only", "HEAD"],
        ["git", "diff", "--name-only", "--cached"],
        ["git", "ls-files", "--others", "--exclude-standard"],
    )
    for args in commands:
        try:
            out = subprocess.check_output(
                args, cwd=ROOT, text=True, stderr=subprocess.DEVNULL
            )
        except (subprocess.CalledProcessError, FileNotFoundError):
            continue
        for line in out.splitlines():
            rel = line.strip().replace("\\", "/")
            if rel:
                files.add(rel)
    return sorted(files)


def _watched(path: str) -> bool:
    return any(path == item or path.startswith(item) for item in WATCHES)


def _python() -> str:
    candidates = (
        ROOT / "backend" / ".venv" / "Scripts" / "python.exe",
        ROOT / "backend" / ".venv" / "bin" / "python",
    )
    for path in candidates:
        if path.is_file():
            return str(path)
    return sys.executable


def _run(args: list[str]) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [ _python(), *args[1:] ] if args and args[0] == sys.executable else args,
        cwd=ROOT / "backend",
        capture_output=True,
        text=True,
    )


def main() -> int:
    sys.stdin.read()
    watched = [path for path in _changed() if _watched(path)]
    if not watched:
        json.dump({}, sys.stdout)
        return 0

    pytest_run = _run([sys.executable, "-m", "pytest", "tests", "-q"])
    gate_run = _run([sys.executable, "-m", "evals.gate"])
    if pytest_run.returncode == 0 and gate_run.returncode == 0:
        json.dump({}, sys.stdout)
        return 0

    detail = "\n".join(
        part
        for part in (
            pytest_run.stdout,
            pytest_run.stderr,
            gate_run.stdout,
            gate_run.stderr,
        )
        if part
    )
    message = (
        "Prompt or eval files changed and verification failed. "
        "Fix the failure, then rerun pytest tests and python -m evals.gate "
        f"from backend/.\nChanged: {', '.join(watched[:20])}\n{detail[-4000:]}"
    )
    json.dump({"followup_message": message}, sys.stdout, ensure_ascii=False)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
