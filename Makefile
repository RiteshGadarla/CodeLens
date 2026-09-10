SHELL := /bin/bash
JAVA_HOME ?= /usr/lib/jvm/java-21-openjdk-amd64
export JAVA_HOME

COMPOSE     := docker compose
COMPOSE_DEV := docker compose -f docker-compose.dev.yml
VENV        := ai-service/.venv

.DEFAULT_GOAL := help
.PHONY: help env install infra-up infra-down infra-reset infra-logs \
        dev dev-backend dev-ai dev-frontend \
        test test-backend test-ai test-frontend \
        prod-build prod-up prod-down prod-logs ps clean

help: ## list targets
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN{FS=":.*?## "}{printf "  \033[36m%-14s\033[0m %s\n",$$1,$$2}'

# setup
env: ## create .env files from examples
	@for d in backend ai-service frontend; do [ -f $$d/.env ] || cp $$d/.env.example $$d/.env; done

install: env ## install all deps
	cd backend && ./mvnw -q -DskipTests dependency:go-offline
	python3 -m venv $(VENV) && $(VENV)/bin/pip install -q -r ai-service/requirements-dev.txt
	cd frontend && npm install

# infra
infra-up: env ## start postgres + redis
	$(COMPOSE_DEV) up -d --wait

infra-down: ## stop infra
	$(COMPOSE_DEV) down

infra-reset: ## stop infra and wipe data
	$(COMPOSE_DEV) down -v

infra-logs: ## tail infra logs
	$(COMPOSE_DEV) logs -f

# dev
dev: infra-up ## infra + all apps
	@trap 'kill 0' INT TERM; \
	$(MAKE) --no-print-directory dev-ai & \
	$(MAKE) --no-print-directory dev-backend & \
	$(MAKE) --no-print-directory dev-frontend & \
	wait

dev-backend: ## run backend
	cd backend && ./mvnw spring-boot:run

dev-ai: ## run ai-service
	cd ai-service && .venv/bin/uvicorn app.main:app --reload --port 8000

dev-frontend: ## run frontend
	cd frontend && npm run dev

# test
test: test-backend test-ai test-frontend ## run all tests

test-backend: ## backend tests
	cd backend && ./mvnw -q test

test-ai: ## ai-service tests
	cd ai-service && .venv/bin/pytest -q

test-frontend: ## type-check + build
	cd frontend && npm run build

# prod
prod-build: env ## build images
	$(COMPOSE) build

prod-up: env ## run full stack
	$(COMPOSE) up -d --build --wait

prod-down: ## stop full stack
	$(COMPOSE) down

prod-logs: ## tail stack logs
	$(COMPOSE) logs -f

ps: ## stack status
	$(COMPOSE) ps

clean: ## remove build outputs
	rm -rf backend/target frontend/dist ai-service/.pytest_cache
