# Architecture

Offline-first, single-module (`:app`), package `com.pansare.sadan`. No backend, no network
permission.

```
UI (Compose, Material 3)
  └── AppViewModel          one StateFlow-backed UI state + one-shot UiEvent channel
        └── RentRepository  the only writer; wraps every multi-step change in a transaction
              ├── Room DAOs
              └── domain/   pure Kotlin engines, no Android imports
```

## Layers

**`domain/`** — framework-free and directly unit-testable.

- `Accounting.kt` (`LedgerEngine`) — derives month state, plans allocations, computes
  outstanding, unpaid periods, defaulter summaries, and verifies invariants.
- `RentResolver.kt` — the rent in force for a given month, from dated `RentChange` rows.
- `ImportEngine.kt` — validation, fingerprinting, duplicate detection, reconciliation.
- `MonthKey.kt` — `yyyy-MM` arithmetic and formatting.

**`data/`** — Room entities, DAOs, database, and `RentRepository`.

`RentRepository` is the sole write path. It recalculates the affected tenant's ledger from
its allocations after every change and runs `verifyTenantInvariants` before commit, so a
write that would corrupt the books aborts instead of persisting.

**`ui/`** — Compose screens under `ui/<feature>/`, navigation in `ui/navigation` and
`ui/PansareApp.kt`, shared widgets in `ui/components`, report assembly in `ReportBuilder`.

**`util/`** — `BackupCrypto` (PBKDF2 + AES-GCM), `ReceiptPdf` (A4 receipts drawn with
`PdfDocument`), `ShareUtils` (FileProvider + Sharesheet), plus currency and date formatting.

## State

Statuses are **derived, never stored as truth**. A month's status is computed from the sum of
its allocations against its resolved rent, so no code path can leave a status disagreeing
with the money. The stored status column is a projection refreshed by recalculation.

## Fresh install

`initialiseIfEmpty()` runs only when the rooms table is empty and inserts the 48 rooms and
nothing else — zero tenants, payments and ledger rows. There is no seed data in the
production path.
