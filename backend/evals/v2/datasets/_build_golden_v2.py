"""
Build evals/v2/datasets/golden_v2.jsonl from golden_v1.jsonl.

Coverage (simple):
- intent: mute/allow/reject each >= 5
- time: duration/until_absolute/relative_delay/daily/weekly each >= 5 (ok=true only)
- target: all/single_app/multi_app/content/app_and_content each >= 5 (ok=true only)
- route: extract (existing) + chitchat >= 15
- total N >= 60
"""
from __future__ import annotations

import json
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]  # evals/v2
# Prefer rebuilding from v1 golden; fall back to existing v2 if present.
SRC_V1 = ROOT.parent / "v1" / "datasets" / "golden_v1.jsonl"
SRC_V2 = ROOT / "datasets" / "golden_v2.jsonl"
SRC = SRC_V1 if SRC_V1.exists() else SRC_V2
DST = ROOT / "datasets" / "golden_v2.jsonl"
FIXTURE_PATH = ROOT.parent / "v1" / "fixtures" / "installed_apps_device.json"
APPS: list = json.loads(FIXTURE_PATH.read_text(encoding="utf-8"))

NOW = "2026-08-14T15:00:00+09:00"
PKG = {
    "카카오톡": "com.kakao.talk",
    "인스타그램": "com.instagram.android",
    "Gmail": "com.google.android.gm",
    "유튜브": "com.google.android.youtube",
    "네이버": "com.nhn.android.search",
    "메시지": "com.samsung.android.messaging",
}


def cond_none(delivery: str, expires: str) -> dict:
    return {
        "recurrence": "none",
        "days_of_week": [],
        "window_start": None,
        "window_end": None,
        "delivery": {"absolute": delivery},
        "expires": {"absolute": expires},
    }


def cond_daily(ws: str, we: str, delivery: str, expires: str) -> dict:
    return {
        "recurrence": "daily",
        "days_of_week": [],
        "window_start": ws,
        "window_end": we,
        "delivery": {"absolute": delivery},
        "expires": {"absolute": expires},
    }


def cond_weekly(days: list[int], ws: str, we: str, delivery: str, expires: str) -> dict:
    return {
        "recurrence": "weekly",
        "days_of_week": days,
        "window_start": ws,
        "window_end": we,
        "delivery": {"absolute": delivery},
        "expires": {"absolute": expires},
    }


def target(mute: bool, names: list[str], contents: list[str] | None = None) -> dict:
    contents = contents or []
    packages = [PKG[n] for n in names if n in PKG]
    return {
        "mute": mute,
        "name": names,
        "content": contents,
        "packages": packages,
    }


def meta(intent: str, time: str | None, target_v: str | None, route: str = "extract") -> dict:
    m: dict = {"intent": intent, "route": route}
    if time:
        m["time"] = time
    if target_v:
        m["target"] = target_v
    return m


def classify_row(r: dict) -> tuple[str, str | None, str | None, str]:
    out = r.get("outputs") or {}
    ok = bool(out.get("ok"))
    tf = out.get("targetFixed") or {}
    cond = out.get("condition") or {}
    tags = set(r.get("tags") or [])
    prompt = (r.get("inputs") or {}).get("prompt") or ""
    ct = (r.get("inputs") or {}).get("currentTime") or ""

    if out.get("dialogIntent") == "chitchat" or out.get("failReason") == "chitchat":
        return "reject", None, None, "chitchat"

    if not ok:
        intent = "reject"
    elif tf.get("mute") is True:
        intent = "mute"
    elif tf.get("mute") is False:
        intent = "allow"
    else:
        intent = "reject"

    time: str | None = None
    if ok:
        rec = cond.get("recurrence") or "none"
        delivery = (cond.get("delivery") or {}).get("absolute")
        if rec == "daily":
            time = "daily"
        elif rec == "weekly":
            time = "weekly"
        elif delivery and ct and delivery != ct:
            time = "relative_delay"
        elif "until_absolute" in tags or ("까지" in prompt and "동안" not in prompt):
            time = "until_absolute"
        else:
            time = "duration"

    target_v: str | None = None
    if ok:
        names = tf.get("name") or []
        pkgs = tf.get("packages") or []
        contents = tf.get("content") or []
        n_apps = max(len(names), len(pkgs))
        if not names and not pkgs and not contents:
            target_v = "all"
        elif contents and n_apps >= 1:
            target_v = "app_and_content"
        elif contents and n_apps == 0:
            target_v = "content"
        elif n_apps >= 2:
            target_v = "multi_app"
        elif n_apps == 1:
            target_v = "single_app"
        else:
            target_v = "all"

    route = "extract"
    return intent, time, target_v, route


