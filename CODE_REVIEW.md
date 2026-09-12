# Code review — structure, architecture, conventions, modularity

Scope: the whole `app` module at v0.9.1 (~6.9k lines of Kotlin across 70 files), plus build/CI
config. Reviewed for structure, architecture, conventions and modularity — not for feature
correctness, though a few real bugs surfaced along the way and are listed. Each finding says
whether it was **fixed in this pass** or is **left open** (and why).

## Overall assessment

The codebase is in good shape for its size. Package boundaries are sensible and mostly respected
(`model` is Android-free and unit-tested; `ble`/`server`/`persistence`/`qr` are cohesive), domain
types are immutable data classes, and the *why* behind non-obvious decisions is documented inline
unusually well. Test coverage is focused on the pure parts (codecs, logic, payloads), which is the
right place for it.

The main structural weakness is that all state and orchestration lives in two large
`AndroidViewModel`s that construct their own dependencies, so nothing between the UI and the pure
model layer is testable off-device, and UI screens reach across feature boundaries to grab whole
ViewModels. The rest is the usual accumulation of duplication and dead code in a fast-moving app.

---

## 1. Architecture

### 1.1 Two "god" ViewModels with hard-wired dependencies — *fixed (v0.10.0–v0.10.4)*
`OrganizerViewModel` (~470 lines) and `ViewerViewModel` (~310 lines) each instantiate their
repositories, credential stores, BLE broadcaster/receiver and `ServerApi` directly, and every
screen takes the whole ViewModel. Consequences:

- Nothing in the ViewModel layer can be unit-tested (needs an `Application`, EncryptedSharedPrefs,
  a `BluetoothAdapter`).
- The organizer ViewModel mixes at least five concerns: tournament editing, BLE hosting, server live
  sync, account linking, archive upload.

**Fixed** in five releases (plan: `~/.claude/plans/purring-foraging-backus.md`):
- `model/TournamentEdits.kt` — every organizer edit is a pure `Tournament` extension (`TournamentEditsTest`).
- `AppContainer` on `NfLunkyBallApplication` hand-wires one instance of everything and exposes the
  ViewModel factory; interfaces only where the real thing can't run on the JVM (`AppSettings`,
  `CredentialsStore`, `ServerApi`/`KtorServerApi`, `LiveBroadcaster`, `LiveReceiver`); file stores
  take a `File` and are tested for real (`FileStoresTest`).
- `server/AccountManager` (link/unlink/status — shared by organizer flow and Settings),
  `server/ArchiveUploader` (finish/retry/discard), `sync/LiveSyncHost` + `sync/LiveSyncViewer`
  (transport choice, BLE or server; the host follows the account flow so unlinking anywhere stops
  pushes), `persistence/ViewerLibrary` (saved-list rules), `ui/SettingsViewModel`.
- Both ViewModels are now ~190-line facades; every extracted class has JVM tests with
  `FakeServerApi`/`FakeCredentialsStore`/`FakeBroadcaster`/`FakeReceiver`/`FakeAppSettings`
  (`app/src/test/.../fakes`). 103 unit tests total.
- Found along the way: the "Uploading…" guard was set inside the coroutine, so a double-tap on
  Retry could queue two uploads — fixed in `ArchiveUploader`.

### 1.2 UI screens depend on ViewModels from other features — *fixed*
- `ui.viewer.HistoryScreen` took an `OrganizerViewModel` just to read the hosted tournament.
  **Fixed:** it now takes `hostedTournament: Tournament?`, passed from the nav graph.
- `ui.SettingsScreen` depended on `OrganizerViewModel` and `ui.organizer.AccountSyncStatus`.
  **Fixed (v0.10.4):** it has its own `SettingsViewModel` over the shared `AccountManager`;
  `ui.viewer`/`ui` no longer import `ui.organizer`.

### 1.3 Persistence-layer type used as a domain type — *fixed*
`MatchDrinks` lived in `persistence` but was imported by `server.UploadPayload` and
`ui.shared.MatchResultDialog`. Moved to `model.MatchDrinks` (JSON shape unchanged, existing
`match_drinks.json` files still decode).

