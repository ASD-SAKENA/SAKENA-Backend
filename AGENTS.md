# AGENTS.md

Guidance for AI assistants (and humans) working in this repository.

## What this is

Sakena backend — **Kotlin 2.1 + Spring Boot 3.4 on Java 21**, built with
**DDD + Clean Architecture**. A small `Task` CRUD module is the reference
implementation; new features copy its shape. Read [README.md](README.md) for the
full picture.

## Golden rules

1. **Respect the dependency rule.** `infrastructure → application → domain`.
   - The `domain` layer must have **no** imports from Spring, JPA, Jackson, or
     any other framework. If you're tempted to add one, you're in the wrong layer.
   - `application` depends only on domain **ports** (interfaces), never on
     `infrastructure`.
2. **Rich domain model.** Business rules and invariants live in the aggregate
   (`task/domain/model/Task.kt`), enforced at construction and via behaviour
   methods — not in services or controllers. Services orchestrate; they don't
   decide business rules.
3. **Map at every boundary.** Web DTO ⇄ application command ⇄ domain model ⇄ JPA
   entity. Don't let a JPA entity or a DTO reach the domain, and don't return a
   domain object directly from a controller.
4. **One aggregate per transaction.** `@Transactional` lives on the application
   service.
5. **English everywhere** — code, comments, commits, docs.

## Layer cheat-sheet (per bounded context, e.g. `task/`)

| Layer            | Package                       | Contains                                            | May import |
|------------------|-------------------------------|-----------------------------------------------------|------------|
| Domain           | `domain/`                     | aggregates, value objects, ports, domain exceptions | nothing framework |
| Application      | `application/`                | `@Service` use cases, commands                      | domain |
| Infrastructure   | `infrastructure/persistence/` | JPA entity, Spring Data repo, port adapter, mapper  | domain, application, Spring/JPA |
| Infrastructure   | `infrastructure/web/`         | `@RestController`, request/response DTOs            | domain, application, Spring web |
| Shared kernel    | `shared/`                     | base `DomainException`, `ApiError`, global handler, config | minimal |

## How to add a feature

- **New field on an existing aggregate:** update the domain model + its
  invariants → add a Flyway migration (`V<n>__...sql`) → update the JPA entity,
  mapper, DTOs, and command → add/extend tests.
- **New bounded context:** mirror `task/` (`domain`, `application`,
  `infrastructure/{persistence,web}`). See "Adding a New Bounded Context" in the
  README.
- **Schema changes:** ALWAYS a new Flyway migration. Never edit an applied one.
  Hibernate is `ddl-auto: validate` — it will not create tables for you.

## Commands

```bash
make db-up      # start Postgres in Docker
make run        # ./gradlew bootRun
make test       # ./gradlew test   (integration tests need Docker)
make up         # full stack in Docker
make build      # ./gradlew clean bootJar
```

Run `./gradlew test` (or the specific test class) and make sure it passes before
claiming a change is done. Integration tests require Docker to be running.

## Testing expectations

- Domain logic → pure unit tests, no Spring context.
- Use cases → unit tests with **MockK** (`io.mockk`), not Mockito.
- Anything touching the DB / full HTTP stack → extend `IntegrationTest`
  (Testcontainers Postgres). Don't mock the database in integration tests.

## Gotchas

- The Gradle **wrapper jar** (`gradle/wrapper/gradle-wrapper.jar`) is committed
  on purpose — do not gitignore it.
- JDK is pinned by the Gradle toolchain to 21; don't downgrade language/target
  levels to match a local JDK.
- `kotlin-jpa` / `all-open` plugins make `@Entity` classes open + give them a
  no-arg constructor; don't hand-roll that.
- Secrets only in `.env` (git-ignored). `application.yml` reads `${ENV:default}`.
