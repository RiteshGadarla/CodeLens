# CodeLens

AI-powered code intelligence and dependency risk analyzer for Java repositories.

Parses a repo with JavaParser, builds a dependency graph, computes coupling/complexity
metrics, scores change impact, and answers natural-language questions grounded in that
graph plus retrieved source (RAG over Gemma/Gemini).

## Layout

```
backend/      Java 21 + Spring Boot analysis engine and REST API
ai-service/   Python FastAPI RAG service
frontend/     React + Vite + TypeScript + Tailwind + React Flow dashboard
docs/         API and design notes
```

## Config

Each service has its own env file:

```bash
cp backend/.env.example backend/.env
cp ai-service/.env.example ai-service/.env   # set GEMINI_API_KEY
cp frontend/.env.example frontend/.env
```

## Run

```bash
make prod-up        # full stack in docker
make help           # all targets
```

Dashboard on :5173, API on :8090 (Swagger at /swagger-ui.html), AI service on :8000.

### Dev (infra in Docker, apps local)

```bash
make install
make dev            # or: make infra-up, then dev-backend / dev-ai / dev-frontend
```
