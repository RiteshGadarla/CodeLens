from typing import Protocol

import httpx

from app.config import Settings
from app.llm.cache import DiskCache
from app.llm.gemini import Completion, GeminiClient
from app.llm.ratelimit import RateLimiter
from app.rag.embeddings import Embedder, GeminiEmbedder, LocalEmbedder
from app.rag.store import VectorStore


class LLM(Protocol):
    model: str

    async def generate(self, prompt: str) -> Completion: ...


class Services:
    def __init__(
        self,
        settings: Settings,
        store: VectorStore,
        embedder: Embedder,
        llm: LLM,
        http: httpx.AsyncClient | None = None,
    ):
        self.settings = settings
        self.store = store
        self.embedder = embedder
        self.llm = llm
        self._http = http

    @classmethod
    def from_settings(cls, s: Settings) -> "Services":
        http = httpx.AsyncClient()
        if s.embed_provider == "gemini":
            embedder: Embedder = GeminiEmbedder(s.gemini_api_key, s.gemini_embed_model, RateLimiter(s.embed_rpm), http)
        else:
            embedder = LocalEmbedder(s.local_embed_model, s.data_dir / "models")
        llm = GeminiClient(
            api_key=s.gemini_api_key,
            model=s.gemini_model,
            fallback_model=s.gemini_fallback_model,
            limiter=RateLimiter(s.llm_rpm),
            cache=DiskCache(s.data_dir / "cache"),
            http=http,
            max_output_tokens=s.max_output_tokens,
            timeout=s.llm_timeout,
        )
        return cls(s, VectorStore(s.data_dir / "index", embedder.name), embedder, llm, http)

    async def close(self) -> None:
        if self._http:
            await self._http.aclose()
