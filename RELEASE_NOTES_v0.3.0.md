# Pansare Sadan v0.3.0

This release focuses on spreadsheet import reliability, receipt sharing, UI cleanup, and the refreshed Pansare Sadan branding.

## Highlights

- Reworked XLSX importing for the legacy workbook format used by Pansare Sadan.
- Removed hardcoded spreadsheet row/column limits.
- Improved parsing of historical receipt text, mixed date formats, payment modes, and multiple payments in one cell.
- Added safer preflight handling for vacant rooms and unmatched tenant records.
- Fixed receipt PDF sharing/FileProvider issues.
- Added one-tap WhatsApp / WhatsApp Business receipt sharing.
- Fixed large spacing gaps on the Rooms screen.
- Switched the app to a single Solarized-light-inspired theme regardless of system dark mode.
- Replaced old branding assets with the new Pansare Sadan app icon and Play Store asset.
- Updated public documentation with stronger privacy guidance and no production tenant data.

## APK

Attach the finished APK to this release as:

`Pansare-Sadan-v0.3.0.apk`

## Notes

- The app remains offline-first with local Room/SQLite storage.
- The importer validates before writing and does not silently invent missing accounting history.
- No automatic UPI/SMS transaction reading is included in this release.
