# CodeLens

Code intelligence and change-impact analysis for Java repositories.

CodeLens parses a repository into a typed dependency graph, measures coupling and complexity,
predicts what breaks when a class or method changes, and answers questions about the code with
an LLM that is grounded in the graph and in retrieved source.

Static analysis is deterministic and does the heavy lifting; the LLM only explains facts it is given.
Every AI answer in the UI is marked as generated and shows the graph facts and sources it used.

## Features

- **Parsing** – classes, interfaces, enums, records, methods, fields, imports, inheritance,
  annotations, calls, object creation, Spring / JAX-RS endpoints, cyclomatic complexity
- **Dependency graph** – `CALLS`, `EXTENDS`, `IMPLEMENTS`, `OVERRIDES`, `CREATES`, `USES_TYPE`,
  `IMPORTS`, `ANNOTATED_BY`, `ROUTES_TO`; upstream/downstream traversal, shortest and bounded
  all-paths, Tarjan cycle detection, type-level roll-up
- **Change impact** – direct and transitive dependents (following interface dispatch), affected
  APIs, services, modules and tests, the paths that explain each hit, and a 0–100 risk score
- **Metrics** – fan-in/out, transitive dependents/dependencies, dependency depth, complexity,
  package and module coupling (Ca, Ce, instability, abstractness, distance from main sequence)
- **Incremental analysis** – file hashes pick changed files; only those are re-parsed, their
  dependents re-resolved, and the database patched in place
- **RAG assistant** – method and type chunks embedded locally, hybrid retrieval, Gemma answers and
  stored impact reports
- **Dashboard** – overview, interactive graph with path finder, entity pages with source,
  impact view, hotspots and coupling chart, assistant

## Architecture

```
frontend (React, Vite, React Flow)
   │  /api
backend (Java 21, Spring Boot) ── PostgreSQL (graph, metrics, runs, reports)
   │   parser → graph → metrics → impact      └─ Redis (query cache, keyed by graph version)
   │  REST
ai-service (FastAPI) ── local embeddings (fastembed) + vector index
                     └─ Gemini API: gemma-4-26b-a4b-it, fallback gemini-2.5-flash-lite
```

**Analysis pipeline** – scan → parse files in parallel (JavaParser, pass 1) → resolve symbols
across the project into entities and edges (pass 2) → bulk write → load graph → compute metrics →
index chunks for RAG.

**Risk score** – `100 × (0.30·dependents + 0.15·depth + 0.20·coupling + 0.20·complexity + 0.15·exposed)`,
each factor log-normalised against the project maximum. `HIGH ≥ 60`, `MEDIUM ≥ 30`.

## Layout

```
backend/      analysis engine and REST API
ai-service/   RAG service
frontend/     dashboard
Makefile      dev, test and docker shortcuts
```

## Setup

Requires Java 21, Node 20+, Python 3.12 and Docker.

```bash
make env        # creates backend/.env, ai-service/.env, frontend/.env from the examples
                # then put your GEMINI_API_KEY in ai-service/.env
make install
```

## Run

```bash
make dev        # postgres + redis in docker, backend :8090, ai-service :8000, dashboard :5173
make prod-up    # everything in docker: dashboard :5173, api :8090
make help       # all targets
```

API docs: http://localhost:8090/swagger-ui.html

## Test

```bash
make test       # backend (JUnit, Testcontainers), ai-service (pytest), frontend type-check + build
```

Backend integration tests start PostgreSQL and Redis with Testcontainers and use a fake AI
service, so they need Docker but no API key.

## Free-tier friendly

- embeddings run locally; indexing never calls the Gemini API
- client-side rate limit, disk cache of answers, retry with backoff, fallback model
- prompts are capped (`MAX_CONTEXT_CHARS`) and only built on explicit ask/report requests
