# 2. Flyway migrations with `ddl-auto: validate`

**Status:** Accepted

## Context

Hibernate can create and update the schema from the entity classes. That is
convenient in development and dangerous in production: the generated DDL is
whatever the current mapping implies, with no review step and no way to
express a data change ("backfill this column", "vacate these rows").

## Decision

Every schema change is a numbered Flyway migration. Hibernate runs with
`ddl-auto: validate`, so it verifies the mapping against the real schema and
refuses to start on a mismatch, but never alters anything itself.

An applied migration is never edited; a mistake is corrected by a new one.

## Consequences

**Gained.** The schema has a reviewable history, and data fixes live beside
structural ones — `V38` vacating staff residencies is a data change no
generated DDL could have expressed.

**Given up.** Adding a field is two steps instead of one, and forgetting the
migration fails at startup rather than silently working. That failure is the
point: it happens on the developer's machine, not in production.

**Watch out.** Two branches adding `V<n>` with the same number merge cleanly
in git and then fail in Flyway. It has already happened twice here; renumber
before merging.
