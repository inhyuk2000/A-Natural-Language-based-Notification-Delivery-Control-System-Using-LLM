"""Offline route regression gate.

Scores golden_v2 with HeuristicIntentClassifier and route_correctness.
No OpenAI call. Exits non-zero when the score drops below the committed baseline.

  python -m evals.gate
  python -m evals.gate --write-baseline
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

from app.common.intent_classifier import HeuristicIntentClassifier
from evals.v3.evaluators import route_correctness

EVAL_ROOT = Path(__file__).resolve().parent
JSONL_PATH = EVAL_ROOT / "v2" / "datasets" / "golden_v2.jsonl"
BASELINE_PATH = EVAL_ROOT / "baselines" / "v3_route.json"


def score_routes() -> tuple[float, int, int, list[str]]:
    classifier = HeuristicIntentClassifier()
    passed = 0
    total = 0
    failures: list[str] = []
    with JSONL_PATH.open(encoding="utf-8") as handle:
        for line in handle:
            line = line.strip()
            if not line:
                continue
            row = json.loads(line)
            inputs = row["inputs"]
            predicted = classifier.classify(str(inputs.get("prompt") or ""))
            result = route_correctness(
                {"dialogIntent": predicted},
                row.get("outputs") or {},
                inputs,
            )
            total += 1
            if float(result["score"]) == 1.0:
                passed += 1
            else:
                failures.append(str(row.get("id") or f"row-{total}"))
    score = round(passed / total, 4) if total else 0.0
    return score, passed, total, failures


def baseline_payload(score: float, passed: int, total: int) -> dict:
    return {
        "metric": "route_correctness",
        "dataset": "evals/v2/datasets/golden_v2.jsonl",
        "classifier": "HeuristicIntentClassifier",
        "n": total,
        "passed": passed,
        "min_score": score,
        "note": (
            "CI blocks a merge when heuristic route_correctness on golden_v2 "
            "falls below min_score. This gate does not call OpenAI."
        ),
    }


def write_baseline(score: float, passed: int, total: int) -> None:
    BASELINE_PATH.parent.mkdir(parents=True, exist_ok=True)
    BASELINE_PATH.write_text(
        json.dumps(baseline_payload(score, passed, total), ensure_ascii=False, indent=2)
        + "\n",
        encoding="utf-8",
    )


def main(argv: list[str] | None = None) -> int:
    args = list(sys.argv[1:] if argv is None else argv)
    score, passed, total, failures = score_routes()
    if "--write-baseline" in args:
        write_baseline(score, passed, total)
        print(f"wrote {BASELINE_PATH} route_correctness={score} ({passed}/{total})")
        return 0

    if not BASELINE_PATH.is_file():
        print(f"missing baseline: {BASELINE_PATH}")
        print("run: python -m evals.gate --write-baseline")
        return 2

    baseline = json.loads(BASELINE_PATH.read_text(encoding="utf-8"))
    minimum = float(baseline["min_score"])
    print(
        f"route_correctness {score:.4f} ({passed}/{total}) "
        f"baseline {minimum:.4f}"
    )
    if score < minimum:
        print("eval gate failed")
        for item in failures[:15]:
            print(f"  {item}")
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
