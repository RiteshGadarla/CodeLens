import asyncio

from app.rag.retriever import retrieve, terms
from app.rag.store import ProjectIndex
from tests.fakes import FakeEmbedder, chunk


def build(tmp_path):
    emb = FakeEmbedder()
    idx = ProjectIndex(tmp_path / "1", emb.name)
    chunks = [
        chunk("1", "OrderService#place(Order)", "void place(Order order) { repo.save(order); }"),
        chunk("2", "UserService#find(Long)", "User find(Long id) { return repo.findById(id); }"),
        chunk("3", "InvoiceMapper#map(Invoice)", "InvoiceDto map(Invoice invoice) { return new InvoiceDto(); }"),
    ]
    idx.upsert(chunks, asyncio.run(emb.embed_documents([c.text for c in chunks])))
    return idx, emb


def test_terms_split_camel_case():
    assert {"userservice", "user", "service", "findbyid"} <= terms("UserService findById")
    assert "the" not in terms("what does the thing do")


def test_class_named_in_question_ranks_first(tmp_path):
    idx, emb = build(tmp_path)
    hits = asyncio.run(retrieve(idx, emb, "How does UserService work?", k=1))
    assert hits[0].chunk.id == "2"


def test_pinned_names_win(tmp_path):
    idx, emb = build(tmp_path)
    hits = asyncio.run(retrieve(idx, emb, "place an order", k=2, pinned={"com.acme.InvoiceMapper#map(Invoice)"}))
    assert hits[0].chunk.id == "3"


def test_empty_index(tmp_path):
    emb = FakeEmbedder()
    assert asyncio.run(retrieve(ProjectIndex(tmp_path / "x", emb.name), emb, "anything")) == []
