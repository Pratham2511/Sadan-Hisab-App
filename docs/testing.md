# Testing

Tests never use real tenant data. Any names that appear are invented
(Rahul Sharma, Priya Patil, Amit Kulkarni) with fictional numbers and amounts.

## Running

```bash
./gradlew testDebugUnitTest
```

## Suites

**`domain/AccountingEngineTest`** — the accounting rules:

- a new month is UNPAID and fully outstanding;
- ₹600 settled by three ₹200 payments, asserting PARTIALLY_PAID → PARTIALLY_PAID → PAID;
- partial followed by the exact remainder;
- one payment spanning several months, oldest first;
- payment gaps: Jan paid, Feb/Mar unpaid, Apr/May paid, Jun unpaid reports **two** separate
  unpaid runs, not one Jan–Jun block;
- historical rent: a 2024 month is charged ₹300 while the current rate is ₹400;
- rent resolution across several dated changes, returning null before the earliest rate;
- editing a payment from ₹600 down to ₹300 leaves exactly one allocation and a ₹300 balance;
- deleting a payment returns the month to UNPAID with ₹0 paid;
- overpayment, zero and negative amounts are rejected;
- allocation order is independent of input row order;
- recalculation is deterministic and idempotent;
- unresolved months are flagged and kept out of authoritative totals;
- zero-data summary returns empty rather than crashing;
- invariant checks reject over-allocation, duplicates and orphans.

**`domain/ImportEngineTest`** — import safety:

- every input row lands in exactly one of valid / review / rejected — none is dropped;
- missing room, unknown room, missing or non-positive amount, missing date and malformed or
  reversed periods are rejected with specific reasons;
- an unstated period goes to review rather than being guessed;
- re-importing the same file produces duplicates flagged for review, not double payments;
- duplicate detection uses a composite fingerprint, so genuine repeat instalments survive
  and rows with blank receipt numbers are still distinguished correctly;
- reconciliation classifies a real mismatch, unknown historical rent and a self-contradictory
  source as three different things;
- source arithmetic and month-count checks report contradictions without correcting them.

**`data/RoomInventoryTest`** — fresh install:

- exactly 48 rooms; A wing A-01…A-26 plus A-27(A) and A-27(B); B wing B-01…B-20;
- identities and sort keys are unique and order naturally;
- the inventory contains rooms only, never a tenant.

## Last recorded run

`./gradlew testDebugUnitTest` — **BUILD SUCCESSFUL**, 56 tests, 0 failures, 0 errors.
