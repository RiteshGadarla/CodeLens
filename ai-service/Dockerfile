FROM python:3.12-slim
WORKDIR /app
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    DATA_DIR=/app/data \
    MODELS_DIR=/app/models
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
RUN useradd --system --create-home --uid 1001 codelens \
    && mkdir -p /app/data /app/models \
    && chown codelens /app/data /app/models
USER codelens
# bake the embedding model so indexing works offline
RUN python -c "from fastembed import TextEmbedding; TextEmbedding('BAAI/bge-small-en-v1.5', cache_dir='/app/models')"
COPY --chown=codelens app app
EXPOSE 8000
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
