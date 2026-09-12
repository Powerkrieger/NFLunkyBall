# NFLunkyBall

Android app (Kotlin + Jetpack Compose) for running Flunkyball tournaments: an organizer sets up
groups, records match results and (optionally) uploads the finished tournament to the
[NFLunkyBallServer](../NFLunkyBallServer) history/Elo backend; viewers follow live scores on their
own phones and browse history, leaderboards and player stats.

## Layout

Single Gradle module (`app`), package `com.example.nflunkyball`:

| Package | Contents |
| --- | --- |
| `model` | Pure domain types (`Tournament`, `Team`, `Group`, `Match`, `MatchDrinks`) and pure logic (round-robin generation, standings, provisional Elo, group seeding). No Android dependencies — fully unit-tested. |
| `persistence` | On-device stores (all via `JsonFile`, written off the main thread): the organizer's single in-progress tournament, the viewer's saved/downloaded tournaments (`ViewerLibrary` holds the rules), per-match drinks, pending finish info, app settings. |
| `ble` | Connectionless Bluetooth LE transport: chunked/gzipped tournament state over advertising, emoji reactions back, room codes. `LiveBroadcaster`/`LiveReceiver` are the interfaces the sync layer uses. |
| `server` | Backend client (`ServerApi` interface, `KtorServerApi`), wire DTOs (`ServerModels`), Ed25519 request signing (`UploadSigner`), `AccountManager` (invite linking), `ArchiveUploader` (finish → upload with retry), `KeystoreCredentialsStore` (credentials encrypted under an Android Keystore key). |
| `sync` | Live sync in both roles — `LiveSyncHost` (organizer) and `LiveSyncViewer` (viewer) pick BLE or server per the Settings toggle. |
| `qr` | Join-code payload and QR rendering. |
| `ui` | Compose screens (`organizer/`, `viewer/`, `shared/`, `theme/`) and three thin ViewModels (`OrganizerViewModel`, `ViewerViewModel`, `SettingsViewModel`) that delegate to the classes above. `MainActivity` holds the single navigation graph (`Routes`). |

Dependencies are hand-wired in `AppContainer` (held by `NfLunkyBallApplication`) — no DI framework.
Everything below the ViewModels runs on the JVM, so `app/src/test` covers it with fakes
(`fakes/`) rather than instrumentation.

Live sync has two transports, chosen in Settings: server-backed (default, needs internet) or
peer-to-peer BLE (offline venues). Drink choices and finish metadata are deliberately kept out of
the synced `Tournament` model and only leave the device in the final archive upload.

## Building

Standard Gradle/Android Studio project — open it in Android Studio, or build from the CLI:

```
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

## Release builds & CI

`.github/workflows/build.yml` runs the unit tests and builds a signed release APK on every push to
`main` and on version tags (`v*`), attaching the APK to a GitHub Release for tagged builds. Signing
reads from environment variables (`KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`,
`KEY_PASSWORD`), populated in CI from repo secrets. Same setup as `QuickMusicQuiz` and
`AmelieMusikster`.

To build a signed release locally, set those four environment variables (pointing
`KEYSTORE_PATH` at your own `.jks` file) and run `./gradlew assembleRelease`.

## AI notice

Project scaffolding, CI pipeline and large parts of the app were built with Claude Code.
