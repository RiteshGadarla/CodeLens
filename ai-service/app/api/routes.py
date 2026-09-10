from fastapi import APIRouter, Depends, HTTPException, Request, Response

from app.api.schemas import (
    AskRequest, AskResponse, IndexRequest, IndexResponse, IndexStats,
    ReportRequest, ReportResponse, Source,
)
from app.llm.gemini import Completion, LLMError
from app.llm.prompts import ask_prompt, report_prompt
from app.rag.retriever import Hit, retrieve
from app.rag.store import Chunk
from app.services import Services

router = APIRouter()

EMBED_TEXT_CHARS = 2000


def services(request: Request) -> Services:
    return request.app.state.services


@router.get("/health")
async def health(svc: Services = Depends(services)):
    return {
        "status": "ok",
        "model": svc.llm.model,
        "embedder": svc.embedder.name,
        "llmConfigured": bool(svc.settings.gemini_api_key),
    }


@router.get("/projects/{project_id}/index", response_model=IndexStats)
async def index_stats(project_id: int, svc: Services = Depends(services)):
    idx = svc.store.get(project_id)
    return IndexStats(
        project_id=project_id, chunks=len(idx), files=len({c.path for c in idx.chunks}), embedder=svc.embedder.name
    )


@router.post("/projects/{project_id}/index", response_model=IndexResponse)
async def index(project_id: int, req: IndexRequest, svc: Services = Depends(services)):
    idx = svc.store.get(project_id)
    if req.reset:
        idx.reset()

    incoming = [Chunk(**c.model_dump()) for c in req.chunks]
    ids = {c.id for c in incoming}
    paths = {c.path for c in incoming}
    removed_paths = set(req.removed_paths)
    # deleted files, and stale chunks of re-sent files
    removed = idx.remove(lambda c: c.path in removed_paths or (c.path in paths and c.id not in ids))

    known = idx.hashes()
    fresh = [c for c in incoming if known.get(c.id) != c.hash]
    if fresh:
        vectors = await svc.embedder.embed_documents([embed_text(c) for c in fresh])
        idx.upsert(fresh, vectors)
    idx.save()
    return IndexResponse(added=len(fresh), skipped=len(incoming) - len(fresh), removed=removed, total=len(idx))


@router.delete("/projects/{project_id}/index", status_code=204)
async def delete_index(project_id: int, svc: Services = Depends(services)):
    svc.store.delete(project_id)
    return Response(status_code=204)


@router.post("/projects/{project_id}/ask", response_model=AskResponse)
async def ask(project_id: int, req: AskRequest, svc: Services = Depends(services)):
    hits = await retrieve(svc.store.get(project_id), svc.embedder, req.question, k=req.top_k, pinned=set(req.focus))
    prompt, used = ask_prompt(req.question, req.facts, hits, svc.settings.max_context_chars)
    completion = await generate(svc, prompt)
    return AskResponse(answer=completion.text, sources=to_sources(used), model=completion.model, cached=completion.cached)


@router.post("/projects/{project_id}/report", response_model=ReportResponse)
async def report(project_id: int, req: ReportRequest, svc: Services = Depends(services)):
    target = req.facts.get("target") or {}
    query = f"{target.get('label', '')} {target.get('qualifiedName', '')}".strip() or "impact"
    hits = await retrieve(svc.store.get(project_id), svc.embedder, query, k=req.top_k, pinned=set(req.focus))
    prompt, used = report_prompt(req.facts, hits, svc.settings.max_context_chars)
    completion = await generate(svc, prompt)
    return ReportResponse(
        summary=completion.text, sources=to_sources(used), model=completion.model, cached=completion.cached
    )


async def generate(svc: Services, prompt: str) -> Completion:
    try:
        return await svc.llm.generate(prompt)
    except LLMError as e:
        raise HTTPException(status_code=503, detail=f"llm unavailable: {e}") from e


def embed_text(c: Chunk) -> str:
    return f"{c.kind} {c.label}\n{c.qualified_name}\n{c.text}"[:EMBED_TEXT_CHARS]


def to_sources(hits: list[Hit]) -> list[Source]:
    return [
        Source(
            ref=i,
            path=h.chunk.path,
            label=h.chunk.label,
            qualified_name=h.chunk.qualified_name,
            start_line=h.chunk.start_line,
            end_line=h.chunk.end_line,
            score=h.score,
        )
        for i, h in enumerate(hits, 1)
    ]
