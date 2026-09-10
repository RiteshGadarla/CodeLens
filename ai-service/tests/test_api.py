from app.llm.gemini import LLMError

CHUNKS = [
    {
        "id": "1", "path": "src/UserService.java", "kind": "METHOD", "label": "UserService#find(Long)",
        "qualifiedName": "com.acme.UserService#find(Long)", "startLine": 10, "endLine": 12,
        "text": "User find(Long id);",
    },
    {
        "id": "2", "path": "src/UserController.java", "kind": "METHOD", "label": "UserController#get(Long)",
        "qualifiedName": "com.acme.UserController#get(Long)", "startLine": 20, "endLine": 24,
        "text": "@GetMapping User get(Long id) { return service.find(id); }",
    },
]


def test_health(client):
    body = client.get("/health").json()
    assert body["status"] == "ok" and body["model"] == "fake-model" and body["llmConfigured"]


def test_index_is_incremental(client):
    r = client.post("/projects/1/index", json={"chunks": CHUNKS})
    assert r.json() == {"added": 2, "skipped": 0, "removed": 0, "total": 2}

    r = client.post("/projects/1/index", json={"chunks": CHUNKS})
    assert r.json() == {"added": 0, "skipped": 2, "removed": 0, "total": 2}

    changed = [dict(CHUNKS[1], text="User get(Long id) { return null; }")]
    r = client.post("/projects/1/index", json={"chunks": changed, "removedPaths": ["src/UserService.java"]})
    assert r.json() == {"added": 1, "skipped": 0, "removed": 1, "total": 1}

    stats = client.get("/projects/1/index").json()
    assert stats["chunks"] == 1 and stats["files"] == 1


def test_ask_grounds_prompt_in_facts_and_sources(client, services):
    client.post("/projects/7/index", json={"chunks": CHUNKS})
    r = client.post("/projects/7/ask", json={
        "question": "What depends on UserService?",
        "facts": {"dependents": ["UserController#get(Long)"]},
    })
    assert r.status_code == 200
    body = r.json()
    assert body["answer"] == "stub answer [1]"
    assert body["generatedBy"] == "llm"
    assert body["sources"][0]["label"] == "UserService#find(Long)"

    prompt = services.llm.prompts[-1]
    assert '"dependents":["UserController#get(Long)"]' in prompt
    assert "User find(Long id);" in prompt


def test_report_uses_impact_facts(client, services):
    client.post("/projects/7/index", json={"chunks": CHUNKS})
    facts = {"target": {"label": "UserService#find(Long)"}, "risk": {"score": 72.5, "level": "HIGH"}}
    r = client.post("/projects/7/report", json={"facts": facts, "focus": ["com.acme.UserController#get(Long)"]})
    assert r.status_code == 200
    assert r.json()["sources"][0]["qualifiedName"] == "com.acme.UserController#get(Long)"
    assert "HIGH, score 72.5/100" in services.llm.prompts[-1]


def test_llm_failure_is_503(client, services):
    async def boom(prompt):
        raise LLMError("quota")

    services.llm.generate = boom
    assert client.post("/projects/1/ask", json={"question": "anything?"}).status_code == 503


def test_validation(client):
    assert client.post("/projects/1/ask", json={"question": "x"}).status_code == 422


def test_delete_index(client):
    client.post("/projects/3/index", json={"chunks": CHUNKS})
    assert client.delete("/projects/3/index").status_code == 204
    assert client.get("/projects/3/index").json()["chunks"] == 0
