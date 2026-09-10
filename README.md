# CodeLens

AI-powered code intelligence and dependency risk analyzer for Java repositories.

Parses a repo with JavaParser, builds a dependency graph, computes coupling/complexity
metrics, scores change impact, and answers natural-language questions grounded in that
graph plus retrieved source (RAG over Gemini).

## Layout

```
backend/      Java 21 + Spring Boot analysis engine and REST API
ai-service/   Python FastAPI RAG service (Gemini)
frontend/     React + TypeScript + Tailwind + React Flow dashboard
docs/         API and design notes
```

## Run

```bash
cp .env.example .env      # put your GEMINI_API_KEY in it
docker compose up --build
```

Dashboard on :5173, API on :8080, AI service on :8000.

### Dev (infra in Docker, apps local)

```bash
docker compose -f docker-compose.dev.yml up -d
cd backend && ./mvnw spring-boot:run
cd ai-service && uvicorn app.main:app --reload
cd frontend && npm run dev
```
