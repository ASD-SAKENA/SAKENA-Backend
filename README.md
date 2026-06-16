# Sakena — Backend

Production-grade backend service built with **Kotlin + Spring Boot**, following
**Domain-Driven Design (DDD)** and **Clean Architecture**. This repository is a
clean, opinionated template: it ships a small **Task** CRUD module that
demonstrates the layering, conventions and tooling the team is expected to follow.

---

## Tech Stack

| Concern            | Choice                                  |
|--------------------|-----------------------------------------|
| Language           | Kotlin 2.1                              |
| Framework          | Spring Boot 3.4                         |
| Runtime            | Java 21 (LTS)                           |
| Build tool         | Gradle (Kotlin DSL) via the wrapper     |
| Database           | PostgreSQL 16                           |
| Migrations         | Flyway                                  |
| Persistence        | Spring Data JPA / Hibernate             |
| API docs           | springdoc-openapi (Swagger UI)          |
| Observability      | Spring Boot Actuator                    |
| Testing            | JUnit 5, MockK, Testcontainers          |
| Packaging / dev    | Docker & Docker Compose                 |

> **Why Java 21?** It is the current LTS and is fully supported by Spring Boot.
> The exact JDK is pinned by the Gradle *toolchain* (`build.gradle.kts`), so it
> does not matter which `java` you have on your `PATH` — Gradle uses (and, if
> needed, downloads) JDK 21 for compilation and tests.

---

## Architecture

We combine **Clean Architecture** (dependency rule pointing inward) with **DDD**
tactical patterns (aggregates, value objects, repositories as ports). Code is
organized **by bounded context first, then by layer**.

```
com.sakena
├── SakenaApplication.kt          # Spring Boot entrypoint
│
├── shared/                       # Shared kernel — cross-context building blocks
│   ├── domain/                   #   framework-free base types (DomainException…)
│   ├── web/                      #   GlobalExceptionHandler, ApiError
│   └── config/                   #   OpenApiConfig, etc.
│
└── task/                         # ← a BOUNDED CONTEXT (copy this shape for new ones)
    ├── domain/                   # 1. DOMAIN     — pure Kotlin, no framework
    │   ├── model/                #      aggregates, entities, value objects
    │   ├── TaskRepository.kt     #      repository PORT (interface)
    │   └── TaskNotFoundException.kt
    ├── application/              # 2. APPLICATION — use cases / orchestration
    │   ├── TaskService.kt        #      transaction boundaries, no business rules
    │   └── command/              #      input models (commands)
    └── infrastructure/           # 3. INFRASTRUCTURE — adapters to the outside world
        ├── persistence/          #      JPA entity, Spring Data repo, port adapter
        └── web/                  #      REST controller + request/response DTOs
```

### The dependency rule

```
infrastructure ──▶ application ──▶ domain
        (web, persistence)              (knows nothing about the layers above)
```

- **Domain** has **zero** Spring/JPA imports. It owns the business rules and
  invariants (see `Task.kt`). It defines *ports* (e.g. `TaskRepository`).
- **Application** orchestrates use cases and owns transactions. It depends only
  on domain ports — never on infrastructure.
- **Infrastructure** implements the ports (e.g. `TaskRepositoryAdapter`) and
  exposes delivery mechanisms (REST). It maps between the domain model and
  framework models (JPA entities, DTOs) so neither leaks into the other.

This is what lets us refactor aggressively and, later, swap delivery/persistence
(or move to Kubernetes) without touching the core.

---

## Prerequisites

You only need two things installed:

