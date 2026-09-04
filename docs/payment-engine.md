# Payment engine

All allocation logic lives in `LedgerEngine` (`domain/Accounting.kt`). It is pure Kotlin —
no Room, no Android — so every rule below is unit-tested on a plain JVM.

## Allocation

1. The ledger months for the chosen period are loaded and their derived state computed from
   existing allocations (`computeStates`).
2. Months are ordered deterministically by month key, then by id. Database row order is
   never relied upon.
3. The payment cascades oldest-outstanding-first, taking `min(remaining, month.outstanding)`
   from each month until it is exhausted.

A single payment may settle several months, and a single month may be settled by any number
of payments.

## Overpayment

There is **no credit or advance facility**. If the amount exceeds the total outstanding for
the selected period, `plan` throws `AccountingException` and nothing is written. The user is
told the actual capacity and asked to reduce the amount or widen the period.

This is deliberate: silently parking unallocated money on a payment record produces figures
that cannot be reconciled later.

## Preview before saving

`RentRepository.previewPayment` runs the same planner without writing anything. The payment
screen shows, per month, what was owed before, how much is being applied, and the resulting
status — so allocation is never a silent guess.

## Editing and deleting

Editing is *reverse then re-apply*, inside one transaction:

1. delete the payment's allocations;
2. re-plan against the ledger as it now stands (`planForEdit` excludes the payment being
   edited, so it can never double count);
3. write the updated payment and its new allocations;
4. recalculate every month for the tenant;
5. verify invariants — a violation aborts the transaction.

Deleting performs steps 1, 4 and 5. Neither operation can leave a stale balance.

## Historical rent

`RentResolver` picks the rate in force for each month from the tenant's `RentChange` rows.
Outstanding is never `months × current rent`. A month that predates every known rate is
marked `UNRESOLVED`, given no invented amount, excluded from firm totals, and raised as a
validation issue.

## Invariants

`LedgerEngine.verifyInvariants` asserts, after every mutating operation:

- no negative outstanding;
- no month paid beyond its rent;
- allocations for a payment never exceed that payment's amount;
- no duplicate `(payment, month)` allocation rows;
- no allocation pointing at a missing month or payment.

Recalculation is deterministic and idempotent — running it twice yields identical rows.
