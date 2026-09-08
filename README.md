# Pansare Sadan

<p align="center">
  <img src="store-assets/pansare_sadan_play_store_icon_512.png" alt="Pansare Sadan app icon" width="160" />
</p>

<p align="center">
  Offline-first Android property management and rent accounting.
</p>

## Overview

Pansare Sadan is a native Android application for managing rooms, tenants, rent ledgers, payments, receipts, reports, spreadsheet imports, and encrypted backups. Core accounting and storage work locally on the device without requiring a backend service.

## Features

- **Room and tenant management** — occupancy, contact details, rent changes, and move-outs.
- **Month-by-month rent ledger** — derives paid, partially paid, and unpaid states from actual allocations.
- **Partial and multi-month payments** — previews allocation before saving and prevents over-allocation.
- **Payment editing and reversal** — recalculates affected ledger state transactionally.
- **PDF rent receipts** — generates receipts locally and supports Android sharing.
- **WhatsApp receipt sharing** — opens WhatsApp or WhatsApp Business for one-tap receipt sharing when available.
- **CSV/XLSX importing** — supports multiple worksheets, dynamic header detection, flexible row counts, validation preview, duplicate checks, and transactional commit/rollback.
- **Reports** — collection summaries, outstanding balances, defaulters, and tenant payment history.
- **Encrypted backup and restore** — local password-protected backups using authenticated encryption.
- **Single light theme** — a Solarized-light-inspired interface independent of the device dark-mode setting.

## Import Safety

Spreadsheet import is intentionally conservative:

1. The selected sheet is parsed without a hardcoded row or column limit.
2. Data is normalized and validated before anything is written.
3. Every parsed payment is classified as ready, needs review, or rejected.
4. Missing tenant/room relationships are surfaced before commit.
5. Duplicate payments and receipt numbers are detected.
6. Valid rows are committed in a single database transaction; failures roll back the import.

The importer does not silently invent missing accounting history.

## Privacy

This repository must not contain production tenant data.

- Real tenant names, phone numbers, payment records, bank details, identity documents, or production spreadsheets must never be committed.
- Tests and examples must use synthetic placeholder data only.
- Application data is stored in the app-private local Room database.
- Core operation does not require a cloud backend.
- The app does **not** automatically read UPI transactions, SMS messages, or payment notifications to create payment records.

## Architecture

| Area | Technology |
| --- | --- |
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM + StateFlow |
| Database | Room / SQLite |
| Concurrency | Kotlin Coroutines + Flow |
| PDF | Android `PdfDocument` |
| Backup encryption | AES-GCM with PBKDF2-HMAC-SHA256 |
| Minimum Android | API 26 |
| Compile / target SDK | 36 |

### Accounting model

```text
RentChange
    -> MonthlyLedger
        -> PaymentAllocation
            <- Payment
```

Payment standing is derived from ledger dues and allocations rather than stored as an independent source of truth.

## Project Structure

```text
app/src/main/java/com/pansare/sadan/
├── data/        Room entities, DAOs, repository and backup layer
├── domain/      Accounting, month and import rules
├── ui/          Compose screens, navigation and view model
└── util/        XLSX/CSV parsing, receipts, sharing and helpers
```

## Build

### Requirements

- JDK 17
- Android SDK 36
- Android Studio with current Android Gradle tooling

### Commands

```bash
./gradlew testDebugUnitTest --no-daemon --console=plain
./gradlew compileDebugKotlin --no-daemon --console=plain
./gradlew assembleDebug --no-daemon --console=plain
```

## Testing

The test suite covers the accounting engine, room inventory, duplicate protection, month handling, CSV/XLSX parsing, receipt generation, and regression cases.

## App Icon and Play Store Asset

The launcher resources live under `app/src/main/res/mipmap-*` with the adaptive icon definition under `mipmap-anydpi-v26`.

The Play Store-ready source is:

```text
store-assets/pansare_sadan_play_store_icon_512.png
```

It is prepared as a 512 × 512, 32-bit sRGB PNG and kept below the Google Play 1 MB icon limit. The Play Store applies its own corner mask and outer shadow, so the store asset is full-square rather than pre-rounded.

## Security

See [SECURITY.md](SECURITY.md) for security guidance.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Do not include any real resident or financial data in issues, pull requests, screenshots, fixtures, or test files.
