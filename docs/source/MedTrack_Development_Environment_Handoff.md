# MedTrack — Development Environment & Project Handoff

**Status as of:** 20 October 2026  
**Platform:** macOS 26.5.1, Apple Silicon (`darwin-arm64` / `aarch64`)  
**Purpose:** Give a coding AI an accurate picture of the user's existing machine setup, what has actually been verified, what is not yet implemented, and what to do next.

> **Primary specification:** `MedTrack_SRS_Technical_Implementation_Guide.pages` (version 1.0, 20 October 2026). Supply this file alongside this handoff. **The SRS is the single source of truth for product requirements, APIs, business rules, phase gates, and acceptance criteria.** This handoff is specifically a *local environment and progress report*; it does not replace the SRS.

## 1. Project summary

MedTrack is a medication tracking mobile app, initially targeting Android, with iOS support later. Its proposed architecture is:

- **Client:** Flutter/Dart; local notifications, daily dose views, inventory, history, and eventually offline local store + operation queue.
- **Server:** Java 21, Spring Boot, Spring Data JPA, REST API, authentication/authorization, idempotent dose actions.
- **Data:** PostgreSQL, version-controlled schema migrations via Flyway.
- **Local development:** Docker Desktop / Docker Compose for PostgreSQL; Android Studio SDK and emulator for device tests.
- **Safety scope:** No automatic medication or dosage advice, emergency alert guarantees, or medical-device claims. Do not use real personal health data in local demos.

Per SRS: `TAKEN` or `SKIPPED` must be triggered by an explicit user action; do not deduct inventory twice on retries; preserve historical events across schedule revisions; all account data must be scoped to its owner. The SRS's MUST/SHOULD/COULD classification takes precedence over guesses.

## 2. Verified local setup

These statements come from the actual installation/test commands shown during setup, **not assumptions that a MedTrack app already exists**.

| Tool / service | Observed version or detail | Verified condition |
|---|---|---|
| macOS | 26.5.1, Apple Silicon ARM64 | Flutter doctor and Maven output |
| VS Code | Installed and working | Python project opened and run; `code .` shell command enabled |
| Google Antigravity | Installed | GUI installed; not tested for MedTrack |
| Git | Apple Git 2.50.1 | `git --version` worked |
| Git identity | Global `user.name` and `user.email` configured | `git config --global --list` worked; GitHub sign-in done through VS Code |
| Homebrew | Installed | Used for Maven / other developer utilities |
| Java JDK | Temurin OpenJDK **21.0.12.1**, arm64 | `java -version` worked |
| Maven | **3.10.0** | `mvn -version` worked, using Java 21 after `JAVA_HOME` export |
| Docker CLI | **29.9.1** | `docker --version` worked |
| Docker Compose | **v5.6.0** | `docker compose version` worked |
| Docker Desktop | 4.96.0, server Docker 29.9.1 | `docker info` returned client **and** server information |
| Flutter SDK | **3.47.7 stable** | `flutter --version`, `flutter doctor -v` worked |
| Dart SDK | **3.13.5** | `dart --version` worked |
| Android Studio | Installed | Setup Wizard completed |
| Android SDK | `~/Library/Android/sdk` | Flutter doctor reports Android toolchain **✓** |
| Android SDK platform | Android 37.0; Build-Tools 36.0.0 | Installed |
| Android Emulator | **37.2.12.0** | Installed and booted |
| AVD | `Medium_Phone_API_37.0` | Created and listed by `flutter emulators` |
| Android emulator device | `emulator-5554`, Android 17 (API 37), arm64 | **Detected** by `flutter devices` |
| Android SDK licences | Accepted | `flutter doctor -v` reports all accepted |
| Flutter VS Code extension | Installed | Extension configured; SDK later installed |
| Python | 3.15.0, with test `.venv` | Unrelated to main MedTrack stack |
| Ollama | 0.40.6 | Installed for separate local LLM work, **not part of MedTrack MVP** |

### Known local paths

```text
/Users/pelinsaglamer/Developer/flutter       # Flutter SDK — DO NOT treat as MedTrack project
/Users/pelinsaglamer/Developer/python-test   # unrelated Python test project
/Users/pelinsaglamer/Library/Android/sdk     # Android SDK
/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home
```

