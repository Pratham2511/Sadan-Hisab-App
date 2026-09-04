# Backup and restore

Local, encrypted, user-driven. Nothing is uploaded anywhere by the app.

## Format

Files carry the extension `.sadanbackup` and are written through the Storage Access
Framework, so the user chooses the destination (device storage, an SD card, or a cloud
folder mounted as a document provider — the app itself never talks to a network).

Layout produced by `util/BackupCrypto.kt`:

```
magic "PSB1" | version | salt (random) | IV (random) | AES-GCM ciphertext+tag
```

- Key derivation: PBKDF2-HMAC-SHA256, 210,000 iterations, random per-file salt.
- Encryption: AES-256-GCM with a random per-file IV; the GCM tag authenticates the payload.
- Minimum password length is 8 characters, confirmed twice in the UI.

Because salt and IV are random per export, two backups of identical data are never
byte-identical.

## Payload

`BackupPayload` version 2 is a JSON snapshot of every table — rooms, tenants, rent changes,
ledger, payments, allocations, import issues and settings — plus the schema version and a
timestamp. The database file itself is not copied, so a restore cannot smuggle in a foreign
SQLite file.

## Restore

1. The user picks a file via SAF and enters the password.
2. `validate` checks the magic bytes, version and GCM tag **before** anything is written.
   - A wrong password fails tag verification and is reported as *incorrect password*.
   - A truncated or edited file fails the same check and is reported as *corrupted*.
   - A newer payload version is refused rather than partially read.
3. Only after validation succeeds is the restore performed, in a single transaction. If any
   part fails, the transaction rolls back and the existing data is untouched.

Restore replaces current data, so the UI requires an explicit confirmation that spells out
what will be overwritten. Validation happening entirely before the write is what makes a
failed restore non-destructive.
