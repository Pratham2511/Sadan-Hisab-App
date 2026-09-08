# Changelog

## [0.3.0] - 2026-09-09
### Added
- New Pansare Sadan launcher branding and Play Store icon asset
- One-tap WhatsApp / WhatsApp Business receipt sharing
- More flexible XLSX import parsing for legacy workbook layouts
- Dynamic row and column handling without hardcoded spreadsheet limits
- Safer preflight review for vacant rooms and unmatched tenant records
- Solarized-light-inspired single app theme

### Fixed
- XLSX imports incorrectly treating years and totals as payment amounts
- Legacy receipt strings containing multiple historical payments
- FileProvider receipt sharing path errors
- Large spacing gaps on the Rooms screen
- WhatsApp receipt sharing type inference conflict

### Changed
- Import validation now surfaces reviewable rows before commit
- README and public repository documentation were refreshed with stricter privacy guidance
- Old logo assets were removed and replaced with the new Pansare Sadan identity

## [0.2.0] - 2026-09-04
### Added
- XLSX import with multi-sheet support
- Expanded accounting and payment allocation features
- Improved reports and PDF receipt handling

## [0.1.0] - 2026-09-02
### Added
- Offline-first rent management
- Tenant master/profile management
- Month-wise rent ledger displaying UNPAID, PARTIALLY_PAID, and PAID states
- Payment entry with automatic allocation
- Historical rent tracking
- Defaulter and outstanding logic calculation
- PDF Receipts generated dynamically
- Backup & Restore via Android SAF
- Dynamic Dashboard and Reports
