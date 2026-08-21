# Contributing

## Before you start

Read [CLAUDE.md](CLAUDE.md) — it holds the rules this codebase is actually
held to (layering, testing, migrations). The [ADRs](docs/adr/) explain why the
awkward parts are the way they are; skim them before proposing to change one.

## Setup

```bash
make db-up     # Postgres in Docker
make run       # ./gradlew bootRun
make test      # integration tests need Docker running
```

Copy `.env.example` to `.env` first. Nothing in the repo contains a working
secret, so the app will refuse to start until you do.

## Branching and commits

Branch from `main` as `feat/…`, `fix/…`, `refactor/…`, `docs/…`.

Commits follow [Conventional Commits](https://www.conventionalcommits.org):

```
fix(billing): make a fractional debt actually payable
```

Write the body to explain **why**, not what — the diff already shows what.
When a commit fixes a bug, say what the bug did to a user.

## The bar for a pull request

- `./gradlew test` passes. Integration tests need Docker.
- New behaviour has a test. A bug fix has a test that fails without the fix —
  if you cannot write one, say so in the PR and explain why.
- Schema changes have a Flyway migration. Never edit an applied one, and
  check `main` for a colliding version number before merging.
- The dependency rule holds: nothing in `domain/` imports Spring or JPA.
- Messages in code are English; user-facing text is Persian.

## Review

At least one approval. Reviewers look for correctness first and style last.
Push fixes as new commits rather than force-pushing during review, so the
reviewer can see what changed.
