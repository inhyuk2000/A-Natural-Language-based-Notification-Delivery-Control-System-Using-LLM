"""
설치된 앱 라벨 ↔ packageName 자동 매핑 (cosine similarity).

Android가 보낸 installedApps[{packageName, labels[]}]에 대해
LLM이 추출한 앱 이름(name[])을 embedding 유사도로 매칭한다.

동의어 사전(app_synonyms.json)에 있으면 쿼리를 확장한 뒤,
확장어×라벨 cosine 중 최댓값으로 앱을 고른다.
"""

from __future__ import annotations

import json
import math
import os
from pathlib import Path
from typing import Any

from openai import OpenAI

EMBED_MODEL = os.getenv("APP_MAPPER_EMBED_MODEL", "text-embedding-3-small")
# 짧은 한글 별칭(카톡↔카카오톡)도 통과하도록 다소 낮게 시작. eval로 튜닝.
SIMILARITY_THRESHOLD = float(os.getenv("APP_MAPPER_THRESHOLD", "0.45"))

_SYNONYM_PATH = Path(__file__).with_name("app_synonyms.json")
_SYNONYM_MAP: dict[str, list[str]] | None = None


def _cosine(a: list[float], b: list[float]) -> float:
    if not a or not b or len(a) != len(b):
        return 0.0
    dot = sum(x * y for x, y in zip(a, b))
    na = math.sqrt(sum(x * x for x in a))
    nb = math.sqrt(sum(y * y for y in b))
    if na == 0.0 or nb == 0.0:
        return 0.0
    return dot / (na * nb)


def _embed_texts(client: OpenAI, texts: list[str]) -> list[list[float]]:
    if not texts:
        return []
    # OpenAI embeddings API: batch ok
    resp = client.embeddings.create(model=EMBED_MODEL, input=texts)
    # data 순서는 input 순서와 동일
    by_index = sorted(resp.data, key=lambda d: d.index)
    return [list(d.embedding) for d in by_index]


def _norm_key(s: str) -> str:
    return s.strip().lower().replace(" ", "")


def _load_synonym_map() -> dict[str, list[str]]:
    """각 단어 → 같은 그룹의 모든 표기 (원문 표기 유지)."""
    global _SYNONYM_MAP
    if _SYNONYM_MAP is not None:
        return _SYNONYM_MAP
    mapping: dict[str, list[str]] = {}
    if _SYNONYM_PATH.is_file():
        groups = json.loads(_SYNONYM_PATH.read_text(encoding="utf-8"))
        for group in groups:
            if not isinstance(group, list):
                continue
            terms = [str(t).strip() for t in group if str(t).strip()]
            if not terms:
                continue
            for t in terms:
                mapping[_norm_key(t)] = terms
    _SYNONYM_MAP = mapping
    return mapping


def _expand_queries(name: str) -> list[str]:
    """동의어 그룹이 있으면 그룹 전체(+원문), 없으면 [name]."""
    syn = _load_synonym_map()
    group = syn.get(_norm_key(name))
    if not group:
        return [name]
    out: list[str] = []
    for t in [name, *group]:
        t = t.strip()
        if t and t not in out:
            out.append(t)
    return out


def _normalize_apps(installed_apps: list[dict[str, Any]] | None) -> list[dict[str, Any]]:
    """[{packageName, labels: [str]}] 정규화."""
    out: list[dict[str, Any]] = []
    if not installed_apps:
        return out
    for app in installed_apps:
        if not isinstance(app, dict):
            continue
        pkg = str(app.get("packageName") or "").strip()
        if not pkg:
            continue
        raw_labels = app.get("labels") or []
        labels: list[str] = []
        if isinstance(raw_labels, list):
            for lab in raw_labels:
                s = str(lab).strip()
                if s and s.lower() not in {x.lower() for x in labels}:
                    labels.append(s)
        if not labels:
            continue
        out.append({"packageName": pkg, "labels": labels})
    return out


def resolve_packages(
    names: list[str],
    installed_apps: list[dict[str, Any]] | None,
    *,
    threshold: float = SIMILARITY_THRESHOLD,
) -> tuple[list[str], list[str], list[dict[str, Any]]]:
    """
    names(표시용 앱 이름) → packageName 목록.

    동의어가 있으면 쿼리를 확장한 뒤, 확장어×라벨 cosine 최댓값으로 매칭.

    Returns:
      (packages, unresolved_names, mapping_scores)
    """
    cleaned = [n.strip() for n in names if isinstance(n, str) and n.strip()]
    if not cleaned:
        return [], [], []

    apps = _normalize_apps(installed_apps)
    if not apps:
        return [], cleaned, [
            {
                "name": n,
                "packageName": None,
                "label": None,
                "score": None,
                "matched": False,
                "queriesUsed": _expand_queries(n),
            }
            for n in cleaned
        ]

    # 후보: (packageName, label) 평탄화
    candidates: list[tuple[str, str]] = []
    for app in apps:
        pkg = app["packageName"]
        for lab in app["labels"]:
            candidates.append((pkg, lab))

    # name → 확장 쿼리들
    expansions: list[list[str]] = [_expand_queries(n) for n in cleaned]
    unique_queries: list[str] = []
    seen_q: set[str] = set()
    for group in expansions:
        for q in group:
            if q not in seen_q:
                seen_q.add(q)
                unique_queries.append(q)

    client = OpenAI()
    query_embs = _embed_texts(client, unique_queries)
    query_emb_by_text = dict(zip(unique_queries, query_embs))
    label_embs = _embed_texts(client, [lab for _, lab in candidates])

    packages: list[str] = []
    unresolved: list[str] = []
    mapping_scores: list[dict[str, Any]] = []
    seen: set[str] = set()

    for name, queries in zip(cleaned, expansions):
        best_pkg = ""
        best_label = ""
        best_score = -1.0
        best_query = name
        for q in queries:
            q_emb = query_emb_by_text.get(q)
            if q_emb is None:
                continue
            for (pkg, lab), l_emb in zip(candidates, label_embs):
                score = _cosine(q_emb, l_emb)
                if score > best_score:
                    best_score = score
                    best_pkg = pkg
                    best_label = lab
                    best_query = q
        matched = bool(best_pkg) and best_score >= threshold
        mapping_scores.append(
            {
                "name": name,
                "queriesUsed": queries,
                "matchedQuery": best_query,
                "packageName": best_pkg or None,
                "label": best_label or None,
                "score": round(best_score, 6) if best_score >= 0 else None,
                "matched": matched,
            }
        )
        if matched:
            if best_pkg not in seen:
                seen.add(best_pkg)
                packages.append(best_pkg)
        else:
            unresolved.append(name)

    return packages, unresolved, mapping_scores
