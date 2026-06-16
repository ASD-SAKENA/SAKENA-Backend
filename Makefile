.DEFAULT_GOAL := help

# --- JDK 21 discovery -------------------------------------------------------
# The build's Gradle toolchain requires JDK 21. Your default `java` may be a
# different version, and a brew-installed `openjdk@21` is keg-only (invisible to
# Gradle's auto-detection), so we locate a *real* 21 here and hand it to Gradle.
# Override with `make run JAVA_HOME_21=/path/to/jdk21` if needed.
JAVA_HOME_21 ?= $(shell \
	for h in \
		"$$(/usr/libexec/java_home -v 21 2>/dev/null)" \
		/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
		/usr/lib/jvm/java-21-openjdk-amd64 \
		/usr/lib/jvm/java-21-openjdk; do \
		if [ -x "$$h/bin/java" ] && "$$h/bin/java" -version 2>&1 | grep -q 'version "21'; then \
			echo "$$h"; break; \
		fi; \
	done)

GRADLE = JAVA_HOME="$(JAVA_HOME_21)" ./gradlew

.PHONY: check-jdk
check-jdk:
	@if [ -z "$(JAVA_HOME_21)" ]; then \
		echo "ERROR: No JDK 21 found. Install one (e.g. 'brew install openjdk@21')"; \
		echo "       or pass it explicitly: make <target> JAVA_HOME_21=/path/to/jdk21"; \
		exit 1; \
	fi

.PHONY: help
help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | \
		awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-16s\033[0m %s\n", $$1, $$2}'

.PHONY: db-up
db-up: ## Start only the PostgreSQL database (for running the app from your IDE)
	docker compose up -d db

.PHONY: db-down
db-down: ## Stop the database
	docker compose stop db

.PHONY: run
run: check-jdk ## Run the application locally (needs the DB up: `make db-up`)
	$(GRADLE) bootRun

.PHONY: test
test: check-jdk ## Run all tests (integration tests need Docker running)
	$(GRADLE) test

.PHONY: build
build: check-jdk ## Build the executable jar
	$(GRADLE) clean bootJar

.PHONY: up
up: ## Build and start the full stack (app + db) in Docker
	docker compose up --build -d

.PHONY: down
down: ## Stop the full stack
	docker compose down

.PHONY: logs
logs: ## Tail application logs from Docker
	docker compose logs -f app

.PHONY: clean
clean: check-jdk ## Remove build artifacts
	$(GRADLE) clean
