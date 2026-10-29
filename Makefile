# Thin wrappers around mise, Nx, Gradle and Compose.
SHELL := /bin/bash
.DEFAULT_GOAL := help
ENV ?= dev
IMAGE_TAG ?= dev
SERVICE ?=
COMPOSE := docker compose --env-file .env -f deploy/docker/compose/compose.yaml

.PHONY: help configure bootstrap install playwright-install infra-up infra-down dev dev-api dev-web up down reset-data reset-api-data nuke logs ps restart build build-images clean check-contract bundle-contract generate-api modulith-check seed-dev delete-user wipe-users test-user-maintenance test test-web test-api typecheck lint format e2e k8s-dry-run
help: ## Show commands
	@awk 'BEGIN {FS = ":.*##"} /^[a-zA-Z0-9_-]+:.*##/ {printf "  %-20s %s\n", $$1, $$2}' $(MAKEFILE_LIST)
.PHONY: todos
.PHONY: keycloak-theme
keycloak-theme: ## Apply local Keycloak theme, sign-up, password reset and Mailpit delivery
	$(COMPOSE) up -d --no-deps --wait --wait-timeout 300 keycloak mailpit
	node tools/apply-keycloak-theme.mjs
ifeq ($(OS),Windows_NT)
todos: SHELL := cmd.exe
todos: .SHELLFLAGS := /c
endif
todos: ## List TODOs grouped by file, with line numbers and a total
	@node tools/list-todos.mjs
configure: ## Create .env with random local credentials, preserving an existing file
	node tools/configure.mjs
bootstrap: install playwright-install ## Install dependencies and Chromium
install: ## Install locked Node dependencies
	pnpm install --frozen-lockfile
playwright-install: ## Install Chromium for E2E tests
	pnpm exec playwright install --with-deps chromium
infra-up: ## Start development infrastructure only
	$(COMPOSE) up -d --wait --wait-timeout 300 postgres valkey keycloak mailpit prometheus grafana loki alloy tempo
	node tools/apply-keycloak-theme.mjs
infra-down: ## Stop development infrastructure, keeping volumes
	$(COMPOSE) down
dev: ## Start infrastructure and both host apps with HMR; stop with Ctrl+C
	$(COMPOSE) stop web api
	$(MAKE) infra-up
	pnpm nx run-many -t serve --projects=web,api --parallel=2
dev-api: ## Run the API using credentials from .env
	node tools/run-api.mjs
dev-web: ## Run Vite with HMR on localhost:5173
	pnpm nx serve web
up: ## Build and start the full local stack
	$(COMPOSE) up --build -d --wait --wait-timeout 300
	node tools/apply-keycloak-theme.mjs
down: ## Stop the stack and preserve local data
	$(COMPOSE) down
reset-data: ## Explicitly delete this stack's local containers and data volumes
	$(COMPOSE) down --volumes
reset-api-data: ## Recreate only the development application schema; stop any host API first
	$(COMPOSE) stop api
	$(COMPOSE) exec -T postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$$POSTGRES_USER" -d "$$POSTGRES_DB" -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public"'
nuke: ## Delete all local The Parlor state and rebuild/reseed; preview with DRY_RUN=1
	node tools/nuke.mjs $(if $(filter 1,$(DRY_RUN)),--dry-run,)
logs: ## Tail logs; optionally SERVICE=api|web|keycloak|...
	$(COMPOSE) logs -f --tail=100 $(SERVICE)
ps: ## Show stack status
	$(COMPOSE) ps
restart: ## Restart containers without rebuilding; optionally SERVICE=api|web|...
	$(COMPOSE) restart $(SERVICE)
build: ## Build web and API artifacts through Nx
	pnpm nx run-many -t build --projects=web,api
clean: ## Clear Nx caches and stop its daemon; preserve application data
	pnpm nx reset
build-images: ## Build application images with IMAGE_TAG=dev by default
	IMAGE_TAG=$(IMAGE_TAG) $(COMPOSE) build api web
check-contract: ## Lint and validate the split OpenAPI contract
	pnpm check:contract
bundle-contract: ## Bundle the OpenAPI contract into dist/openapi/
	pnpm bundle:contract
generate-api: ## Lint/validate the OpenAPI contract and regenerate Java/TypeScript bindings
	pnpm generate:api
modulith-check: ## Verify module dependencies and generate module documentation (no Docker needed)
	pnpm nx run api:test --args="test --tests '*ModularityTests' generateModuleDocumentation"
seed-dev: ## Sync local BFF client/roles and staff user from .env, then reset and reseed branches, catalog, editions, stock and tables
	node tools/seed-dev.mjs
delete-user wipe-users: export THEPARLOR_DELETE_USERNAME = $(USERNAME)
delete-user wipe-users: export THEPARLOR_DELETE_USER_ID = $(USER_ID)
delete-user wipe-users: export THEPARLOR_DELETE_CONFIRM = $(CONFIRM)
delete-user wipe-users: export THEPARLOR_DELETE_DRY_RUN = $(DRY_RUN)
delete-user: ## Delete a local user and linked data; USERNAME=... or USER_ID=...; DRY_RUN=1 or CONFIRM=1
	node tools/manage-users.mjs delete-user
wipe-users: ## Delete all local users and linked data; DRY_RUN=1 or CONFIRM=1
	node tools/manage-users.mjs wipe-users
test-user-maintenance: ## Test user deletion against isolated PostgreSQL, Valkey and Keycloak containers
	node tools/test-api.mjs maintenanceTest
test: test-web test-api ## Run frontend and backend tests
test-web: ## Run all frontend unit tests once
	pnpm nx run-many -t test --exclude=api
test-api: ## Run backend unit and Testcontainers integration tests
	node tools/test-api.mjs
typecheck: ## Check handwritten TypeScript and Vue types
	pnpm nx run-many -t typecheck
lint: ## Run frontend ESLint and Java Spotless checks
	pnpm nx run-many -t lint
format: ## Format source and configuration files
	pnpm exec prettier --write .
	cd apps/api && ./gradlew spotlessApply
e2e: ## Run Chromium login/API/logout tests against the live stack
	node tools/run-e2e.mjs
k8s-dry-run: ## Render manifests without contacting a cluster (ENV=dev|staging|prod)
	kubectl kustomize deploy/k8s/overlays/$(ENV)
