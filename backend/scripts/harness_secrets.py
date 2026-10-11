"""Secret and .env checks shared by CI, git hooks, and Cursor hooks."""

from __future__ import annotations

import re
import subprocess
from pathlib import Path

SECRET_PATTERNS: list[tuple[str, re.Pattern[str]]] = [
    ("openai", re.compile(r"sk-(?:proj-)?[A-Za-z0-9_\-]{20,}")),
    ("aws", re.compile(r"\bAKIA[0-9A-Z]{16}\b")),
    ("github", re.compile(r"\bghp_[A-Za-z0-9]{20,}\b")),
    ("slack", re.compile(r"\bxox[baprs]-[A-Za-z0-9-]{10,}\b")),
    ("langsmith", re.compile(r"\blsv2_(?:pt|sk)_[A-Za-z0-9_]{10,}\b")),
]

_COPY_EXAMPLE = re.compile(
    r"(?i)\b(?:cp|copy)\b[^\n]*\.env\.example[^\n]*\.env\b"
)
_GIT_ADD_ENV = re.compile(r"(?i)\bgit\s+add\b[^\n]*\.env(?!\.example)\b")
_WRITE_ENV = re.compile(
    r"(?i)(?:>>?\s*|Out-File\s+|Set-Content\s+|Add-Content\s+|tee\s+|"
    r"New-Item\s+|ni\s+)[^\n]*\.env(?!\.example)\b"
)


def find_secrets(text: str) -> list[str]:
    found: list[str] = []
    for name, pattern in SECRET_PATTERNS:
        match = pattern.search(text or "")
        if match:
            raw = match.group(0)
            found.append(f"{name}:{raw[:6]}...")
    return found


def is_protected_env_path(path: str) -> bool:
    name = Path(path.replace("\\", "/")).name
    if name == ".env.example":
        return False
    return name == ".env" or name.startswith(".env.")


def shell_env_violation(command: str) -> str | None:
    if find_secrets(command):
        return "command contains a secret"
    if _COPY_EXAMPLE.search(command):
        return None
    if _GIT_ADD_ENV.search(command):
        return "git add of an env file"
    if _WRITE_ENV.search(command):
        return "command writes an env file"
    return None


def scan_files(paths: list[Path], root: Path | None = None) -> list[str]:
    hits: list[str] = []
    for path in paths:
        if not path.is_file() or path.stat().st_size > 5_000_000:
            continue
        try:
            text = path.read_text(encoding="utf-8")
        except (UnicodeDecodeError, OSError):
            continue
        label = str(path)
        if root is not None:
            try:
                label = str(path.relative_to(root))
            except ValueError:
                label = str(path)
        for match in find_secrets(text):
            hits.append(f"{label}: {match}")
    return hits


def git_paths(root: Path, cached: bool) -> list[Path]:
    args = ["git", "diff", "--name-only", "--diff-filter=ACM"]
    if cached:
        args.append("--cached")
    try:
        out = subprocess.check_output(args, cwd=root, text=True, stderr=subprocess.DEVNULL)
    except (subprocess.CalledProcessError, FileNotFoundError):
        return []
    paths: list[Path] = []
    for line in out.splitlines():
        rel = line.strip()
        if rel:
            paths.append(root / rel)
    return paths


def tracked_files(root: Path) -> list[Path]:
    try:
        out = subprocess.check_output(
            ["git", "ls-files"], cwd=root, text=True, stderr=subprocess.DEVNULL
        )
    except (subprocess.CalledProcessError, FileNotFoundError):
        return []
    return [root / line.strip() for line in out.splitlines() if line.strip()]