`~/Developer` already exists and is suitable for a future `~/Developer/MedTrack` project directory. **The actual MedTrack repository/scaffolding is NOT yet created or confirmed.**

### Java environment details

Initially `java -version` returned Java 21 while Maven used Homebrew's Java 27. This was corrected by setting:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```

The user then appended the following to `~/.zshrc` and sourced the file:

```bash
echo 'export JAVA_HOME=$(/usr/libexec/java_home -v 21)' >> ~/.zshrc
source ~/.zshrc
```

Subsequent `mvn -version` displayed Java **21.0.12.1**. Recheck in a *fresh shell* when starting backend work. Flutter doctor may report Android Studio's **bundled Java 25** for its Android toolchain; that is a distinct JDK, and should not be confused with the backend's JDK 21.

### Docker environment details

The Docker app initially worked but `docker` was absent from shell PATH. Docker Desktop CLI installation settings were adjusted; afterwards **`docker --version`, `docker compose version`, and `docker info` worked**. Docker Desktop's Linux engine was running (`desktop-linux`, `aarch64`). One **Docker welcome/example container** was running at the time of the test. **No MedTrack PostgreSQL database/container has been created yet.** The reported Docker VM memory was approximately 7.7 GiB.

### Android environment details

The Android Studio setup initially failed downloading `Google Play ARM 64 v8a System Image API 37.0` due to `Connection reset` / `Premature EOF`. Retrying succeeded. The command-line tools were initially absent, causing `flutter doctor --android-licenses` to fail with `sdkmanager not found`. After installing Command-line Tools, `flutter doctor --android-licenses` reported the deprecated option is unnecessary and `flutter doctor -v` confirmed Android licenses accepted and Android toolchain ✓.

Emulator started and `flutter devices` returned:

```text
sdk gphone64 arm64 (mobile) • emulator-5554 • android-arm64 • Android 17 (API 37) (emulator)
macOS (desktop)             • macos         • darwin-arm64
```

`flutter emulators` returned `Medium_Phone_API_37.0`. **This proves Flutter discovers the emulator; it does NOT yet prove any Flutter app has built or run successfully.**

### Current Flutter doctor warnings (non-blocking for Android-first plan)

- **Full Xcode incomplete:** needed when starting iOS/macOS native app development; CocoaPods also absent.
- **Google Chrome not installed:** needed only if choosing Chrome for Flutter web debugging; not required for Android MVP.

Do not install Xcode, CocoaPods, or Chrome merely to make every doctor item green unless the next approved phase actually needs them.

## 3. Explicitly NOT done yet

1. **The temporary Flutter sample app was NOT created or run.** The previous suggestion was to run `flutter create flutter_test_app` then `flutter run -d emulator-5554`; the user explicitly deferred this. Do not state the full build pipeline has passed.
2. **MedTrack codebase/repository does not yet exist as a verified local project.** No Flutter `mobile/` app, Spring Boot `backend/` app, Docker Compose project, database migration, tests, or CI pipeline have been generated.
3. **No MedTrack PostgreSQL container/database** started; only an unrelated Docker sample container was observed.
4. **No MedTrack-specific Flutter packages** (e.g. Riverpod, Drift, notification packages) installed yet.
5. **No backend API, JWT auth, schemas, notifications, inventory, or sync implementation** exists yet.
6. **No Android APK for MedTrack** has been built or tested.
7. **No real-device notification testing** performed.
8. **No GitHub repository/push for MedTrack** has been established by this setup conversation; general GitHub authentication is configured.

## 4. Next steps — recommended order

### Next session: environment smoke test (optional, deferred by user)

Do **not** automatically start this now without the user's approval. When the user is ready:

```bash
cd ~/Developer
flutter create flutter_test_app
cd flutter_test_app
code .
flutter devices
flutter run -d emulator-5554
```

Success criteria: starter Flutter app builds, launches on emulator, and button interactions work. Treat this as disposable **test-only** code, not MedTrack functionality. First Android Gradle build may take several minutes.

