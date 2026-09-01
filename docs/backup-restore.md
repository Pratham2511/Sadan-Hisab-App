# Backup and Restore

To ensure data permanence without exposing tenant PII to a web backend, Sadan utilizes encrypted local backups.

## Process
- **Export:** A snapshot of the Room database is captured. It is encrypted in-memory using AES-GCM, with a key derived from the user's password via PBKDF2.
- **Storage:** The resulting `.psbackup` file is stored safely utilizing the Android Storage Access Framework (SAF). The user can place it in their local files or their Google Drive.
- **Restore:** The user provides the password, decrypting the SAF-loaded file in-memory and replacing the local Room database upon validation.
