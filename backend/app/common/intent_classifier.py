"""Intent classifier interface + implementations.

Labels:
  - extract: notification-control request
  - reject: unrelated chitchat / noise

Swap implementations via get_intent_classifier() / set_intent_classifier().
"""
from __future__ import annotations

import json
import re
from abc import ABC, abstractmethod
from pathlib import Path
from typing import Literal

from langsmith import traceable

DialogIntent = Literal["extract", "reject"]

_MODEL_DIR = Path(__file__).resolve().parent / "models"
_MODEL_PATH = _MODEL_DIR / "intent_clf.joblib"
_META_PATH = _MODEL_DIR / "intent_clf_meta.json"

# Seed examples for training / heuristic alignment
TRAIN_EXTRACT = [
    "카톡 30분 받지마",
    "인스타 알림만 허용해줘",
    "회의 중에는 문자 알림 받지마",
    "지금부터 1시간 카톡만 받아줘",
    "모든 알림 2시간 조용히",
    "매일 밤 10시부터 아침 6시까지 알림 받지마",
    "유튜브 알림 끄고 싶어",
    "광고 알림 30분 막아줘",
    "카톡이랑 인스타 1시간 받지마",
    "오후 6시까지 조용히 해줘",
    "5분 후부터 10분 동안 카톡 뮤트",
    "지메일만 받아줘 지금부턴 한시간",
    "카톡 알림 받지마",
    "문자 스팸 차단해줘 2시간",
    "네이버 알림 꺼줘",
]
TRAIN_REJECT = [
    "안녕",
    "안녕하세요",
    "ㅎㅎ",
    "ㅋㅋ",
    "고마워",
    "감사합니다",
    "뭐해",
    "심심해",
    "잘가",
    "ㅇㅋ",
    "응",
    "ㅎ2",
    "도움말",
    "좋은 아침",
    "😊",
    "롤할래?"
]


class IntentClassifier(ABC):
    @abstractmethod
    def classify(self, prompt: str) -> DialogIntent:
        ...


class HeuristicIntentClassifier(IntentClassifier):
    """Dependency-free fallback (tests / ST model missing)."""

    _EXTRACT_HINT = re.compile(
        r"(받지\s*마|막아|뮤트|조용|끄|허용|받아|알림|카톡|인스타|문자|"
        r"지메일|유튜브|분\s*동안|시간|까지|매일|매주|차단)",
        re.I,
    )
    _REJECT_ONLY = re.compile(
        r"^(안녕|안녕하세요|ㅎㅎ+|ㅋㅋ+|고마워|감사|ㅇㅋ|응|ㅎ2|잘가|뭐해|심심|"
        r"도움말|좋은\s*아침|😊+)$",
        re.I,
    )

    def classify(self, prompt: str) -> DialogIntent:
        text = (prompt or "").strip()
        if not text:
            return "reject"
        if self._REJECT_ONLY.match(text.replace(" ", "")) or self._REJECT_ONLY.match(text):
            return "reject"
        if self._EXTRACT_HINT.search(text):
            return "extract"
        # short non-rule utterances
        if len(text) <= 6 and not self._EXTRACT_HINT.search(text):
            return "reject"
        return "extract"


class EmbeddingLogisticIntentClassifier(IntentClassifier):
    """SentenceTransformer embeddings + LogisticRegression."""

    def __init__(self, model_name: str = "paraphrase-multilingual-MiniLM-L12-v2"):
        self.model_name = model_name
        self._encoder = None
        self._clf = None

    def _ensure_loaded(self) -> None:
        if self._clf is not None and self._encoder is not None:
            return
        if _MODEL_PATH.exists():
            import joblib

            bundle = joblib.load(_MODEL_PATH)
            self._encoder = bundle["encoder_name"]
            self._clf = bundle["clf"]
            self.model_name = bundle.get("encoder_name", self.model_name)
            # encoder must be re-created
            from sentence_transformers import SentenceTransformer

            self._st = SentenceTransformer(self.model_name)
            return
        self.fit_default()

    def fit_default(self) -> None:
        from sentence_transformers import SentenceTransformer
        from sklearn.linear_model import LogisticRegression
        import joblib
        import numpy as np

        self._st = SentenceTransformer(self.model_name)
        texts = TRAIN_EXTRACT + TRAIN_REJECT
        labels = ["extract"] * len(TRAIN_EXTRACT) + ["reject"] * len(TRAIN_REJECT)
        X = self._st.encode(texts, show_progress_bar=False)
        clf = LogisticRegression(max_iter=1000)
        clf.fit(np.asarray(X), labels)
        self._clf = clf
        _MODEL_DIR.mkdir(parents=True, exist_ok=True)
        joblib.dump(
            {"clf": clf, "encoder_name": self.model_name},
            _MODEL_PATH,
        )
        _META_PATH.write_text(
            json.dumps(
                {
                    "encoder": self.model_name,
                    "n_extract": len(TRAIN_EXTRACT),
                    "n_reject": len(TRAIN_REJECT),
                },
                ensure_ascii=False,
                indent=2,
            ),
            encoding="utf-8",
        )

    def classify(self, prompt: str) -> DialogIntent:
        self._ensure_loaded()
        from sentence_transformers import SentenceTransformer
        import numpy as np

        if not hasattr(self, "_st") or self._st is None:
            self._st = SentenceTransformer(self.model_name)
        emb = self._st.encode([prompt.strip()], show_progress_bar=False)
        pred = self._clf.predict(np.asarray(emb))[0]
        return "extract" if pred == "extract" else "reject"


_classifier: IntentClassifier | None = None


def set_intent_classifier(clf: IntentClassifier | None) -> None:
    global _classifier
    _classifier = clf


def get_intent_classifier() -> IntentClassifier:
    global _classifier
    if _classifier is not None:
        return _classifier
    # Prefer embedding model when deps + (optional) env allow; else heuristic.
    use_embed = True
    try:
        import os

        if os.getenv("INTENT_CLASSIFIER", "auto").strip().lower() in {
            "heuristic",
            "rules",
        }:
            use_embed = False
        if use_embed:
            import sentence_transformers  # noqa: F401
            import sklearn  # noqa: F401

            _classifier = EmbeddingLogisticIntentClassifier()
            return _classifier
    except Exception:
        pass
    _classifier = HeuristicIntentClassifier()
    return _classifier


@traceable(name="v3_intent_classify")
def classify_intent(prompt: str) -> DialogIntent:
    return get_intent_classifier().classify(prompt)
