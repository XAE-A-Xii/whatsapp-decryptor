# WA Decryptor: property listings from your own WhatsApp groups

An Android app that decrypts your own WhatsApp backup on your phone and extracts property listings from your WhatsApp group chats into a deduplicated CSV inventory.

> **Privacy first.** This app only decrypts **your own** WhatsApp backup, using **your own** 64-digit end-to-end encrypted backup key, **on your own device**. The app declares no `INTERNET` permission and contains no networking code, so it cannot send your data anywhere. Files leave the app only when you export them and choose a destination yourself through the Android share sheet.

## The problem

Property dealers share listings in WhatsApp groups as free-text messages. The same flat is often reposted many times, by different dealers, in different formats. Turning hundreds of groups of these messages into a usable inventory (project, location, BHK, size, price, rent, contact) is slow, repetitive manual work.

## Approach: manual first

Before writing any code, I did the whole process by hand to learn the existing workflow: reading the groups, pulling out the fields that matter, standardising project names and spotting reposts. The extraction rules, project registry and dedup logic in this repo encode that manual workflow.

## Features

- **On-device Crypt15 decryption.** Reads a `msgstore.db.crypt15` backup picked with the Android file picker. Derives the AES-256-GCM key from your 64-digit hex key with a clean-room RFC 5869 HKDF (HMAC-SHA256), parses the protobuf header, and decrypts with Bouncy Castle.
- **Chunked, constant-memory decryption.** Streams the backup in 64 KB chunks through AES-GCM and zlib inflate, then verifies the MD5 checksum and the SQLite header. Handles single-file and multi-file trailers. Runs in a foreground service with progress reporting, and checks for free space (2.5x the backup size) before starting.
- **Read-only database access.** The decrypted database is opened read-only (`SQLiteDatabase.OPEN_READONLY` on Android, `setReadOnly(true)` over JDBC). A schema detector checks for the modern `chat`, `message` and `jid` tables before reading.
- **Listing extraction.** Rule-based (regular expressions, not machine learning). Reads text messages from active group chats and extracts date, time, posted by, deal type, property type, project or society, location, BHK, size, sale price (Cr), rent per month, rate per sq ft, contact number, times posted and the full message.
- **Project registry.** Known projects are marked "IN" and everything else "OUT". You can add your own societies with aliases, promote an OUT society to IN, and edit, delete or manually add listings.
- **Deduplication.** Per-run SQLite consolidation on disk. The earliest exact repost of the same normalised dealer and message is kept, and older distinct ads for the same society and dealer are marked as duplicates, using timestamps and stable tie-breaking.
- **CSV export.** Master inventory CSV for the last 1 month (default) or 3 months, counted from the first day of the month N months before the latest message. Also per-project CSVs and a ZIP of all project CSVs. The master CSV is streamed to a staging file that replaces the shareable export only when the write finishes.
- **Caching.** A persistent parsed-text cache (`noBackupFilesDir/inventory_cache/parsed-text.db`, up to 256 entries held in memory) avoids re-parsing messages between runs.
- **Tests.** 39 JUnit 5 tests in `core-crypto`, including an end-to-end decryption test against a sample backup fixture.
- **CI releases.** GitHub Actions builds a debug APK on every push to `main`/`master` and publishes it as a GitHub release.

## Architecture

```
app/          Android app (Kotlin, Jetpack Compose, Material 3)
              UI, foreground decryption service, key storage, exports and share sheet
core-crypto/  Kotlin/JVM library, no Android dependencies
              crypto/     HKDF, protobuf header parser, streaming Crypt15 decryptor
              database/   schema detection and read-only adapter for the modern schema
              inventory/  listing extractor, project registry, streaming dedup store
              export/     TXT and JSON chat exporters
```

- The decryption and extraction logic lives in `core-crypto`, so it can be tested on the JVM and run from the command line without a phone.
- The Android app wraps `core-crypto` with a Compose UI, a foreground service for long decryptions and the Android share sheet for exports.
- Before this app, the extraction was done with a separate Python script. That script is **not** part of this repository, and the app's fields are not guaranteed to match its output (see `INVENTORY_EXPORT.md`).

## Build and run

### Requirements

- JDK 21
- Android SDK with API 35 (the app targets API 35; minimum supported version is Android 11, API 30)

### Android app

```bash
./gradlew :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Or download the latest debug APK from the Releases page.

On the phone:

1. In WhatsApp, turn on the end-to-end encrypted backup and note your 64-digit key (WhatsApp > Settings > Chats > Chat backup > End-to-end encrypted backup).
2. Open the app, pick `msgstore.db.crypt15` (usually in `WhatsApp/Databases`) and enter your key.
3. Once decryption finishes, open the inventory screens and export the CSV you need.

### Command line (JVM, no phone)

Both runners use the **parent directory** of the project as their working directory.

```bash
# Put msgstore.db.crypt15 and backup_key.txt (your 64-digit key) in the parent directory, then:
./gradlew :core-crypto:runDecrypt     # writes msgstore_decrypted.db
./gradlew :core-crypto:runInventory   # writes Master_Important_Dealer_Inventory_test.csv (last 3 months)
```

## Tests

```bash
./gradlew :core-crypto:test
# Full check used during development:
./gradlew :core-crypto:test :app:assembleDebug
```

The 39 tests cover HKDF, protobuf header parsing, key validation, streaming decryption (against `sample_backup.crypt15` and `sample_expected.db`), the schema adapter, listing extraction, the streaming dedup store and chat export.

If you change the extraction rules, bump `StreamingInventoryStore.CACHE_VERSION` so stale cached results are discarded.

## Privacy and security

- **No network access.** No `INTERNET` permission is declared, and there are no HTTP, socket or networking libraries in the code. The only permissions are `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_DATA_SYNC` (for decryption progress).
- **Your data stays in the app sandbox.** The decrypted database is written to app-private storage (`noBackupFilesDir`). App backup is disabled (`allowBackup="false"`), and data-extraction rules exclude everything from cloud backup and device transfer.
- **Exports are user-initiated.** CSV and ZIP files are shared through the Android share sheet via a `FileProvider`. You choose where they go.
- **Optional key storage.** "Remember Key" encrypts your key with an AES-256-GCM key held in the Android Keystore. "Forget Saved Key" removes it.
- **Privacy screen.** `FLAG_SECURE` is on by default to block screenshots and the recent-apps preview. It can be turned off in Settings.
- **Purge.** "Purge Decrypted Data" deletes the decrypted database and the parsed-text cache.

## Limitations

- Only the modern WhatsApp database schema (`chat`, `message`, `jid`) is supported. Older `messages` schemas are detected but not read.
- Only text messages from group chats are processed. Images, voice notes and documents are ignored.
- Extraction is rule-based and tuned to the Indian real-estate market (project names such as DLF, M3M, Sobha and Godrej, BHK, prices in crore). Other markets would need new rules.
- The decrypted database stays on the device until you purge it.
- CI publishes debug builds only.
- No licence has been chosen yet.
- Use this only on backups and chats you are entitled to access.

## How this was built

This project was built with AI coding assistance. My focus was the system design: the manual-first workflow, the on-device and no-network privacy model, the split between the Android app and the testable `core-crypto` library, streaming decryption, read-only data access, the dedup rules and the test and CI setup.
