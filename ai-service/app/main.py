import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.api.routes import router
from app.config import get_settings
from app.services import Services

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")


def create_app(services: Services | None = None) -> FastAPI:
    @asynccontextmanager
    async def lifespan(app: FastAPI):
        app.state.services = services or Services.from_settings(get_settings())
        yield
        await app.state.services.close()

    app = FastAPI(title="CodeLens AI", version="0.1.0", lifespan=lifespan)
    app.include_router(router)
    return app


app = create_app()
