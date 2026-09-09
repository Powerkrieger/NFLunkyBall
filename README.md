# NFLunkyBall

A Kotlin + Jetpack Compose Android app. Freshly scaffolded, no features yet.

## Building

Standard Gradle/Android Studio project — open it in Android Studio, or build from the CLI:

```
./gradlew assembleDebug
```

## Release builds & CI

`.github/workflows/build.yml` builds a signed release APK on every push to `main` and on
version tags (`v*`), and attaches the APK to a GitHub Release for tagged builds. Signing reads
from environment variables (`KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`),
populated in CI from repo secrets. Same setup as `QuickMusicQuiz` and `AmelieMusikster`.

To build a signed release locally, set those four environment variables (pointing
`KEYSTORE_PATH` at your own `.jks` file) and run `./gradlew assembleRelease`.

## AI notice

Project scaffolding and CI pipeline set up with Claude Code.
