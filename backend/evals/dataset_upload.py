"""
golden_v1.jsonl → LangSmith Dataset 업로드
실행 (backend/ 에서):
  python -m evals.dataset_upload
"""

# 필요한 패키지 설치
from __future__ import annotations
import json
from pathlib import Path
from dotenv import load_dotenv
from langsmith import Client

# 환경 변수 로드 및 경로 설정
BACKEND_ROOT = Path(__file__).resolve().parents[1]
JSONL_PATH = Path(__file__).resolve().parent / "datasets" / "golden_v1.jsonl"
DATASET_NAME = "promptengine_golden_v1"
load_dotenv(BACKEND_ROOT / ".env")
client = Client()

# 데이터셋 업로드 함수 정의
def load_examples(path: Path) -> list[tuple[dict, dict]]:
    # Type Hint: examples라는 빈 리스트를 만들되, 앞으로 (입력 dict, 정답 출력 dict) 형태의 tuple들을 담을 거라고 선언.
    examples: list[tuple[dict, dict]] = []
    with path.open(encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            row = json.loads(line)
            # inputs  = handle()에 넣을 것
            # outputs = 정답(reference) — evaluate 때만 씀
            examples.append((row["inputs"], row["outputs"]))
    return examples
examples = load_examples(JSONL_PATH)

# 데이터셋이 이미 존재하는지 확인하고, 없으면 새로 생성 후 업로드
if not client.has_dataset(dataset_name=DATASET_NAME):
    dataset = client.create_dataset(
        dataset_name=DATASET_NAME,
        description="PromptEngine v1 golden set (few-shot seed)",
    )
    # examples → LangSmith create_examples용 inputs/outputs 시퀀스로 분리
    # inp/out 은 그냥 루프 변수명 (JSON 키 "inputs"/"outputs" 와 다름)
    # outputs 는 케이스마다 키가 다를 수 있음 (ok:false 면 targetFixed 없음) → 통째로 전달
    inputs, outputs = zip(*[(inp, out) for inp, out in examples])
    client.create_examples(
        inputs=inputs,
        outputs=outputs,
        dataset_id=dataset.id,
    )
    print(f"Created '{DATASET_NAME}' with {len(examples)} examples")
else:
    print(f"Dataset '{DATASET_NAME}' already exists — skip upload")
