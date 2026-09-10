import asyncio
import logging
from dataclasses import dataclass, field

import httpx

from app.llm.cache import DiskCache
from app.llm.ratelimit import RateLimiter

log = logging.getLogger(__name__)

BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
RETRYABLE = {429, 500, 502, 503, 504}


class LLMError(RuntimeError):
    pass


@dataclass
class Completion:
    text: str
    model: str
    cached: bool
    usage: dict = field(default_factory=dict)


class GeminiClient:
    """generateContent over REST: rate limit, disk cache, retry, fallback model."""

    def __init__(
        self,
        api_key: str,
        model: str,
        fallback_model: str,
        limiter: RateLimiter,
        cache: DiskCache,
        http: httpx.AsyncClient,
        max_output_tokens: int = 2048,
        timeout: float = 120.0,
        retries: int = 2,
        backoff: float = 2.0,
    ):
        self.api_key = api_key
        self.model = model
        self.fallback_model = fallback_model
        self._limiter = limiter
        self._cache = cache
        self._http = http
        self._max_output_tokens = max_output_tokens
        self._timeout = timeout
        self._retries = retries
        self._backoff = backoff

    async def generate(self, prompt: str, temperature: float = 0.2) -> Completion:
        if not self.api_key:
            raise LLMError("GEMINI_API_KEY is not set")

        key = DiskCache.key(self.model, self.fallback_model, str(temperature), prompt)
        if hit := self._cache.get(key):
            return Completion(hit["text"], hit["model"], True, hit.get("usage", {}))

        error: LLMError | None = None
        for model in dict.fromkeys(m for m in (self.model, self.fallback_model) if m):
            try:
                text, usage = await self._call(model, prompt, temperature)
            except LLMError as e:
                log.warning("model %s failed: %s", model, e)
                error = e
                continue
            self._cache.put(key, {"text": text, "model": model, "usage": usage})
            return Completion(text, model, False, usage)
        raise error or LLMError("no model configured")

    async def _call(self, model: str, prompt: str, temperature: float) -> tuple[str, dict]:
        # gemma has no system role, so instructions live in the prompt
        body = {
            "contents": [{"role": "user", "parts": [{"text": prompt}]}],
            "generationConfig": {"temperature": temperature, "maxOutputTokens": self._max_output_tokens},
        }
        delay = self._backoff
        error = LLMError("not called")
        for attempt in range(self._retries + 1):
            await self._limiter.acquire()
            try:
                r = await self._http.post(
                    f"{BASE_URL}/{model}:generateContent",
                    json=body,
                    headers={"x-goog-api-key": self.api_key},
                    timeout=self._timeout,
                )
            except httpx.HTTPError as e:
                error = LLMError(f"network: {e}")
            else:
                if r.status_code == 200:
                    return self._parse(r.json())
                error = LLMError(f"{r.status_code}: {r.text[:300]}")
                if r.status_code not in RETRYABLE:
                    raise error
            if attempt < self._retries:
                await asyncio.sleep(delay)
                delay *= 2
        raise error

    @staticmethod
    def _parse(data: dict) -> tuple[str, dict]:
        candidates = data.get("candidates") or []
        if not candidates:
            raise LLMError(f"no candidates: {data.get('promptFeedback')}")
        parts = candidates[0].get("content", {}).get("parts", [])
        text = "".join(p.get("text", "") for p in parts if not p.get("thought")).strip()
        if not text:
            raise LLMError(f"empty response: {candidates[0].get('finishReason')}")
        return text, data.get("usageMetadata", {})
