"""v3 unit tests: classifier, pending resolver, validator, handle orchestration (mocked extract)."""
from __future__ import annotations

from unittest.mock import patch

import pytest

from app.common.intent_classifier import (
    HeuristicIntentClassifier,
    set_intent_classifier,
)
from app.common.pending_resolver import resolve_pending_action
from app.common.rule_validator import validate_extraction
from app.v3 import prompt_engine as v3


@pytest.fixture(autouse=True)
def _use_heuristic_classifier():
    set_intent_classifier(HeuristicIntentClassifier())
    yield
    set_intent_classifier(None)


def test_reject_hello():
    out = v3.handle("안녕", "2026-08-14T15:00:00+09:00")
    assert out["ok"] is False
    assert out["failReason"] == "chitchat"
    assert out["dialogIntent"] == "reject"
    assert out["needsSupplement"] is False


def test_reject_hh():
    out = v3.handle("ㅎㅎ", "2026-08-14T15:00:00+09:00")
    assert out["failReason"] == "chitchat"
    assert out["needsSupplement"] is False


def test_extract_complete_mocked():
    complete = {
        "ok": True,
        "targetFixed": {
            "mute": True,
            "name": ["카카오톡"],
            "packages": ["com.kakao.talk"],
            "content": [],
            "exceptions": [],
        },
        "condition": {
            "recurrence": "none",
            "delivery": {"absolute": "2026-08-14T15:00:00+09:00"},
            "expires": {"absolute": "2026-08-14T15:30:00+09:00"},
        },
        "assistantMessage": "ok",
        "failReason": "none",
        "needsSupplement": False,
        "dialogIntent": "extract",
    }
    with patch.object(v3, "run_extract", return_value=complete) as m:
        out = v3.handle("카톡 30분 받지마", "2026-08-14T15:00:00+09:00")
    assert out["ok"] is True
    m.assert_called_once()


def test_extract_incomplete_sets_pending_flags():
    incomplete = {
        "ok": False,
        "assistantMessage": "시간을 알려주세요",
        "failReason": "missing_time",
        "needsSupplement": True,
        "dialogIntent": "extract",
    }
    with patch.object(v3, "run_extract", return_value=incomplete):
        out = v3.handle("카톡 알림 받지마", "2026-08-14T15:00:00+09:00")
    assert out["needsSupplement"] is True
    assert out["failReason"] == "missing_time"


def test_pending_continue():
    complete = {
        "ok": True,
        "targetFixed": {"mute": True, "name": ["카카오톡"], "packages": [], "content": [], "exceptions": []},
        "condition": {"recurrence": "none"},
        "assistantMessage": "ok",
        "failReason": "none",
        "needsSupplement": False,
        "dialogIntent": "extract",
        "pendingClassification": "CONTINUE",
    }
    with patch.object(v3, "run_extract", return_value=complete) as m:
        out = v3.handle(
            "2시간",
            "2026-08-14T15:00:00+09:00",
            pending=True,
            pending_original="카톡 알림 받지마",
        )
    assert out["ok"] is True
    args = m.call_args
    assert "이전 사용자 명령" in args[0][0]
    assert "2시간" in args[0][0]


def test_pending_cancel():
    out = v3.handle(
        "취소해",
        "2026-08-14T15:00:00+09:00",
        pending=True,
        pending_original="카톡 알림 받지마",
    )
    assert out["ok"] is False
    assert out["failReason"] == "cancelled"
    assert out["needsSupplement"] is False
    assert out["pendingClassification"] == "CANCEL"


def test_pending_update():
    incomplete = {
        "ok": False,
        "assistantMessage": "시간을 알려주세요",
        "failReason": "missing_time",
        "needsSupplement": True,
        "dialogIntent": "extract",
        "pendingClassification": "UPDATE",
    }
    with patch.object(v3, "run_extract", return_value=incomplete) as m:
        out = v3.handle(
            "아 카톡 말고 인스타",
            "2026-08-14T15:00:00+09:00",
            pending=True,
            pending_original="카톡 알림 받지마",
        )
    assert out["needsSupplement"] is True
    assert resolve_pending_action("카톡 알림 받지마", "아 카톡 말고 인스타") == "UPDATE"
    assert "수정" in m.call_args[0][0] or "변경" in m.call_args[0][0]


def test_pending_new_request():
    with patch.object(
        v3,
        "run_extract",
        return_value={
            "ok": False,
            "failReason": "missing_time",
            "needsSupplement": True,
            "assistantMessage": "x",
            "dialogIntent": "extract",
            "pendingClassification": "NEW_REQUEST",
        },
    ) as m:
        out = v3.handle(
            "아 됐고 인스타 알림 받지마",
            "2026-08-14T15:00:00+09:00",
            pending=True,
            pending_original="카톡 알림 받지마",
        )
    assert resolve_pending_action("카톡 알림 받지마", "아 됐고 인스타 알림 받지마") == "NEW_REQUEST"
    # extract called with current prompt only (not merge)
    assert m.call_args[0][0] == "아 됐고 인스타 알림 받지마"
    assert out["pendingClassification"] == "NEW_REQUEST"


def test_pending_unresolved():
    out = v3.handle(
        "뭐라고?",
        "2026-08-14T15:00:00+09:00",
        pending=True,
        pending_original="카톡 알림 받지마",
    )
    assert out["failReason"] == "incomplete_rule"
    assert out["needsSupplement"] is True
    assert out["pendingClassification"] == "UNRESOLVED"


def test_validator_missing_time():
    r = validate_extraction(
        None,
        {"name": ["카카오톡"], "content": []},
        None,
    )
    assert r.ok is False
    assert r.fail_reason == "missing_time"
    assert r.needs_supplement is True


def test_validator_complete():
    r = validate_extraction(
        {
            "recurrence": "none",
            "delivery": {"absolute": "2026-08-14T15:00:00+09:00"},
            "expires": {"absolute": "2026-08-14T15:30:00+09:00"},
        },
        {"name": ["카카오톡"], "content": []},
        None,
    )
    assert r.ok is True
    assert r.mute is True
