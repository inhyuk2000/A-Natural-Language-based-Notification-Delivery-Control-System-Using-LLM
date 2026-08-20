"""
Train / refresh SentenceTransformer + LogisticRegression intent classifier.

  cd backend
  python -m app.common.train_intent_classifier
"""
from __future__ import annotations

from app.common.intent_classifier import EmbeddingLogisticIntentClassifier


def main() -> None:
    clf = EmbeddingLogisticIntentClassifier()
    clf.fit_default()
    print("Saved intent classifier to app/common/models/")
    # smoke
    for t in ("안녕", "카톡 30분 받지마", "ㅎㅎ", "인스타만 받아줘"):
        print(t, "->", clf.classify(t))


if __name__ == "__main__":
    main()
