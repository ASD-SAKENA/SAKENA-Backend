# Service request flow: unit visibility, resident confirmation, staff rating

Status: approved for planning
Date: 2026-08-17
Repos affected: SAKENA-Backend, SAKENA-FRONTEND

## Problem

The service-request flow has three gaps raised by the product owner:

1. A manager reviewing the queue cannot tell which unit a request came from
   — the only field shown is `location`, free text the resident typed, not a
   real reference to their apartment.
2. There is no quality signal on staff. A manager assigning a request has no
   way to tell a reliable worker from an unreliable one.
3. A staff member can mark a request `COMPLETED` unilaterally and the manager
   can pay them out — the resident who filed the request is never asked
   whether the work was actually done.

This spec covers all three, scoped to what's needed to close them cleanly —
not a rewrite of the service-request system.

## Current state (verified in code)

- `ServiceRequest` (`servicerequest/domain/ServiceRequest.kt`) is a flat
  aggregate with a linear status machine:
  `PENDING → APPROVED → ASSIGNED → IN_PROGRESS → COMPLETED → SETTLED`,
  with `PENDING → REJECTED` as the only branch.
- `requestingApartmentId` is captured at creation (from the resident's active
  residency, now mandatory — see the residency-gate work already shipped) and
  stored, but never resolved to a human label anywhere in the API response or
  the frontend. `ServiceRequestResponse` exposes the raw UUID only.
- The frontend's "unit" column (`ManagerRequest.unit`) actually shows
  `location`, a free-text field the resident optionally fills in — unrelated
  to `requestingApartmentId`.
- `complete()` is called by the assigned staff member alone
  (`ServiceRequestController.completeRequest`, gated by
  `assignedTo == command.userId`). It sets `status = COMPLETED` and stores
  `completionReport`/`completionCost`, both self-reported by staff.
- From `COMPLETED`, a manager can call `assignCostResponsibility` and then
  `WalletService.settleServiceRequest`, which pays the worker — the resident
  has no say at any point in this chain.
- Staff are a system-wide pool (`StaffDirectoryService.getActiveStaff()`,
  added recently) with no quality signal — `UserSummaryResponse` has no
  rating field, and no rating concept exists in the domain at all.
- `"priority"` on the manager queue is already fake: `api/requests.ts`'s
  `toManagerRequest` hardcodes `priority: "نامشخص"` with a comment admitting
  it isn't modeled server-side. Out of scope here — noted only so it isn't
  mistaken for something this spec touches.

## Decisions from stakeholder Q&A

- A resident rejecting a completed job sends the request back to
  `IN_PROGRESS` for the same staff member to redo — not back to the manager.
- The resident rates the worker (1–5 stars) in the same action as confirming
  the work — one combined "تایید و امتیازدهی" step, not two.
- The staff average rating is **display-only** next to their name in the
  manager's assign-worker picker — no eligibility threshold, no gating.
- Unit display resolves the real apartment (`requestingApartmentId` →
  `Apartment`) rather than reusing the free-text `location` field, which
  stays as a separate, optional "additional detail" the resident can still
  fill in.
- Requests with no `requestingApartmentId` (only possible for the handful of
  legacy/staff-filed requests that predate the residency-required-to-file
  gate) skip the confirmation step entirely — a manager can settle them
  directly from `COMPLETED`, exactly as today. No backfill migration, no new
  code path: this is just "the confirmation step requires a resident to
  confirm; no resident on the request, no gate."

## Design

### 1. Unit label on a service request

**Backend**

- `ServiceRequestResponse` gains a nullable `requestingUnit: RequestingUnitResponse?`
  object (not just a formatted string, so the frontend can compose its own
  label):
  ```kotlin
  data class RequestingUnitResponse(
      val unitNumber: String,
      val floorNumber: Int,
      val buildingName: String,
  )
  ```
- Resolved in `ServiceRequestController` (or a small mapper) via
  `ApartmentRepository.findById` + `BuildingRepository.findById`, mirroring
  the exact pattern already used in `DashboardService.forResident` for
  `ResidentUnitInfo`. No new repository methods needed.
- `location` stays on the response, untouched, as free-text detail.

**Frontend**

- `ServiceRequestApiResponse` gains `requestingUnit: { unitNumber, floorNumber, buildingName } | null`.
- `ManagerRequest.unit` is computed from `requestingUnit` (e.g.
  `"۱۲ — طبقه ۳"`) instead of `location`; falls back to `"—"` when null
  (the legacy/staff-filed edge case above).
- `location`, when present, is shown in the request detail/modal as
  "جزئیات مکان" — not in the queue table.

### 2. Resident confirmation gate

**Backend — new domain method + one new status**

Add `CONFIRMED` to `ServiceRequestStatus`, inserted between `COMPLETED` and
`SETTLED`:

