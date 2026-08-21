# 1. DDD + Clean Architecture layering

**Status:** Accepted

## Context

The domain has real rules — who may book a facility, when a ticket reopens,
how a charge splits across units — and those rules are the part of the system
most likely to be misunderstood or quietly broken. Spring makes it easy to
put such logic in a `@Service` next to a JPA repository, where it becomes
inseparable from the framework and hard to test.

## Decision

`infrastructure → application → domain`, with the dependency rule enforced by
convention and review:

- `domain/` holds aggregates, value objects and ports. **No** Spring, JPA or
  Jackson imports.
- `application/` orchestrates use cases against domain ports only.
- `infrastructure/` implements the ports and adapts the web and database.

Business rules live in the aggregate and are enforced at construction and in
behaviour methods, not in services.

## Consequences

**Gained.** Domain rules are unit-testable with no Spring context, which is
why the status machine and the cost split have direct tests rather than being
exercised only through HTTP.

**Given up.** More mapping code: web DTO ⇄ command ⇄ domain ⇄ entity, four
shapes for one concept. This is real, ongoing cost and the most common
complaint about the structure. We accept it because the alternative — a JPA
entity used as the domain model — makes every schema change a domain change.
