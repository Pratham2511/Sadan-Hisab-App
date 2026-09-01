# Sadan

![Build Status](https://img.shields.io/badge/build-passing-brightgreen)
![Platform](https://img.shields.io/badge/platform-Android-blue)
![License](https://img.shields.io/badge/license-MIT-green)

A native Android application for managing the Pansare Sadan property securely and entirely offline.

## Features

| Feature | Description |
|---|---|
| **Offline-First** | Runs entirely on the device. No internet required. |
| **Tenant Management** | Track 48 rooms across A and B wings effortlessly. |
| **Rent Ledger** | A precise month-wise ledger for each tenant that respects historical rent changes. |
| **Payment Allocations** | Accurate handling of partial, full, and over-payments automatically mapped to unpaid months. |
| **Defaulter Tracking** | Automatically generated defaulter list based on actual unpaid monthly balances. |
| **PDF Receipts** | Automatically generate and share rent receipts via the Android Sharesheet. |
| **Backup & Restore** | AES-GCM encrypted backups ensuring absolute data privacy. |

## Getting Started

1. Download the latest APK from the [Releases](https://github.com/Pratham2511/Sadan-Hisab-App/releases) page.
2. Install the APK on your Android device.

## Usage

- **Dashboard:** At-a-glance view of total outstanding balances and defaulters.
- **Rooms:** Browse wings and view individual tenant ledgers.
- **Record Payment:** Navigate to a tenant and click `Record Payment`. Payments are allocated chronologically.
- **Reports:** Generate Monthly and Yearly collection summaries.

## Configuration
Requires an Android device running Android 7.0 (API Level 24) or higher.

## Development
- Clone the repository: `git clone https://github.com/Pratham2511/Sadan-Hisab-App.git`
- Open the project in Android Studio.
- Run the Gradle sync and hit play.

## Architecture
See [docs/architecture.md](docs/architecture.md) for a detailed overview of our Compose UI, Jetpack Room DB, and Ledger Engine.

## Privacy & Data
- The app is strictly **offline-first**.
- Production data is stored locally and securely.
- **Public GitHub source contains only synthetic development/demo data.**
- Real tenant data is explicitly prohibited from being stored in the source code or committed to GitHub.

## Backup & Restore
Backups are encrypted locally using AES-GCM and PBKDF2 before export. See [docs/backup-restore.md](docs/backup-restore.md).

## Testing
Unit tests cover all historical rent edge cases and Ledger Engine functionality. See [docs/testing.md](docs/testing.md).

## Contributing
See [CONTRIBUTING.md](CONTRIBUTING.md) for details on submitting pull requests.

## License
MIT License
