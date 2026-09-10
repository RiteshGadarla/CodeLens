import numpy as np

from app.rag.embeddings import normalize
from app.rag.store import ProjectIndex
from tests.fakes import chunk


def test_upsert_search_and_reload(tmp_path):
    idx = ProjectIndex(tmp_path / "1", "e")
    vecs = normalize(np.eye(3))
    idx.upsert([chunk("a", "A#a()", "a"), chunk("b", "B#b()", "b"), chunk("c", "C#c()", "c", path="C.java")], vecs)

    assert idx.search(vecs[1], 1)[0][0].id == "b"
    idx.save()

    again = ProjectIndex(tmp_path / "1", "e")
    assert len(again) == 3
    assert again.search(vecs[2], 1)[0][0].id == "c"


def test_upsert_replaces_same_id(tmp_path):
    idx = ProjectIndex(tmp_path / "1", "e")
    idx.upsert([chunk("a", "A#a()", "old")], normalize(np.eye(2)[:1]))
    idx.upsert([chunk("a", "A#a()", "new")], normalize(np.eye(2)[1:]))
    assert len(idx) == 1
    assert idx.chunks[0].text == "new"
    assert idx.vectors.shape == (1, 2)


def test_remove_by_predicate(tmp_path):
    idx = ProjectIndex(tmp_path / "1", "e")
    idx.upsert([chunk("a", "A#a()", "a"), chunk("c", "C#c()", "c", path="C.java")], normalize(np.eye(2)))
    assert idx.remove(lambda c: c.path == "C.java") == 1
    assert [c.id for c in idx.chunks] == ["a"]
    assert idx.remove(lambda c: True) == 1
    assert idx.search(np.ones(2), 5) == []


def test_other_embedder_ignores_saved_vectors(tmp_path):
    idx = ProjectIndex(tmp_path / "1", "e")
    idx.upsert([chunk("a", "A#a()", "a")], normalize(np.eye(1)))
    idx.save()
    assert len(ProjectIndex(tmp_path / "1", "other")) == 0
