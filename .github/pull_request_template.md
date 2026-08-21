## What and why

<!-- What changes, and what problem it solves. If it fixes a bug, say what the
     bug did to a user. -->

Closes #

## How it was verified

<!-- Commands run, cases covered. "Tests pass" alone is not verification —
     name what the new test would catch. -->

- [ ] `./gradlew test` passes locally (Docker running for integration tests)
- [ ] New behaviour is covered by a test, or the PR explains why not
- [ ] Schema change has a Flyway migration with a version not used on `main`
- [ ] `domain/` still imports no Spring or JPA

## Anything reviewers should look at closely

<!-- Trade-offs taken, parts you are unsure about, follow-ups deliberately
     left out. -->