1. **Docker** (with Docker Compose) — for PostgreSQL and for running the full stack.
2. **A JDK 21** on your machine — to run Gradle. (The build toolchain pins 21
   regardless; if you don't have it, Gradle can provision it.)

You do **not** need to install Gradle, Kotlin, or PostgreSQL — the Gradle wrapper
(`./gradlew`) and Docker handle those.

> macOS: `brew install --cask temurin@21` (or use the JDK you already have).

---

## Quick Start

Clone, then copy the env file:

```bash
cp .env.example .env
```

### Option A — Recommended for development (app from IDE/CLI, DB in Docker)

```bash
make db-up        # start PostgreSQL in Docker
make run          # run the app with ./gradlew bootRun
```

### Option B — Full stack in Docker (closest to production)

```bash
make up           # build the image and start app + db
make logs         # follow the app logs
make down         # stop everything
```

Either way, once it's up:

- API base URL: <http://localhost:8080/api/v1/tasks>
- Swagger UI: <http://localhost:8080/swagger-ui.html>
- Health check: <http://localhost:8080/actuator/health>

> Don't have `make`? Every target is a thin wrapper — run the underlying command
> shown in the [Makefile](Makefile) directly (e.g. `./gradlew bootRun`).

---

## Configuration

All configuration is driven by environment variables (with sane defaults in
[`application.yml`](src/main/resources/application.yml)). For local development
they come from `.env`.

| Variable        | Default                                        | Description                  |
|-----------------|------------------------------------------------|------------------------------|
| `DB_URL`        | `jdbc:postgresql://localhost:5432/sakena`      | JDBC connection URL          |
| `DB_USERNAME`   | `sakena`                                        | Database user                |
| `DB_PASSWORD`   | `sakena`                                        | Database password            |
| `DB_NAME`       | `sakena`                                        | Database name (compose)      |
| `DB_PORT`       | `5432`                                          | Host port for Postgres       |
| `SERVER_PORT`   | `8080`                                          | HTTP port                    |
| `LOG_LEVEL_APP` | `DEBUG`                                          | Log level for `com.sakena`   |

Secrets live only in `.env` (git-ignored). Never commit real credentials.

---

## API

The shipped `Task` module exposes a standard CRUD API:

| Method   | Path                          | Description                |
|----------|-------------------------------|----------------------------|
| `GET`    | `/api/v1/tasks`               | List all tasks             |
| `GET`    | `/api/v1/tasks/{id}`          | Get a task by id           |
| `POST`   | `/api/v1/tasks`               | Create a task              |
| `PUT`    | `/api/v1/tasks/{id}`          | Update title & description |
| `PATCH`  | `/api/v1/tasks/{id}/status`   | Change status              |
| `DELETE` | `/api/v1/tasks/{id}`          | Delete a task              |

Example:

```bash
curl -X POST http://localhost:8080/api/v1/tasks \
  -H 'Content-Type: application/json' \
  -d '{"title": "Write the README", "description": "for the team"}'
```

Errors use a uniform shape (see `ApiError`):

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Request validation failed",
  "path": "/api/v1/tasks",
  "timestamp": "2026-06-16T12:00:00Z",
  "fieldErrors": [{ "field": "title", "message": "title must not be blank" }]
}
```

---

## Database & Migrations

Schema is managed by **Flyway**. Migration scripts live in
[`src/main/resources/db/migration`](src/main/resources/db/migration) and are
applied automatically on startup. Hibernate runs in `ddl-auto: validate` mode —
it never changes the schema, it only checks the entities match it.

To add a change, create a new file following the naming convention:

```
V2__add_due_date_to_tasks.sql
V3__...
```

Never edit a migration that has already been applied/merged — add a new one.

---

## Testing

```bash
make test        # or: ./gradlew test
```

The testing strategy mirrors the architecture:

- **Domain unit tests** (`TaskTest`) — pure, fast, no Spring context.
- **Application unit tests** (`TaskServiceTest`) — use cases with **MockK**.
- **Integration tests** (`*IntegrationTest`) — boot the full app against a real
  PostgreSQL using **Testcontainers**, exercising HTTP → service → DB →
  migrations end to end.

> Integration tests require **Docker to be running** (Testcontainers starts a
> throwaway Postgres container automatically — no manual setup).

---

## Troubleshooting

### `Cannot find a Java installation … matching languageVersion=21`

The build's Gradle toolchain requires **JDK 21**. If your default `java` is a
different version, Gradle needs to *locate* a JDK 21 — and a brew-installed
`openjdk@21` is keg-only, so neither `/usr/libexec/java_home` nor Gradle's
auto-detection finds it.

The **`make` targets handle this for you** — they discover a real JDK 21
(including the brew path) and hand it to Gradle, so just use `make run` /
`make test` / `make build`.

If you call `./gradlew` directly, point it at a JDK 21 yourself:

```bash
# macOS + Homebrew openjdk@21:
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew build
```

To make your JDK 21 visible system-wide (so IntelliJ and `java_home` see it too):

```bash
sudo ln -sfn /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk \
  /Library/Java/JavaVirtualMachines/openjdk-21.jdk
```

### Docker can't pull images (Docker Hub blocked / timeouts)

If `docker pull` or the integration tests fail with
`registry-1.docker.io … context deadline exceeded`, configure a registry mirror.
Add (or merge) this into `~/.docker/daemon.json` and restart Docker:

```json
{
  "registry-mirrors": ["https://docker.arvancloud.ir"]
}
```

Then verify with `docker info | grep -A1 "Registry Mirrors"`. Testcontainers
pulls through the Docker daemon, so the mirror applies automatically — no code
changes needed.

---

## Adding a New Bounded Context

1. Create a top-level package `com.sakena.<context>` mirroring `task/`:
   `domain/`, `application/`, `infrastructure/{persistence,web}`.
2. Model the aggregate and its invariants in `domain/model` (pure Kotlin).
3. Declare repository **ports** as interfaces in `domain`.
4. Write use cases in `application` (one `@Service`, transactional).
5. Implement the port with a JPA adapter in `infrastructure/persistence`, and
   add a Flyway migration for its tables.
6. Expose it via a controller + DTOs in `infrastructure/web`.

Keep the dependency rule intact: **domain depends on nothing**.

---

## Roadmap

- [ ] CI/CD pipeline (build, test, image publish) — *planned, not yet set up*
- [ ] Kubernetes manifests / Helm chart — Actuator probes are already enabled
- [ ] AuthN/AuthZ (Spring Security)
- [ ] API versioning & pagination conventions

---

## Project Conventions

- Code, comments, commit messages and docs are **in English**.
- One aggregate = one transaction = one consistency boundary.
- Controllers stay thin; business rules live in the domain.
- Map at the boundaries: DTO ⇄ command ⇄ domain ⇄ JPA entity.
- Formatting is enforced by [`.editorconfig`](.editorconfig).

See [CLAUDE.md](CLAUDE.md) for AI-assistant guidance and a condensed map of the codebase.
