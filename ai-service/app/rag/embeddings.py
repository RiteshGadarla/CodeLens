import asyncio
import threading
from pathlib import Path
from typing import Protocol

import httpx
import numpy as np

from app.llm.gemini import BASE_URL
from app.llm.ratelimit import RateLimiter


def normalize(v) -> np.ndarray:
    v = np.asarray(v, dtype=np.float32)
    if v.ndim == 1:
        v = v[None, :]
    return v / np.maximum(np.linalg.norm(v, axis=1, keepdims=True), 1e-12)


class Embedder(Protocol):
    name: str

    async def embed_documents(self, texts: list[str]) -> np.ndarray: ...

    async def embed_query(self, text: str) -> np.ndarray: ...


class LocalEmbedder:
    """fastembed (onnx); runs on cpu, no api calls."""

    def __init__(self, model: str, cache_dir: Path):
        self.name = f"local:{model}"
        self._model_name = model
        self._cache_dir = cache_dir
        self._model = None
        self._lock = threading.Lock()

    def _load(self):
        with self._lock:
            if self._model is None:
                from fastembed import TextEmbedding

                self._model = TextEmbedding(self._model_name, cache_dir=str(self._cache_dir))
            return self._model

    async def embed_documents(self, texts: list[str]) -> np.ndarray:
        if not texts:
            return np.zeros((0, 0), dtype=np.float32)
        return await asyncio.to_thread(lambda: normalize(list(self._load().embed(texts, batch_size=32))))

    async def embed_query(self, text: str) -> np.ndarray:
        return await asyncio.to_thread(lambda: normalize(list(self._load().query_embed(text)))[0])


class GeminiEmbedder:
    """batchEmbedContents; each batch is one request against the quota."""

    batch_size = 100
    max_chars = 6000

    def __init__(self, api_key: str, model: str, limiter: RateLimiter, http: httpx.AsyncClient, dim: int = 768):
        self.name = f"gemini:{model}:{dim}"
        self._api_key = api_key
        self._model = model
        self._limiter = limiter
        self._http = http
        self._dim = dim

    async def _embed(self, texts: list[str], task: str) -> np.ndarray:
        out = []
        for i in range(0, len(texts), self.batch_size):
            await self._limiter.acquire()
            requests = [
                {
                    "model": f"models/{self._model}",
                    "content": {"parts": [{"text": t[: self.max_chars]}]},
                    "taskType": task,
                    "outputDimensionality": self._dim,
                }
                for t in texts[i : i + self.batch_size]
            ]
            r = await self._http.post(
                f"{BASE_URL}/{self._model}:batchEmbedContents",
                json={"requests": requests},
                headers={"x-goog-api-key": self._api_key},
                timeout=60,
            )
            r.raise_for_status()
            out.extend(e["values"] for e in r.json()["embeddings"])
        return normalize(out)

    async def embed_documents(self, texts: list[str]) -> np.ndarray:
        if not texts:
            return np.zeros((0, self._dim), dtype=np.float32)
        return await self._embed(texts, "RETRIEVAL_DOCUMENT")

    async def embed_query(self, text: str) -> np.ndarray:
        return (await self._embed([text], "RETRIEVAL_QUERY"))[0]
