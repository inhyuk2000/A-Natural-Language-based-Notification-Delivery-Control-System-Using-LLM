"""Scan tracked files or hook input for secrets and .env writes.

  python backend/scripts/secret_scan.py
  python backend/scripts/secret_scan.py --staged
"""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "backend"))

from scripts.harness_secrets import git_paths, scan_files, tracked_files  # noqa: E402


def main(argv: list[str]) -> int:
    staged = "--staged" in argv
    paths = git_paths(ROOT, cached=True) if staged else tracked_files(ROOT)
    hits = scan_files(paths, ROOT)
    if not hits:
        print("secret scan: clean")
        return 0
    print("secret scan failed:")
    for hit in hits:
        print(f"  {hit}")
    return 1


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