```
PENDING → APPROVED → ASSIGNED → IN_PROGRESS → COMPLETED → CONFIRMED → SETTLED
                                                    ↓ (resident rejects)
                                              IN_PROGRESS
```

New `ServiceRequest` domain methods:

```kotlin
fun confirmCompletion(userId: UserId): ServiceRequest {
    if (status != ServiceRequestStatus.COMPLETED) {
        throw DomainValidationException("Service request can only be confirmed when it is completed")
    }
    if (createdBy != userId) {
        throw DomainForbiddenException("Only the resident who created this request can confirm it")
    }
    return copy(status = ServiceRequestStatus.CONFIRMED, updatedAt = Instant.now(), updatedBy = userId)
}

fun rejectCompletion(userId: UserId): ServiceRequest {
    if (status != ServiceRequestStatus.COMPLETED) {
        throw DomainValidationException("Service request can only be rejected when it is completed")
    }
    if (createdBy != userId) {
        throw DomainForbiddenException("Only the resident who created this request can reject it")
    }
    return copy(
        status = ServiceRequestStatus.IN_PROGRESS,
        completionReport = null,
        completionCost = null,
        resolvedAt = null,
        updatedAt = Instant.now(),
        updatedBy = userId,
    )
}
```

`assignCostResponsibility()` and `settle()` on the `ServiceRequest` aggregate
both change their status guard to accept either `CONFIRMED`, or `COMPLETED`
when `requestingApartmentId == null`:

```kotlin
private fun requireSettleable() {
    val settleableFromCompleted = status == ServiceRequestStatus.COMPLETED && requestingApartmentId == null
    if (status != ServiceRequestStatus.CONFIRMED && !settleableFromCompleted) {
        throw DomainValidationException("Service request must be confirmed by the resident before this action")
    }
}
```

This lives entirely inside the aggregate (both methods call it first), so
there is exactly one place the rule is expressed — not split between the
domain and `WalletService`.

**Backend — application service + endpoints**

`ServiceRequestService` gains:

```kotlin
fun confirmCompletionAndRate(id: ServiceRequestId, residentId: UserId, score: Int): ServiceRequest
fun rejectCompletion(id: ServiceRequestId, residentId: UserId): ServiceRequest
```

`rejectCompletion` simply loads, calls the domain method (which already
enforces `createdBy == residentId`), saves. Confirming always rates in the
same step per the "simultaneous" decision, so there is no separate
rating-less `confirmCompletion` — `confirmCompletionAndRate` is the only
entry point:

New endpoint: `PATCH /api/v1/service-requests/{id}/confirm`
Body: `{ score: 1..5 }` (required — confirming always rates, since there's no
"confirm without rating" path per the decision above)
→ `ServiceRequestService` takes `RatingService` as a constructor dependency
  and exposes `confirmCompletionAndRate(id, residentId, score)`, which calls
  `confirmCompletion` then `ratingService.rate(...)` inside its own
  `@Transactional` method — one transaction, one commit. `ServiceRequestService`
  owns it (rather than the controller sequencing two service calls) so a
  failure partway through cannot leave a `CONFIRMED` request with no rating.

New endpoint: `PATCH /api/v1/service-requests/{id}/reject-completion`
Body: none.
→ calls `ServiceRequestService.rejectCompletion`.

Both endpoints: no `@PreAuthorize` role restriction beyond `authenticated()`
(same as the existing resident-facing endpoints) — the domain-level
`createdBy == residentId` check is the real authorization, consistent with
how `updateRequest` already works.

**Frontend**