### Begin actual MedTrack work only with user consent

Follow the SRS roadmap strictly, one phase at a time:

- **Phase 0:** Confirm outstanding decisions and repository structure, initialize Git repo, README, `docs/ADR.md`, basic CI plan. The SRS is the product authority.
- **Phase 1:** Scaffold Spring Boot (Java 21/Maven); PostgreSQL via Docker Compose; Flyway migrations; health endpoint; exception handling; tests.
- **Phase 2:** Registration, login, refresh token rotation, per-user authorization.
- **Phase 3:** Medication CRUD, validation, archiving, tests, OpenAPI.
- **Phase 4:** Schedule versioning and dose occurrence generation with timezone/DST cases.
- **Phase 5:** Dose actions and append-only inventory ledger, idempotency/transaction and replay tests.
- **Phase 6:** Flutter app scaffolding, routing, secure auth state, typed API client; decide Riverpod.
- **Phase 7:** Mobile medication, today, dose, stock, and history UI, including error/empty states.
- **Phase 8:** Local notifications and permission logic; real Android device tests.
- **Phase 9:** Offline SQLite/Drift, operation queue and conflict/replay handling.
- **Phase 10:** Privacy, account deletion, logging redaction, accessibility, security and acceptance regression.
- **Phase 11:** Documentation, reproducible setup, build artifacts, demo and portfolio.

**Important:** The SRS proposes Riverpod, Drift/SQLite, Maven, and Android-first rollout; choices identified as *open decisions* must be explicitly confirmed before committing to them. Avoid unapproved scope or dependencies.

### Suggested repository structure (from SRS appendix; not yet created)

```text
MedTrack/
  README.md
  docs/
    SRS.md
    ADR.md
    api-contract.md
    test-plan.md
  backend/
    pom.xml
    src/main/java/.../
    src/main/resources/db/migration/
    src/test/
  mobile/
    lib/
    test/
    integration_test/
  infrastructure/
    docker-compose.yml
  .github/workflows/
```

Use separate Java and Flutter build environments. Do not place the MedTrack app inside `~/Developer/flutter`, because that folder contains the SDK itself.

## 5. Commands for the coding AI to verify, before any change

```bash
uname -m
flutter --version
dart --version
flutter doctor -v
flutter devices
flutter emulators
java -version
echo "$JAVA_HOME"
mvn -version
docker --version
docker compose version
docker info
git --version
```

Commands may produce different results if Android Emulator or Docker Desktop is currently stopped. Starting a background service or creating files should be an explicit, explained action.

## 6. Coding AI operating instructions

1. Read the **complete MedTrack SRS** before implementing. Keep requirements and MUST acceptance criteria traceable to the spec.
2. Treat this handoff as a snapshot of the **toolchain only**, not evidence that app features exist.
3. Ask before scaffolding the real MedTrack project or performing installations/migrations. User prefers **step-by-step guidance with explanations** of what a command does.
4. Before each phase, state goal, files to create/change, dependencies, commands, expected outputs, and acceptance tests.
5. Execute one scoped phase at a time; do not implement all phases at once.
6. Never introduce real health records, guessed dosage recommendations, medically guaranteed notifications, or dangerous assumptions about skipped doses.
7. Use UUIDs, server-side authorization, strict DTO validation, transactional ledger updates and idempotency as specified in the SRS.
8. Prefer fixtures with fictional users and medications; do not commit secrets, personal credentials, tokens, or real health information.
9. Provide a brief end-of-phase report: what changed, commands run, test results, known issues, and next step.

## 7. Suggested AI handoff prompt

> I am building MedTrack on an Apple Silicon Mac. Read the attached `MedTrack_SRS_Technical_Implementation_Guide.pages` and this `MedTrack_Development_Environment_Handoff.md` in full. The SRS is the primary specification. The toolchain is installed and the Android emulator is detected, but **no Flutter test app has been run** and the real MedTrack repository/application has **not yet** been scaffolded. First summarize the current state and outstanding specification decisions. Do not write code or make filesystem changes until I approve Phase 0. Then guide me through one small, explained step at a time. Maintain the SRS's phase order and verify outcomes with actual commands and tests.
