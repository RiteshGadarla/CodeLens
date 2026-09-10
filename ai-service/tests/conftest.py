import pytest
from fastapi.testclient import TestClient

from app.config import Settings
from app.main import create_app
from app.rag.store import VectorStore
from app.services import Services
from tests.fakes import FakeEmbedder, FakeLLM


@pytest.fixture
def services(tmp_path):
    settings = Settings(gemini_api_key="test", data_dir=tmp_path, max_context_chars=6000)
    embedder = FakeEmbedder()
    return Services(settings, VectorStore(tmp_path / "index", embedder.name), embedder, FakeLLM())


@pytest.fixture
def client(services):
    with TestClient(create_app(services)) as c:
        yield c
