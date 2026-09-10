import asyncio
import time


class RateLimiter:
    """Spaces calls to at most `rpm` per minute."""

    def __init__(self, rpm: int):
        self._interval = 60.0 / max(rpm, 1)
        self._next = 0.0
        self._lock = asyncio.Lock()

    async def acquire(self) -> None:
        async with self._lock:
            now = time.monotonic()
            wait = self._next - now
            if wait > 0:
                await asyncio.sleep(wait)
                now = time.monotonic()
            self._next = max(now, self._next) + self._interval
