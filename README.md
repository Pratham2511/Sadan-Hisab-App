# Sadan

Offline-first rent and ledger management for **Pansare Sadan**, Sakinaka, Mohili Village.

Native Android. Kotlin, Jetpack Compose, Material 3, Room. No backend, no account, no
network dependency — the device's database is the source of truth.

---

## What the app does

- Tracks the property's **48 rooms** (A-01…A-27 plus A-27(A) and A-27(B); B-01…B-20).
- Records tenancies, rent changes and move-outs.
- Keeps a **month-by-month ledger** per tenant with real accounting states:
  `UNPAID`, `PARTIALLY_PAID`, `PAID`.
- Allocates payments across months and shows a **preview before saving**.
- Generates and shares **PDF receipts** through the Android Sharesheet.
- Produces collection, outstanding, defaulter and tenant-history reports.
- Exports and restores **encrypted, versioned backups** via the Storage Access Framework.

## First launch

A fresh installation contains the 48 rooms and **nothing else** — no tenants, no payments,
no ledger rows, no sample data. All rooms start vacant and the dashboard invites you to add
your first tenant.

The repository contains **no real tenant information**. Any names used in tests are
invented (Rahul Sharma, Priya Patil, Amit Kulkarni) with fictional numbers and amounts.

---

## Accounting model

```
RentChange   → the rent rate in force for a given month
MonthlyLedger→ one row per tenant-month: what is owed
Payment      → one transaction of money received
Allocation   → how much of which payment settled which month
```

Derived state (`paid`, `outstanding`, `status`) is always **computed from allocations**,
never trusted from storage. The cached columns on the ledger row are recomputed inside the
same transaction that changes any allocation, so they cannot drift.

### Partial payments

A month is not "settled by one payment". Any number of payments may contribute to a month:

| Event | Paid | Status | Outstanding |
|---|---|---|---|
| Rent ₹600 due | ₹0 | UNPAID | ₹600 |
| Pay ₹200 | ₹200 | PARTIALLY_PAID | ₹400 |
| Pay ₹200 | ₹400 | PARTIALLY_PAID | ₹200 |
| Pay ₹200 | ₹600 | PAID | ₹0 |

### Historical rent

Each month is charged **the rate that applied to that month**, resolved from `RentChange`.
Outstanding is never `months × current rent`. If a month predates every known rate, the app
does **not** invent a figure: the month is marked `UNRESOLVED`, excluded from firm totals,
and raised as an issue for review.

### Invariants

Enforced in `LedgerEngine.verifyInvariants` and checked after every mutating operation:

- no negative outstanding;
- no month paid beyond its rent (there is no credit/advance facility, so overpayment is
  rejected with a clear message rather than absorbed);
- allocations for a payment never exceed the payment amount;
- no duplicate allocation rows;
- recalculation is deterministic and idempotent.

Adding, editing, deleting and importing payments each run in a single Room transaction.

---

## Architecture

```
Compose UI
  → AppViewModel            (immutable UI state, StateFlow)
    → RentRepository        (transactions, validation, persistence)
      → LedgerEngine        pure Kotlin accounting core
        RentResolver        historical rent resolution
        ImportEngine        validation, duplicates, reconciliation
      → DAOs → Room
```

`LedgerEngine`, `RentResolver` and `ImportEngine` have no Android or Compose dependency, so
the accounting rules are unit-tested on a plain JVM.

---

## Backups

AES-GCM with a PBKDF2-HMAC-SHA256 derived key, a fresh random salt and nonce per backup,
and a versioned envelope. Passwords are never stored. A wrong password or a corrupted file
fails authentication and reports a clear error. Restore validates and decrypts fully before
touching the database, then replaces it inside one transaction — a failed restore leaves the
existing data intact.

---

## Building

Open the project in Android Studio and build normally, or:

```bash
./gradlew testDebugUnitTest    # unit tests
./gradlew assembleDebug        # debug APK
```

Requires JDK 17 and the Android SDK (compileSdk 36, minSdk 26).

## Tests

```bash
./gradlew testDebugUnitTest
```

Covers the partial-payment sequence, multi-month allocation, payment gaps, historical rent
changes, unresolved history, payment edit and delete, overpayment rejection, allocation
determinism, import validation, duplicate detection, reconciliation classification, the
48-room inventory and the database invariants.

## Privacy

All records stay on the device. The app requests no network permission. Personal financial
details are never written to logs, and `.gitignore` excludes databases, backups, exports,
receipts and signing material.