### 1.4 Pure logic living in a Compose file — *fixed*
`mergeLiveStandings` in `HistoryScreen.kt` was untested domain logic. Moved to
`server/LiveLeaderboard.kt` as `List<CompetitorStats>.withLiveStandings(...)` with a unit test.
(It lives in `server` rather than `model` because it combines the backend's `CompetitorStats` with
the model's provisional standings; `model` must stay free of `server` imports.)

### 1.5 Duplicated request signing — *fixed*
`pushLiveState` and `uploadToHistory` each built the `"<id>|<ts>|<sha256>"` message, signed it and
base64-encoded it. Extracted to `server/UploadSigner` (uses `kotlin.io.encoding.Base64` instead of
`android.util.Base64` so it's JVM-testable — same reasoning as `InvitePayloadCodec`; output is
byte-identical to `Base64.NO_WRAP`). Covered by `UploadSignerTest` including a round-trip through
`Ed25519.verify`.

### 1.6 One `HttpClient` per `ServerApi` instance, never closed — *fixed*
`ServerApi(url)` was constructed per call site (a dozen places) and each one created a fresh Ktor
CIO engine with its own thread pool that was never `close()`d. Now a single lazily-created client is
shared process-wide; `ServerApi` remains a cheap per-URL wrapper.

### 1.7 Navigation routes as scattered string literals — *fixed*
`"viewer/player/$id"` etc. appeared in several places in `MainActivity`. Introduced a private
`Routes` object with constants for every destination and builder functions for the parametrised
ones. Zero string literals remain in the graph.

### 1.8 Redundant `ServerCredentialsStore` in composition — *fixed*
`NfLunkyBallApp` constructed a second `EncryptedSharedPreferences` just to compute
`isAlreadyLinked`; `OrganizerViewModel` had already loaded the same two values. Now derived from the
ViewModel.

### 1.9 Synchronous file I/O on the main thread — *fixed (v0.9.3)*
`TournamentRepository.update`, `ViewerTournamentsStore.upsert` and `MatchDrinkStore.set` write JSON
files synchronously from UI callbacks (every recorded score, every settings toggle). Files are small
so this was invisible, but StrictMode-hostile. **Fixed:** all stores now go through
`persistence/JsonFile`, which reads once synchronously at construction and queues writes/deletes on
a shared single-thread executor (strictly ordered, temp-file + rename so a kill mid-write can't
truncate the file). Store APIs are unchanged.

### 1.10 Upload failures are silent and the tournament is cleared regardless — *fixed (v0.9.3)*
`BracketScreen.onFinish` calls `uploadToHistory(...)` then immediately `clearTournament()` and
navigates home. `uploadStatus` is set by the upload but **never read by any screen**, so a failed
upload (offline, revoked account) lost the tournament with no feedback. This was the most
consequential finding in the review. **Fixed:** `finishAndUpload` marks the tournament FINISHED,
persists the finish-dialog answers (`FinishInfoStore`) and only clears the local copy once the
server confirms. Until then it appears in My tournaments as "Finished · not uploaded yet · <error>"
with Retry / Discard; linking an account while one is pending retries automatically; the
new-tournament guard explains the situation. "Finish without saving" (no account) still clears
immediately, as the organizer explicitly chose.

### 1.11 `ReactionsOverlay` is never composed — *fixed (v0.9.3)*
Viewers can send emoji reactions and the organizer's broadcaster receives them, but
`ReactionsOverlay` (the only consumer of `OrganizerViewModel.emojiEvents`) isn't placed on any
screen, so reactions were dropped. **Fixed:** it now floats bottom-right over both scoring
screens (`GroupStageScreen`, `BracketScreen`). Only ever shows anything in BLE mode — server mode
has no viewer→organizer channel.

---

## 2. Bugs found incidentally

| | Where | Status |
| --- | --- | --- |
| Each `startHosting()` in BLE mode launched a new collector on `broadcaster.emojiEvents` without cancelling the previous one. `HostingScreen` calls `startHosting()` every time it's shown, and linking an account mid-tournament calls it again — so every reaction would be re-emitted N times. | `OrganizerViewModel` | **Fixed** — tracked as `emojiBridgeJob`, cancelled on restart/`stopHosting`. |
| `onCleared()` didn't call `super.onCleared()` and didn't cancel the emoji bridge. | `OrganizerViewModel` | **Fixed** — now `super.onCleared(); stopHosting()`. |
| HttpClient leak (see 1.6). | `ServerApi` | **Fixed** |
| `ChunkedMessage.chunk` boxed every payload byte via `toList().chunked()` on a hot path (every broadcast version). | `ble` | **Fixed** — `copyOfRange` slices; existing `ChunkedMessageTest` still passes. |

---

## 3. Modularity & duplication

| Finding | Status |
| --- | --- |
| `ServerApi.kt` mixed 18 wire DTOs with the client class (312 lines). | **Fixed** — DTOs moved to `server/ServerModels.kt`. |
| Back-arrow `TopAppBar` copy-pasted in 7 screens. | **Fixed** — `ui/shared/BackTopBar`. |
| `TeamPicker` (BracketScreen) and `GroupDropdownField` (ManagePlayersScreen) were the same `ExposedDropdownMenuBox` widget. | **Fixed** — `ui/shared/DropdownField`; stale comment claiming BracketScreen still used a text-button menu removed with it. |
| Four copies of "resolve server URL, then read password" in `ViewerViewModel` (`historyAvailable`, `loadHistory`, `loadPlayerStats`, `withReadAccess`). | **Fixed** — single private `readAccess()` returning `(api, password)` or null. |
| `Json { … }` configured ad hoc in 12 places. | **Fixed (v0.10.5)** — `model/AppJson` with two named configs: `lenient` (disk/wire) and `compact` (BLE). |
| `String.format(Locale.US, "%.1f", …)` for Elo in 9 places. | **Fixed (v0.10.5)** — `ui/shared/Format.kt`: `Double.format1()` / `formatSigned1()`. |
| Detail screens fetched into local `remember` state while `PlayerStatsScreen` used VM state. | **Fixed (v0.10.5)** — all four loads (`history`, `playerStats`, `tournamentDetail`, `matchDetail`) live in `ViewerViewModel` as `StateFlow<LoadState<T>>`; the stats-mode lens reloads the open match too. |

---

## 4. Conventions & hygiene

| Finding | Status |
| --- | --- |
| Dead code: `BleCapability.canScan`, `OrganizerViewModel.canHost`, `ServerCredentialsStore.clear()`, scaffold `ExampleInstrumentedTest`. | **Fixed** — removed. (`Ed25519.verify` is only used by tests; kept deliberately as the round-trip check.) |
| `GroupStageScreen`/`BracketScreen`: the `Scaffold` body `Column` was un-indented one level. | **Fixed** |
| `TournamentBroadcaster` class KDoc was attached to `private const val TAG`. | **Fixed** |
| `PacketCodec` comment referred to a "12B" legacy chunk; the constant has been 18 for a while. | **Fixed** — references the constant instead. |
| `Math.pow` in Kotlin code. | **Fixed** — `kotlin.math.pow`. |
| `BleCapability` used fully-qualified `android.os.Build…` inline. | **Fixed** — imported. |
| Mixed state-holder idioms: ViewModels exposed some state as `StateFlow` and some as Compose `mutableStateOf`. | **Fixed (v0.10.5)** — ViewModels expose `StateFlow` only; screens `collectAsState()`. |
| User-facing status modelled as raw `String?`. | **Fixed (v0.10.5)** — `ui/LoadState<T>` (Idle/Loading/Loaded/Failed), `server/UploadState`, `sync/SyncState`; the display text lives in the screens (`SyncState.label()`, `PendingUploadRow`). |
| Every UI string is hard-coded in Kotlin (~330 literals); `strings.xml` holds only `app_name`. `RulebookScreen` is in German, everything else English. | *Open* — the one cosmetic item deliberately left: it's a large mechanical diff whose only payoff is translation, and it touches every screen. Do it when a second language is actually wanted. |
| `applicationId` / `namespace` are still `com.example.nflunkyball`. | *Open* — changing the application id makes existing installs a different app; only do it if you ever publish. |
| README described the project as "freshly scaffolded, no features yet". | **Fixed** — now has a package map and the two sync transports. |
| CI only ran `assembleRelease`; unit tests never ran on push. | **Fixed** — `testDebugUnitTest` step added before the build. |
| `.idea/deploymentTargetSelector.xml` and `.idea/vcs.xml` were untracked but not ignored. | **Fixed** — added to `.gitignore`. |
| Dependency versions: AGP 9.0.1 with Compose BOM 2024.09.00 and Kotlin 2.0.21. | *Open* — builds fine, but the BOM is a year behind the AGP; bumping it would let you drop several `@OptIn(ExperimentalMaterial3Api)` annotations and pick up `collectAsStateWithLifecycle`. |

---

## 5. Things that are good and worth keeping

- `model` has no Android imports and pure functions (`standings`, `provisionalPlayerStandings`,
  `assignGroupsBySeeding`) — keep it that way.
- The deliberate `UploadTournament` ≠ `Tournament` split so drinks can never leak into live sync is
  a good example of enforcing an invariant structurally rather than by convention.
- `SavedTournamentList` (pure merge logic split from `ViewerTournamentsStore` file I/O) is the
  pattern the other stores should follow.
- `ServerResult` sealed result type instead of exceptions at the API boundary.
- BLE codec layers (`PacketCodec` → `ChunkedMessage` → `GzipCodec` → `TournamentBroadcaster`) are
  cleanly separated and individually tested.

---

## What changed in this pass

35 files, +735/−612. All existing unit tests pass, plus two new test classes (`UploadSignerTest`,
`LiveLeaderboardTest`). `compileDebugKotlin` is clean (only pre-existing deprecation warnings).
No user-visible behaviour changed except the two bug fixes in §2. Changes are staged but not
committed.

**v0.9.3 follow-up:** 1.9, 1.10 and 1.11 fixed. **v0.10.0–v0.10.4:** 1.1 and 1.2 fixed.
**v0.10.5:** every remaining §3/§4 item except `strings.xml` (see its row) and the dependency
bump. Also fixed on the way: `HostingScreen` wasn't scrollable, so in landscape "Continue to
scoring" was unreachable.

**Verified on a device (Moto G9 Plus, v0.10.5 debug build):** cold start with the custom
`Application`/factory, Settings BLE toggle persisting, hosting over BLE (broadcast v1→v4 across
an app restart and two result edits — the host side of the "two edits" test), resume from
"My tournaments" after a kill, "Finish without saving" clearing the tournament, and old
`viewer_tournaments.json` data from a previous install decoding through `JsonFile`/`AppJson`.
**Still unverified** (need an account or a second phone): the retry/discard row after a failed
upload, reactions arriving on the organizer's screens, and BLE reception on a viewer.
