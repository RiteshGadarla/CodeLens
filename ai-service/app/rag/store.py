import hashlib
import json
import shutil
import threading
from collections.abc import Callable
from dataclasses import asdict, dataclass
from pathlib import Path

import numpy as np


@dataclass
class Chunk:
    id: str
    path: str
    kind: str
    label: str
    qualified_name: str
    start_line: int
    end_line: int
    text: str
    hash: str = ""

    def __post_init__(self):
        if not self.hash:
            self.hash = hashlib.sha256(f"{self.label}\n{self.text}".encode()).hexdigest()[:16]


class ProjectIndex:
    """Chunks + L2-normalised vectors for one project, persisted as json + npy."""

    def __init__(self, directory: Path, embedder_name: str):
        self.dir = directory
        self.embedder_name = embedder_name
        self.chunks: list[Chunk] = []
        self.vectors = np.zeros((0, 0), dtype=np.float32)
        self.lock = threading.RLock()
        self._load()

    def __len__(self) -> int:
        return len(self.chunks)

    def _load(self) -> None:
        meta, vec = self.dir / "chunks.json", self.dir / "vectors.npy"
        if not meta.exists() or not vec.exists():
            return
        data = json.loads(meta.read_text())
        # vectors from another embedder are not comparable
        if data.get("embedder") != self.embedder_name:
            return
        self.chunks = [Chunk(**c) for c in data["chunks"]]
        self.vectors = np.load(vec)

    def save(self) -> None:
        with self.lock:
            self.dir.mkdir(parents=True, exist_ok=True)
            np.save(self.dir / "vectors.tmp.npy", self.vectors)
            payload = {"embedder": self.embedder_name, "chunks": [asdict(c) for c in self.chunks]}
            (self.dir / "chunks.tmp.json").write_text(json.dumps(payload))
            (self.dir / "vectors.tmp.npy").replace(self.dir / "vectors.npy")
            (self.dir / "chunks.tmp.json").replace(self.dir / "chunks.json")

    def hashes(self) -> dict[str, str]:
        with self.lock:
            return {c.id: c.hash for c in self.chunks}

    def upsert(self, chunks: list[Chunk], vectors: np.ndarray) -> None:
        if not chunks:
            return
        with self.lock:
            ids = {c.id for c in chunks}
            self._drop(lambda c: c.id in ids)
            vectors = np.asarray(vectors, dtype=np.float32)
            self.vectors = vectors if self.vectors.size == 0 else np.vstack([self.vectors, vectors])
            self.chunks.extend(chunks)

    def remove(self, predicate: Callable[[Chunk], bool]) -> int:
        with self.lock:
            return self._drop(predicate)

    def reset(self) -> None:
        with self.lock:
            self.chunks = []
            self.vectors = np.zeros((0, 0), dtype=np.float32)

    def search(self, query: np.ndarray, k: int) -> list[tuple[Chunk, float]]:
        with self.lock:
            if not self.chunks or k <= 0:
                return []
            scores = self.vectors @ np.asarray(query, dtype=np.float32)
            k = min(k, len(self.chunks))
            top = np.argpartition(-scores, k - 1)[:k]
            top = top[np.argsort(-scores[top])]
            return [(self.chunks[i], float(scores[i])) for i in top]

    def _drop(self, predicate: Callable[[Chunk], bool]) -> int:
        keep = [i for i, c in enumerate(self.chunks) if not predicate(c)]
        removed = len(self.chunks) - len(keep)
        if removed:
            dim = self.vectors.shape[1] if self.vectors.ndim == 2 else 0
            self.chunks = [self.chunks[i] for i in keep]
            self.vectors = self.vectors[keep] if keep else np.zeros((0, dim), dtype=np.float32)
        return removed


class VectorStore:
    def __init__(self, root: Path, embedder_name: str):
        self.root = root
        self.embedder_name = embedder_name
        self._indexes: dict[int, ProjectIndex] = {}
        self._lock = threading.Lock()

    def get(self, project_id: int) -> ProjectIndex:
        with self._lock:
            if project_id not in self._indexes:
                self._indexes[project_id] = ProjectIndex(self.root / str(project_id), self.embedder_name)
            return self._indexes[project_id]

    def delete(self, project_id: int) -> None:
        with self._lock:
            self._indexes.pop(project_id, None)
            shutil.rmtree(self.root / str(project_id), ignore_errors=True)
