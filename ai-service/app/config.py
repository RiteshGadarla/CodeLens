from functools import lru_cache
from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    gemini_api_key: str = ""
    gemini_model: str = "gemma-4-26b-a4b-it"
    gemini_fallback_model: str = "gemini-2.5-flash-lite"
    gemini_embed_model: str = "gemini-embedding-001"

    # local = fastembed, no api quota
    embed_provider: str = "local"
    local_embed_model: str = "BAAI/bge-small-en-v1.5"

    # free-tier guards
    llm_rpm: int = 20
    embed_rpm: int = 60
    max_context_chars: int = 24000
    max_output_tokens: int = 8192
    llm_timeout: float = 120.0

    data_dir: Path = Path("./data")


@lru_cache
def get_settings() -> Settings:
    return Settings()
