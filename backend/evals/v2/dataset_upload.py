"""
golden_v2.jsonl → LangSmith Dataset 업로드
실행 (backend/ 에서):
  python -m evals.v2.dataset_upload
"""

from __future__ import annotations

import json
from pathlib import Path

from dotenv import load_dotenv
from langsmith import Client

BACKEND_ROOT = Path(__file__).resolve().parents[2]
JSONL_PATH = Path(__file__).resolve().parent / "datasets" / "golden_v2.jsonl"
DATASET_NAME = "promptengine_golden_v2"
load_dotenv(BACKEND_ROOT / ".env")
client = Client()


def load_examples(path: Path) -> list[dict]:
    examples: list[dict] = []
    with path.open(encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            row = json.loads(line)
            item: dict = {
                "inputs": row["inputs"],
                "outputs": row["outputs"],
            }
            if row.get("metadata"):
                item["metadata"] = row["metadata"]
            examples.append(item)
    return examples


examples = load_examples(JSONL_PATH)

if not client.has_dataset(dataset_name=DATASET_NAME):
    dataset = client.create_dataset(
        dataset_name=DATASET_NAME,
        description=(
            "PromptEngine v2 golden. "
            "Coverage: intent/time/target each >=5; chitchat>=15; N>=60. "
            "outputs.dialogIntent for route eval."
        ),
    )
    client.create_examples(dataset_id=dataset.id, examples=examples)
    print(f"Created '{DATASET_NAME}' with {len(examples)} examples")
else:
    print(f"Dataset '{DATASET_NAME}' already exists — skip upload")
