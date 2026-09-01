# Security and Privacy Policy

## Reporting a Vulnerability
If you discover a security vulnerability or a privacy leak within this project, please send an email to the repository maintainer privately. 
Do not expose vulnerabilities or privacy leaks publicly in GitHub issues.

## Privacy Assurances
- **No Custom Backend:** The app is completely offline. No tracking SDKs or remote servers are utilized.
- **Local DB:** The Room database acts as the single source of truth and never leaves the device unless explicitly exported via the encrypted Backup mechanism.
- **Backups:** Google Drive (when configured) is used purely as an encrypted backup sink, not a live datastore.
