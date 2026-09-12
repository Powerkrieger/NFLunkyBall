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
| `persistence` | On-device stores: the organizer's single in-progress tournament, the viewer's saved/downloaded tournaments, per-match drinks, app settings. |
| `ble` | Connectionless Bluetooth LE live sync: chunked/gzipped tournament state over advertising, emoji reactions back, room codes. Pure codec parts are unit-tested. |
| `server` | Backend client (`ServerApi`), its wire DTOs (`ServerModels`), Ed25519 request signing, invite/upload payloads, encrypted credential storage. |
| `qr` | Join-code payload and QR rendering. |
| `ui` | Compose screens, split into `organizer/`, `viewer/`, `shared/` widgets and `theme/`. The two `AndroidViewModel`s (`OrganizerViewModel`, `ViewerViewModel`) own all state and wire the layers above together; `MainActivity` holds the single navigation graph (`Routes`). |

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
