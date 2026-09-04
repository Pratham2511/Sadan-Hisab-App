# Sadan

A native, offline-first Android property management and rent accounting application for Pansare Sadan.

Sadan provides a month-by-month financial ledger, automated multi-month payment allocations, PDF receipt generation, encrypted backups, and CSV/XLSX spreadsheet import pipelines without relying on cloud services, external servers, or network connectivity.

---

## Overview

Sadan is built for offline privacy and data control. All tenant profiles, payment histories, and property records remain strictly on the local device inside an application-private Room SQLite database.

---

## Features

- **Room Inventory**: Pre-configured physical inventory of 48 rooms (Wing A: A-01 through A-26, A-27(A), A-27(B); Wing B: B-01 through B-20).
- **Tenant Management**: Record tenancy start dates, update rent rates, manage contact details, and handle move-outs.
- **Occupancy Tracking**: Real-time wing-based occupancy status (Occupied vs. Vacant).
- **Month-by-Month Ledger**: Authoritative pure-Kotlin accounting engine tracking `UNPAID`, `PARTIALLY_PAID`, and `PAID` statuses.
- **Historical Rent Resolution**: Resolves effective monthly rent rates per period; handles historical rent changes accurately without corrupting past ledgers.
- **Partial Payments & Multi-Month Allocation**: Allocates payment amounts across current and past dues automatically with preview before saving.
- **Payment Editing & Reversal**: Transactional edit and deletion of payments with automatic ledger recalculation and state preservation.
- **PDF Receipt Generation**: Instant vector PDF receipt generation with standard Android Sharesheet integration.
- **Reports & Analytics**: Monthly collection summaries, yearly accounting overviews, defaulter reports, and per-tenant payment histories.
- **CSV / XLSX Import Pipeline**: Streaming ZIP/XML parser for Excel workbooks and CSV files with sheet selection, header matching, messy receipt string extraction, preview verification, duplicate detection, and transactional commit/rollback.
- **Encrypted Backup & Restore**: AES-GCM with PBKDF2-HMAC-SHA256 password-encrypted data backup and restore via Storage Access Framework.
- **Offline Storage**: 100% offline-first application with zero backend or internet dependency.

---

## Architecture

- **Language**: Kotlin 1.9+
- **UI Framework**: Jetpack Compose with Material 3 Design Components
- **Architecture Pattern**: MVVM (Model-View-ViewModel) with unidirectional reactive StateFlow pipelines
- **Database**: Room (SQLite) with transactional queries and indices on sort keys, display numbers, and tenant lookup IDs
- **Concurrency**: Kotlin Coroutines & Flow (IO-dispatched background calculations)
- **PDF Engine**: Android `PdfDocument` API

---

## Data & Privacy

- **Local Storage**: All application data is stored locally in device-private database storage (`sadan_database`).
- **Zero Cloud Requirement**: Normal operation requires no internet permission, backend services, Google Drive, or third-party cloud accounts.
- **Synthetic Test Data**: The repository contains no real tenant names, contact details, payment amounts, or private financial records. All unit tests and test fixtures use synthetic generated data (e.g. `Tenant A`, `Tenant B`).
- **Privacy for Contributors**: Contributors must never commit real tenant records, phone numbers, identity documents, bank account details, or production spreadsheet files.

---

## Accounting Model

```
RentChange    -> Rent rate in force for a given month
MonthlyLedger -> One row per tenant-month tracking amount owed and paid
Payment       -> Transaction record of money received
Allocation    -> Amount of a payment applied to a specific ledger month
```

Financial standing (`Regular`, `Partially Paid`, `Defaulter`) is dynamically derived from allocations against monthly dues.

### Partial Payments Example

| Transaction | Paid Amount | Month Status | Outstanding Balance |
| :--- | :--- | :--- | :--- |
| Rent ₹600 due | ₹0 | `UNPAID` | ₹600 |
| Payment 1 (₹200) | ₹200 | `PARTIALLY_PAID` | ₹400 |
| Payment 2 (₹200) | ₹400 | `PARTIALLY_PAID` | ₹200 |
| Payment 3 (₹200) | ₹600 | `PAID` | ₹0 |

Multiple payment transactions can settle the same month. Allocations never exceed month dues, and overpayments are rejected to prevent financial drift.

---

## Import Pipeline (CSV / XLSX)

Sadan includes a streaming parser capable of reading `.xlsx` Excel workbooks and `.csv` files:

1. **Sheet Selection**: Enumerates sheets (e.g. `"A Wing"`, `"B Wing"`) for multi-sheet workbooks.
2. **Normalisation**: Normalizes room numbers (`B1` -> `B-01`, `B20` -> `B-20`, `A27A` -> `A-27(A)`) and rent amounts (`"400 Renter"` -> `400L`).
3. **Receipt & Note Parsing**: Parses free-form receipt strings (e.g. `"302/29/09 400 jan 26 to july 26"`) into receipt numbers, dates, amounts, modes (`CASH`, `UPI`, `CHEQUE`), and month ranges.
4. **Validation & Classification**: Rows are categorized into `VALID`, `REVIEW`, or `REJECTED` states before any database writes occur.
5. **Transactional Commit**: Imports valid rows inside a single database transaction with safety rollback on error.

---

## Backup & Restore

- **Encryption**: AES-256-GCM encryption with PBKDF2-HMAC-SHA256 key derivation using random per-backup salts and nonces.
- **Safety**: Restores validate and decrypt completely in memory before updating database tables in a single transaction.

---

## Development & Building

### Prerequisites
- JDK 17
- Android SDK (compileSdk 36, minSdk 26)
- Android Studio Ladybug or newer

### Build Commands

```bash
# Run unit tests
./gradlew testDebugUnitTest --no-daemon --console=plain

# Compile Kotlin source
./gradlew compileDebugKotlin --no-daemon --console=plain

# Build debug APK
./gradlew assembleDebug --no-daemon --console=plain
```

---

## Testing

Comprehensive test suites cover:
- Accounting ledger engine invariants and partial allocations (`AccountingEngineTest.kt`)
- Room inventory 48-room verification (`RoomInventoryTest.kt`)
- Reactive Flow state scaling & invisible tenant regression prevention (`InvisibleTenantRegressionTest.kt`)
- CSV and XLSX import normalization & messy receipt extraction (`CsvImportTest.kt`, `XlsxImportTest.kt`, `ImportEngineTest.kt`)
