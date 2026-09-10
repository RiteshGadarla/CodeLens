import hashlib

import numpy as np

from app.llm.gemini import Completion
from app.rag.embeddings import normalize
from app.rag.store import Chunk


class FakeEmbedder:
    """Hashed bag-of-words; deterministic, no model download."""

    name = "fake"
    dim = 64

    def _vec(self, text: str) -> np.ndarray:
        v = np.zeros(self.dim, dtype=np.float32)
        for w in text.lower().replace("(", " ").replace(")", " ").replace("#", " ").split():
            v[int(hashlib.md5(w.encode()).hexdigest(), 16) % self.dim] += 1
        return v

    async def embed_documents(self, texts):
        if not texts:
            return np.zeros((0, self.dim), dtype=np.float32)
        return normalize([self._vec(t) for t in texts])

    async def embed_query(self, text):
        return normalize(self._vec(text))[0]


class FakeLLM:
    model = "fake-model"

    def __init__(self):
        self.prompts: list[str] = []

    async def generate(self, prompt: str) -> Completion:
        self.prompts.append(prompt)
        return Completion(text="stub answer [1]", model=self.model, cached=False)


def chunk(id: str, label: str, text: str, path: str = "src/A.java") -> Chunk:
    return Chunk(
        id=id, path=path, kind="METHOD", label=label, qualified_name=f"com.acme.{label}",
        start_line=1, end_line=3, text=text,
    )
