import re
from dataclasses import dataclass

from app.rag.embeddings import Embedder
from app.rag.store import Chunk, ProjectIndex

_WORD = re.compile(r"[A-Za-z_][A-Za-z0-9_]*")
_CAMEL = re.compile(r"(?<=[a-z0-9])(?=[A-Z])")
_STOP = {
    "the", "and", "for", "with", "what", "which", "does", "this", "that", "how", "are", "from",
    "who", "where", "when", "why", "will", "into", "about", "work", "works", "code", "explain",
}


@dataclass
class Hit:
    chunk: Chunk
    score: float


def terms(text: str) -> set[str]:
    out: set[str] = set()
    for word in _WORD.findall(text):
        if len(word) >= 3:
            out.add(word.lower())
        for part in _CAMEL.split(word):
            if len(part) >= 3:
                out.add(part.lower())
    return out - _STOP


def mentioned_names(text: str) -> set[str]:
    """Capitalised / camelCase words that look like code identifiers."""
    return {w.lower() for w in _WORD.findall(text) if w[0].isupper() or any(c.isupper() for c in w[1:])}


def keyword_score(query_terms: set[str], chunk: Chunk) -> float:
    if not query_terms:
        return 0.0
    name = terms(chunk.label) | terms(chunk.qualified_name)
    body = chunk.text.lower()
    hits = sum(1.0 if t in name else 0.3 if t in body else 0.0 for t in query_terms)
    return min(1.0, hits / len(query_terms))


async def retrieve(
    index: ProjectIndex,
    embedder: Embedder,
    query: str,
    k: int = 8,
    alpha: float = 0.7,
    pinned: set[str] | None = None,
) -> list[Hit]:
    """Hybrid: dense cosine + identifier keyword match; pinned qualified names first."""
    if len(index) == 0:
        return []
    pinned = pinned or set()
    q_terms = terms(query)
    names = mentioned_names(query)

    qv = await embedder.embed_query(query)
    candidates = {c.id: (c, s) for c, s in index.search(qv, k * 4)}

    # classes named in the question or pinned by the backend always compete
    with index.lock:
        for c in index.chunks:
            owner = c.label.split("#")[0].lower()
            if c.id not in candidates and (owner in names or c.qualified_name in pinned):
                candidates[c.id] = (c, 0.0)

    hits = []
    for c, dense in candidates.values():
        score = alpha * max(dense, 0.0) + (1 - alpha) * keyword_score(q_terms, c)
        if c.qualified_name in pinned:
            score += 1.0
        hits.append(Hit(c, round(score, 4)))
    hits.sort(key=lambda h: h.score, reverse=True)
    return hits[:k]
