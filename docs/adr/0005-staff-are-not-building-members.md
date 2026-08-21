# 5. Service staff belong to no building

**Status:** Accepted

## Context

Staff were initially modelled as members of the building they worked in, by
analogy with residents. That was wrong: staff are a shared pool assigned work
across buildings. Modelling them as members produced a real bug — assigning a
request to a worker returned 403, because the worker was not a "member" of
that building.

## Decision

Staff have no residency and no building membership. They receive work through
service requests only. Every entry point that implies membership — residency,
the members list, support tickets — refuses the STAFF role.

## Consequences

**Gained.** Assignment works across buildings, which is what the business
actually needs, and the "which building is this worker in" question simply
disappears.

**Given up.** Access control for staff cannot be derived from a building, so
each staff-facing use case authorizes on the assignment instead. That is more
explicit but also more code.

**Note.** `V38` cleans up staff who were made members before this rule
existed; ending the residency rather than deleting it keeps the unit's
occupancy history readable.
