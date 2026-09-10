import asyncio

import httpx
import pytest

from app.llm.cache import DiskCache
from app.llm.gemini import GeminiClient, LLMError
from app.llm.ratelimit import RateLimiter


def ok(text):
    return httpx.Response(200, json={
        "candidates": [{"content": {"parts": [{"text": "hmm", "thought": True}, {"text": text}]}, "finishReason": "STOP"}],
        "usageMetadata": {"totalTokenCount": 5},
    })


def client(tmp_path, handler, key="k"):
    return GeminiClient(
        api_key=key, model="gemma", fallback_model="flash", limiter=RateLimiter(60000),
        cache=DiskCache(tmp_path), http=httpx.AsyncClient(transport=httpx.MockTransport(handler)),
        retries=1, backoff=0,
    )


def test_filters_thoughts_and_caches(tmp_path):
    calls = []

    def handler(req):
        calls.append(req.url.path)
        return ok("Paris")

    c = client(tmp_path, handler)
    first = asyncio.run(c.generate("capital?"))
    second = asyncio.run(c.generate("capital?"))
    assert first.text == "Paris" and not first.cached
    assert second.cached and second.model == "gemma"
    assert len(calls) == 1


def test_retries_then_falls_back(tmp_path):
    calls = []

    def handler(req):
        calls.append(req.url.path)
        if "gemma" in req.url.path:
            return httpx.Response(500, json={"error": {"message": "Internal"}})
        return ok("fallback")

    r = asyncio.run(client(tmp_path, handler).generate("q"))
    assert r.model == "flash" and r.text == "fallback"
    assert sum("gemma" in p for p in calls) == 2


def test_client_errors_are_not_retried(tmp_path):
    calls = []

    def handler(req):
        calls.append(req.url.path)
        return httpx.Response(400, json={"error": {"message": "bad"}})

    with pytest.raises(LLMError):
        asyncio.run(client(tmp_path, handler).generate("q"))
    assert len(calls) == 2


def test_missing_key(tmp_path):
    with pytest.raises(LLMError):
        asyncio.run(client(tmp_path, lambda r: ok("x"), key="").generate("q"))
