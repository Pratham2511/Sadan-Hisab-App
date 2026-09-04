# Database

Room, SQLite, WAL, schema version 1, `exportSchema = true`.

## Tables

| Entity | Purpose |
| --- | --- |
| `RoomEntity` | The 48 physical rooms. Created on first launch, never by the user. |
| `TenantEntity` | Name, mobile, room, move-in/out dates, deposit, status. |
| `RentChangeEntity` | Dated rent rates per tenant. Drives per-month rent. |
| `MonthlyLedgerEntity` | One row per tenant per month: rent due, amount paid, status, rent certainty. |
| `PaymentEntity` | A money event: amount, date, mode, receipt number, note. |
| `PaymentAllocationEntity` | Links a payment to a ledger month for a specific amount. |
| `ImportValidationIssueEntity` | Rows needing review or rejected on import, plus reconciliation mismatches. |
| `AppSettingEntity` | Key/value app settings (property name, address, receipt prefix). |

## Key relationships

`PaymentAllocationEntity` is the join that makes partial payments exact: a payment may have
many allocations and a month may receive many. Both foreign keys cascade on delete, and a
UNIQUE index on `(paymentId, ledgerId)` makes a double allocation impossible at the storage
layer, not merely in code.

A UNIQUE index over the import fingerprint prevents the same source row being ingested twice
even if validation is bypassed.

## Ledger

`MonthlyLedgerEntity.rentCertainty` records whether the month's rent is `KNOWN`,
`APPROXIMATED` or `UNRESOLVED`. Only `KNOWN` months contribute to authoritative outstanding
totals; `UNRESOLVED` months are surfaced for review and never carry an invented amount.

Imported outstanding figures from prior records are stored as a reconciliation reference
only. They never override computed balances.

## Migrations

Version 1 is the first released schema, so no migrations exist yet. `exportSchema` is on and
schemas are written to `app/schemas/` when the build runs, which is what future migration
tests will diff against. Destructive fallback is **not** enabled.