def enrich(r: dict) -> dict:
    intent, time, target_v, route = classify_row(r)
    out = dict(r.get("outputs") or {})
    out.setdefault("dialogIntent", route)
    if route == "chitchat":
        out.setdefault("failReason", "chitchat")
        out.setdefault("needsSupplement", False)
        out["ok"] = False
    inputs = dict(r.get("inputs") or {})
    inputs["installedApps"] = APPS
    row = {
        "id": r["id"],
        "inputs": inputs,
        "outputs": out,
        "metadata": meta(intent, time, target_v, route),
    }
    if r.get("tags"):
        row["tags"] = r["tags"]
    if r.get("note"):
        row["note"] = r["note"]
    return row


def case(
    id_: str,
    prompt: str,
    outputs: dict,
    *,
    intent: str,
    time: str | None,
    target_v: str | None,
    route: str = "extract",
    current_time: str = NOW,
    tags: list[str] | None = None,
    note: str = "",
) -> dict:
    out = dict(outputs)
    out["dialogIntent"] = route
    if route == "chitchat":
        out.setdefault("ok", False)
        out.setdefault("failReason", "chitchat")
        out.setdefault("needsSupplement", False)
    row = {
        "id": id_,
        "inputs": {
            "prompt": prompt,
            "currentTime": current_time,
            "installedApps": APPS,
        },
        "outputs": out,
        "metadata": meta(intent, time, target_v, route),
    }
    if tags:
        row["tags"] = tags
    if note:
        row["note"] = note
    return row


def coverage(rows: list[dict]) -> dict[str, Counter]:
    c = {
        "intent": Counter(),
        "time": Counter(),
        "target": Counter(),
        "route": Counter(),
    }
    for r in rows:
        m = r.get("metadata") or {}
        c["intent"][m.get("intent", "?")] += 1
        c["route"][m.get("route", "?")] += 1
        if m.get("intent") != "reject" and m.get("route") != "chitchat":
            if m.get("time"):
                c["time"][m["time"]] += 1
            if m.get("target"):
                c["target"][m["target"]] += 1
    return c