- `ServiceRequestApiStatus` gains `"CONFIRMED"`.
- `RequestCard` (resident's `/requests` page): when `apiStatus === "COMPLETED"`,
  show a new action block: "تایید انجام کار" (opens a small modal with a
  5-star picker, required) and "کار درست انجام نشده" (single-click reject,
  maybe a confirm dialog since it's destructive to the report/cost).
- New mutation hooks: `useConfirmCompletionMutation()`,
  `useRejectCompletionMutation()` in `queries/requests.ts`.
- Manager's `/queue` page: the "پرداخت دستمزد" (settle) button, currently
  shown for `apiStatus === "COMPLETED"`, moves to `apiStatus === "CONFIRMED"`.
  A `COMPLETED` row shows a plain status badge ("در انتظار تایید ساکن") with
  no action — the manager cannot act until the resident does.

### 3. Staff rating

**Backend — new bounded context `rating/`**, mirroring `poll/`'s shape:

```
rating/domain/model/StaffRating.kt       — aggregate: id, serviceRequestId, staffId, residentId, score, createdAt
rating/domain/RatingRepository.kt        — port
rating/application/RatingService.kt      — rate(), getAverageFor(staffId): Double?
rating/infrastructure/persistence/...    — JPA entity + adapter, mirroring PollEntities.kt
```

`StaffRating.create(serviceRequestId, staffId, residentId, score)` validates
`score in 1..5`. One rating per service request — enforced by a unique DB
constraint on `service_request_id`, so a request can never be rated twice
(matches "confirm always rates, confirm happens once" — no edit/re-rate flow
needed since a `CONFIRMED` request can't be un-confirmed).

Migration `V27__create_staff_ratings.sql`:
```sql
CREATE TABLE staff_ratings (
    id UUID PRIMARY KEY,
    service_request_id UUID NOT NULL UNIQUE REFERENCES service_requests(id),
    staff_id UUID NOT NULL REFERENCES users(id),
    resident_id UUID NOT NULL REFERENCES users(id),
    score SMALLINT NOT NULL CHECK (score BETWEEN 1 AND 5),
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_staff_ratings_staff_id ON staff_ratings(staff_id);
```

`RatingService.rate(serviceRequestId, staffId, residentId, score)`: called
from `ServiceRequestService.confirmCompletionAndRate`'s transaction (see
part 2). Reads `assignedTo` off the just-confirmed request as `staffId` —
the caller doesn't pass it separately, removing any chance of mismatch.

`RatingService.getAverageFor(staffId): Double?` — a single aggregate query
(`AVG(score) WHERE staff_id = ?`), null when the staff has no ratings yet.

**`StaffDirectoryService.getActiveStaff()`** extended: each returned
`User` is paired with its average, via a new response-shaping step (not a
domain change to `User` itself — rating is not a `User` property, it's a
derived read). `StaffController`'s response DTO becomes:

```kotlin
data class StaffSummaryResponse(
    val id: String, val username: String, val specialty: String?,
    val active: Boolean, val averageRating: Double?,
)
```

(Computed by one extra query — `RatingRepository.findAverageByStaffIds(ids: List<UserId>): Map<UserId, Double>`
— batched, not N+1.)

**Frontend**

- `UserSummaryApiResponse` used by `/staff` gains `averageRating: number | null`
  — note this is `/staff`-specific, not a global `User` field, so it lives on
  a new `StaffSummaryApiResponse` type (the admin `/users` page keeps using
  the existing `UserSummaryApiResponse` without a rating field, since ADMIN's
  user list has no rating concept).
- `queue/page.tsx`'s assign-worker `<AppSelect>` shows
  `"${s.username} — ${s.specialty ?? "بدون تخصص"} · ⭐ ${s.averageRating?.toFixed(1) ?? "بدون امتیاز"}"`.

## Error handling

- `confirmCompletion`/`rejectCompletion` on a request not in `COMPLETED` →
  existing `DomainValidationException` pattern → 409, same as every other
  invalid-transition case in this aggregate.
- `confirmCompletion`/`rejectCompletion` by someone other than `createdBy` →
  `DomainForbiddenException` → 403, matching `updateRequest`'s existing
  ownership check.
- `RatingService.rate` with `score` outside 1–5 → `DomainValidationException`
  at construction (mirrors every other domain validation in this codebase).
- `settle()`/`assignCostResponsibility()` called on a `COMPLETED` (not yet
  `CONFIRMED`) request with a non-null `requestingApartmentId` →
  `DomainValidationException` — the manager UI should never let this happen
  (button is hidden), but the domain guard is the real enforcement.

## Testing

- Domain: `ServiceRequestTest` gains cases for `confirmCompletion`/
  `rejectCompletion` (happy path, wrong status, wrong user).
- Domain: new `StaffRatingTest` for score bounds.
- Application: `ServiceRequestServiceTest` gains confirm/reject cases;
  `WalletServiceTest` gains a case proving `settle()` rejects a
  merely-`COMPLETED` (not `CONFIRMED`) request when `requestingApartmentId`
  is set, and accepts it when null.
- Application: new `RatingServiceTest` (rate, duplicate-rating rejected by
  the unique constraint at the integration level, average computation).
  new `StaffDirectoryServiceTest` case for average attached correctly.
- Controller: `ServiceRequestControllerTest` gains cases for the two new
  endpoints (success, wrong-user 403, wrong-status 409).
- Frontend: `api/requests.test.ts` for `requestingUnit` mapping and the two
  new mutations; `api/staff.test.ts` for `averageRating` passthrough;
  component-level tests for the new confirm/reject UI on `RequestCard` and
  the settle-button visibility change on `/queue`.

## Out of scope

- Priority field (already fake, not touched here — flagged to the user
  separately if they want it addressed).
- Any change to how staff are assigned to buildings (staff remain a
  system-wide pool, per the earlier confirmed decision).
- Editing or retracting a submitted rating.
- Notifying the resident when a request reaches `COMPLETED` (push/email) —
  today there's no notification system for status changes at all; adding one
  is a separate, larger piece of work.
