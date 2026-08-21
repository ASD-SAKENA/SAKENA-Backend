# 3. Money is whole Toman, split at the domain

**Status:** Accepted

## Context

Splitting a 100,000 Toman expense across three units gives 33,333.33 — an
amount nobody can pay, since Toman has no sub-unit. The first version invoiced
exactly that and the residents were stuck.

## Decision

- A charge line must be a whole number of Toman; `ChargeItem` rejects a
  fraction at construction.
- `CostAllocationPolicy` divides with scale 0 and puts the rounding remainder
  on the last unit, so the shares add up to the total exactly.
- A *payment* may still carry up to two decimals, because invoices issued
  before this rule still have fractional balances that must be settleable.

## Consequences

**Gained.** New invoices are always payable, and the building never over- or
under-bills: the remainder-on-last rule makes the shares sum to the item.

**Given up.** The last unit pays up to (n-1) Toman more than the others.
At this scale that is under a rial per resident and not worth the complexity
of rotating who absorbs it.

**Given up.** Two validators instead of one — `positiveWholeAmountString` for
new amounts, `positiveAmountString` for payments. Collapsing them would either
re-break old debts or let fractions back into new charges.
