# Security and Privacy Policy

## Reporting a Vulnerability
If you discover a security vulnerability or a privacy leak within this project, please send an email to the repository maintainer privately. 
Do not expose vulnerabilities or privacy leaks publicly in GitHub issues.

## Privacy Assurances
- **No Custom Backend:** The app is completely offline. No tracking SDKs or remote servers are utilized.
- **Local DB:** The Room database acts as the single source of truth and never leaves the device unless explicitly exported via the encrypted Backup mechanism.
- **Backups:** Backup files are encrypted with AES-256-GCM before leaving the app and are written only to a location the user picks via the Storage Access Framework. The app has no network permission and cannot upload anything itself.
- **No PII in the repository:** No real tenant name, mobile number or address appears in the source, tests, docs or seed paths. A fresh install contains 48 rooms and zero tenants.