def main() -> None:
    raw = []
    with SRC.open(encoding="utf-8") as f:
        for line in f:
            if line.strip():
                raw.append(json.loads(line))

    # If source is already v2, keep only original (non-v2_*) rows as base.
    if SRC == SRC_V2:
        raw = [r for r in raw if not str(r.get("id", "")).startswith("v2_")]

    rows = [enrich(r) for r in raw]
    existing_ids = {r["id"] for r in rows}

    extras: list[dict] = []

    # --- time gaps: until_absolute (3→5) ---
    extras += [
        case(
            "v2_mute_youtube_until_18",
            "오늘 오후 6시까지 유튜브 알림 받지마",
            {
                "ok": True,
                "targetFixed": target(True, ["유튜브"]),
                "condition": cond_none(NOW, "2026-08-14T18:00:00+09:00"),
            },
            intent="mute",
            time="until_absolute",
            target_v="single_app",
            tags=["mute", "until_absolute"],
        ),
        case(
            "v2_allow_kakao_until_20",
            "오늘 밤 8시까지 카톡만 받아줘",
            {
                "ok": True,
                "targetFixed": target(False, ["카카오톡"]),
                "condition": cond_none(NOW, "2026-08-14T20:00:00+09:00"),
            },
            intent="allow",
            time="until_absolute",
            target_v="single_app",
            tags=["allow", "until_absolute"],
        ),
    ]

    # --- relative_delay (2→5) ---
    extras += [
        case(
            "v2_mute_all_delay_10_then_30m",
            "10분 후부터 30분 동안 모든 알림 받지마",
            {
                "ok": True,
                "targetFixed": target(True, []),
                "condition": cond_none(
                    "2026-08-14T15:10:00+09:00", "2026-08-14T15:40:00+09:00"
                ),
            },
            intent="mute",
            time="relative_delay",
            target_v="all",
            tags=["mute", "relative_delay"],
        ),
        case(
            "v2_allow_gmail_delay_5_then_1h",
            "5분 후부터 1시간 지메일만 받아줘",
            {
                "ok": True,
                "targetFixed": target(False, ["Gmail"]),
                "condition": cond_none(
                    "2026-08-14T15:05:00+09:00", "2026-08-14T16:05:00+09:00"
                ),
            },
            intent="allow",
            time="relative_delay",
            target_v="single_app",
            tags=["allow", "relative_delay"],
        ),
        case(
            "v2_mute_insta_delay_15_then_20m",
            "15분 뒤부터 20분 동안 인스타 받지마",
            {
                "ok": True,
                "targetFixed": target(True, ["인스타그램"]),
                "condition": cond_none(
                    "2026-08-14T15:15:00+09:00", "2026-08-14T15:35:00+09:00"
                ),
            },
            intent="mute",
            time="relative_delay",
            target_v="single_app",
            tags=["mute", "relative_delay"],
        ),
    ]

    # --- daily (2→5) ---
    extras += [
        case(
            "v2_mute_kakao_daily_2300_0700",
            "매일 밤 11시부터 아침 7시까지 카톡 받지마",
            {
                "ok": True,
                "targetFixed": target(True, ["카카오톡"]),
                "condition": cond_daily(
                    "23:00",
                    "07:00",
                    "2026-08-14T23:00:00+09:00",
                    "2026-08-15T07:00:00+09:00",
                ),
            },
            intent="mute",
            time="daily",
            target_v="single_app",
            tags=["mute", "daily"],
        ),
        case(
            "v2_allow_messages_daily_0900_1800",
            "매일 오전 9시부터 오후 6시까지 메시지만 받아줘",
            {
                "ok": True,
                "targetFixed": target(False, ["메시지"]),
                "condition": cond_daily(
                    "09:00",
                    "18:00",
                    "2026-08-15T09:00:00+09:00",
                    "2026-08-15T18:00:00+09:00",
                ),
            },
            intent="allow",
            time="daily",
            target_v="single_app",
            tags=["allow", "daily"],
        ),
        case(
            "v2_mute_ads_daily_2200_0800",
            "매일 밤 10시부터 아침 8시까지 광고 알림 받지마",
            {
                "ok": True,
                "targetFixed": target(True, [], ["광고"]),
                "condition": cond_daily(
                    "22:00",
                    "08:00",
                    "2026-08-14T22:00:00+09:00",
                    "2026-08-15T08:00:00+09:00",
                ),
            },
            intent="mute",
            time="daily",
            target_v="content",
            tags=["mute", "daily", "content"],
        ),
    ]

    # --- weekly (1→5) ---
    extras += [
        case(
            "v2_mute_all_weekly_weekend_1000_1800",
            "주말 오전 10시부터 오후 6시까지 모든 알림 받지마",
            {
                "ok": True,
                "targetFixed": target(True, []),
                "condition": cond_weekly(
                    [6, 7],
                    "10:00",
                    "18:00",
                    "2026-08-15T10:00:00+09:00",
                    "2026-08-15T18:00:00+09:00",
                ),
            },
            intent="mute",
            time="weekly",
            target_v="all",
            tags=["mute", "weekly"],
            note="토=6,일=7",
        ),
        case(
            "v2_mute_kakao_weekly_tue_thu_1300_1500",
            "화목이 오후 1시부터 3시까지 카톡 받지마",
            {
                "ok": True,
                "targetFixed": target(True, ["카카오톡"]),
                "condition": cond_weekly(
                    [2, 4],
                    "13:00",
                    "15:00",
                    "2026-08-18T13:00:00+09:00",
                    "2026-08-18T15:00:00+09:00",
                ),
            },
            intent="mute",
            time="weekly",
            target_v="single_app",
            tags=["mute", "weekly"],
        ),
        case(
            "v2_allow_gmail_weekly_mwf_0900_1200",
            "월수금 오전 9시부터 12시까지 지메일만 받아줘",
            {
                "ok": True,
                "targetFixed": target(False, ["Gmail"]),
                "condition": cond_weekly(
                    [1, 3, 5],
                    "09:00",
                    "12:00",
                    "2026-08-14T09:00:00+09:00",
                    "2026-08-14T12:00:00+09:00",
                ),
            },
            intent="allow",
            time="weekly",
            target_v="single_app",
            tags=["allow", "weekly"],
        ),
        case(
            "v2_mute_youtube_weekly_sun_2000_2200",
            "일요일 밤 8시부터 10시까지 유튜브 받지마",
            {
                "ok": True,
                "targetFixed": target(True, ["유튜브"]),
                "condition": cond_weekly(
                    [7],
                    "20:00",
                    "22:00",
                    "2026-08-16T20:00:00+09:00",
                    "2026-08-16T22:00:00+09:00",
                ),
            },
            intent="mute",
            time="weekly",
            target_v="single_app",
            tags=["mute", "weekly"],
        ),
    ]

    # --- multi_app (2→5) ---
    extras += [
        case(
            "v2_mute_kakao_youtube_30m",
            "지금부터 30분 카톡이랑 유튜브 받지마",
            {
                "ok": True,
                "targetFixed": target(True, ["카카오톡", "유튜브"]),
                "condition": cond_none(NOW, "2026-08-14T15:30:00+09:00"),
            },
            intent="mute",
            time="duration",
            target_v="multi_app",
            tags=["mute", "multi_app", "duration"],
        ),
        case(
            "v2_allow_kakao_gmail_2h",
            "지금부터 2시간 카톡이랑 지메일만 받아줘",
            {
                "ok": True,
                "targetFixed": target(False, ["카카오톡", "Gmail"]),
                "condition": cond_none(NOW, "2026-08-14T17:00:00+09:00"),
            },
            intent="allow",
            time="duration",
            target_v="multi_app",
            tags=["allow", "multi_app", "duration"],
        ),
        case(
            "v2_mute_insta_naver_1h",
            "1시간 동안 인스타랑 네이버 알림 받지마",
            {
                "ok": True,
                "targetFixed": target(True, ["인스타그램", "네이버"]),
                "condition": cond_none(NOW, "2026-08-14T16:00:00+09:00"),
            },
            intent="mute",
            time="duration",
            target_v="multi_app",
            tags=["mute", "multi_app", "duration"],
        ),
    ]

    # --- content (4→5): one more pure content ---
    extras += [
        case(
            "v2_mute_promo_1h",
            "지금부터 1시간 프로모션 알림 받지마",
            {
                "ok": True,
                "targetFixed": target(True, [], ["프로모션"]),
                "condition": cond_none(NOW, "2026-08-14T16:00:00+09:00"),
            },
            intent="mute",
            time="duration",
            target_v="content",
            tags=["mute", "content", "duration"],
        ),
    ]

    # --- chitchat >= 15 ---
    chitchats = [
        ("v2_chitchat_hi", "안녕"),
        ("v2_chitchat_hello", "안녕하세요"),
        ("v2_chitchat_h2", "ㅎ2"),
        ("v2_chitchat_hh", "ㅎㅎ"),
        ("v2_chitchat_kkk", "ㅋㅋ"),
        ("v2_chitchat_thanks", "고마워"),
        ("v2_chitchat_thank_you", "감사합니다"),
        ("v2_chitchat_ok", "ㅇㅋ"),
        ("v2_chitchat_yes", "응"),
        ("v2_chitchat_lol", "ㅋㅋㅋ"),
        ("v2_chitchat_whats_up", "뭐해"),
        ("v2_chitchat_bored", "심심해"),
        ("v2_chitchat_good_morning", "좋은 아침"),
        ("v2_chitchat_bye", "잘가"),
        ("v2_chitchat_emoji", "😊"),
        ("v2_chitchat_help_generic", "도움말"),
    ]
    for cid, prompt in chitchats:
        extras.append(
            case(
                cid,
                prompt,
                {
                    "ok": False,
                    "failReason": "chitchat",
                    "needsSupplement": False,
                    "assistantMessage": "알림 규칙을 등록하려면 시간과 대상을 함께 말씀해주세요.",
                },
                intent="reject",
                time=None,
                target_v=None,
                route="chitchat",
                tags=["chitchat"],
                note="routing gold: chitchat",
            )
        )

    for e in extras:
        if e["id"] in existing_ids:
            raise SystemExit(f"duplicate id: {e['id']}")
        rows.append(e)
        existing_ids.add(e["id"])

    cov = coverage(rows)
    print("N=", len(rows))
    for k, ctr in cov.items():
        print(k, dict(ctr))

    required_time = ["duration", "until_absolute", "relative_delay", "daily", "weekly"]
    required_target = ["all", "single_app", "multi_app", "content", "app_and_content"]
    required_intent = ["mute", "allow", "reject"]
    ok = True
    if len(rows) < 60:
        print("FAIL total < 60")
        ok = False
    for x in required_intent:
        if cov["intent"][x] < 5:
            print(f"FAIL intent {x}={cov['intent'][x]}")
            ok = False
    for x in required_time:
        if cov["time"][x] < 5:
            print(f"FAIL time {x}={cov['time'][x]}")
            ok = False
    for x in required_target:
        if cov["target"][x] < 5:
            print(f"FAIL target {x}={cov['target'][x]}")
            ok = False
    if cov["route"]["chitchat"] < 15:
        print(f"FAIL chitchat={cov['route']['chitchat']}")
        ok = False
    if not ok:
        raise SystemExit("coverage not met")

    with DST.open("w", encoding="utf-8") as f:
        for r in rows:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")
    print("wrote", DST)


if __name__ == "__main__":
    main()
