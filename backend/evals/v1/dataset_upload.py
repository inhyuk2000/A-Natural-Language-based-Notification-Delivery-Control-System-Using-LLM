"""
golden_v1.jsonl → LangSmith Dataset 업로드
실행 (backend/ 에서):
  python -m evals.v1.dataset_upload
"""

from __future__ import annotations

import json
from pathlib import Path

from dotenv import load_dotenv
from langsmith import Client

BACKEND_ROOT = Path(__file__).resolve().parents[2]
JSONL_PATH = Path(__file__).resolve().parent / "datasets" / "golden_v1.jsonl"
DATASET_NAME = "promptengine_golden_v1"
load_dotenv(BACKEND_ROOT / ".env")
client = Client()


def load_examples(path: Path) -> list[tuple[dict, dict]]:
    examples: list[tuple[dict, dict]] = []
    with path.open(encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            row = json.loads(line)
            examples.append((row["inputs"], row["outputs"]))
    return examples


examples = load_examples(JSONL_PATH)

if not client.has_dataset(dataset_name=DATASET_NAME):
    dataset = client.create_dataset(
        dataset_name=DATASET_NAME,
        description="PromptEngine v1 golden set (few-shot seed)",
    )
    inputs, outputs = zip(*[(inp, out) for inp, out in examples])
    client.create_examples(
        inputs=inputs,
        outputs=outputs,
        dataset_id=dataset.id,
    )
    print(f"Created '{DATASET_NAME}' with {len(examples)} examples")
else:
    print(f"Dataset '{DATASET_NAME}' already exists — skip upload")
