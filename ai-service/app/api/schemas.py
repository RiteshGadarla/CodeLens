from typing import Any

from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel


class CamelModel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


class ChunkIn(CamelModel):
    id: str
    path: str
    kind: str
    label: str
    qualified_name: str
    start_line: int
    end_line: int
    text: str


class IndexRequest(CamelModel):
    chunks: list[ChunkIn] = []
    removed_paths: list[str] = []
    reset: bool = False


class IndexResponse(CamelModel):
    added: int
    skipped: int
    removed: int
    total: int


class IndexStats(CamelModel):
    project_id: int
    chunks: int
    files: int
    embedder: str


class Source(CamelModel):
    ref: int
    path: str
    label: str
    qualified_name: str
    start_line: int
    end_line: int
    score: float


class AskRequest(CamelModel):
    question: str = Field(min_length=3, max_length=2000)
    facts: dict[str, Any] = {}
    focus: list[str] = []
    top_k: int = Field(8, ge=1, le=20)


class AskResponse(CamelModel):
    answer: str
    sources: list[Source]
    model: str
    cached: bool
    generated_by: str = "llm"


class ReportRequest(CamelModel):
    facts: dict[str, Any]
    focus: list[str] = []
    top_k: int = Field(6, ge=1, le=20)


class ReportResponse(CamelModel):
    summary: str
    sources: list[Source]
    model: str
    cached: bool
    generated_by: str = "llm"
