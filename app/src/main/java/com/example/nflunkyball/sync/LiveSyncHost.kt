package com.example.nflunkyball.sync

import com.example.nflunkyball.ble.EmojiPalette
import com.example.nflunkyball.ble.LiveBroadcaster
import com.example.nflunkyball.ble.RoomCode
import com.example.nflunkyball.model.Tournament
import com.example.nflunkyball.persistence.AppSettings
import com.example.nflunkyball.server.OrganizerAccount
import com.example.nflunkyball.server.ServerApiFactory
import com.example.nflunkyball.server.ServerResult
import com.example.nflunkyball.server.UploadSigner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Organizer side of live sync: pushes every new tournament state to viewers over whichever
 * transport [AppSettings.useBleSync] selects at [start] time — BLE advertising via
 * [LiveBroadcaster], or signed PUTs to the server's live endpoint. Also relays viewers' emoji
 * reactions (BLE only; the server path has no back-channel).
 */
class LiveSyncHost(
    private val settings: AppSettings,
    private val broadcaster: LiveBroadcaster?,
    private val account: () -> OrganizerAccount?,
    private val serverApi: ServerApiFactory,
    private val scope: CoroutineScope
) {
    private val liveJson = Json { encodeDefaults = true }

    /** The version currently on air, or null while hosting is off — BLE mode only. */
    val broadcastVersion: StateFlow<Int?> = broadcaster?.broadcastVersion ?: MutableStateFlow(null)

    /** Server-mode counterpart to [broadcastVersion] — null until the first push attempt. */
    private val _serverSyncStatus = MutableStateFlow<String?>(null)
    val serverSyncStatus: StateFlow<String?> = _serverSyncStatus

    private val _emojiEvents = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val emojiEvents: SharedFlow<String> = _emojiEvents

    private var serverSyncJob: Job? = null

    /** Bridges [LiveBroadcaster.emojiEvents] into [emojiEvents]. Tracked so a repeat [start]
     *  (HostingScreen is re-entered every time it's shown, and linking an account
     *  mid-tournament restarts sync) replaces the bridge instead of stacking another collector
     *  — which would emit every reaction N times. */
    private var emojiBridgeJob: Job? = null

    /** (Re)starts syncing [tournamentUpdates] for the tournament with [tournamentId]. Safe to call
     *  repeatedly; the previous session is stopped first. */
    fun start(tournamentId: String, tournamentUpdates: Flow<Tournament>) {
        if (settings.useBleSync()) {
            stopServerSync()
            val broadcaster = broadcaster ?: return
            broadcaster.start(RoomCode.forTournament(tournamentId), tournamentUpdates, scope)
            emojiBridgeJob?.cancel()
            emojiBridgeJob = scope.launch {
                broadcaster.emojiEvents.collect { packet -> _emojiEvents.emit(EmojiPalette.emojiFor(packet.emojiCode)) }
            }
        } else {
            stopBle()
            startServerSync(tournamentUpdates)
        }
    }

    fun stop() {
        stopBle()
        stopServerSync()
    }

    /** Server sync only — what unlinking the account needs (BLE keeps going without one). */
    fun stopServerSync() {
        serverSyncJob?.cancel()
        serverSyncJob = null
        _serverSyncStatus.value = null
    }

    private fun stopBle() {
        broadcaster?.stop()
        emojiBridgeJob?.cancel()
        emojiBridgeJob = null
    }

    /** Needs a linked account to sign with — unlike BLE, a tournament can exist locally with
     *  none linked yet (credentials lost, or linking skipped for later — see SettingsScreen's
     *  account section and TournamentSettingsScreen's "Add invite token" row), so this surfaces
     *  that state instead of silently doing nothing. Called again once linking completes. */
    private fun startServerSync(tournamentUpdates: Flow<Tournament>) {
        val account = account()
        if (account == null) {
            stopServerSync()
            _serverSyncStatus.value = "Not linked — link an organizer account to sync"
            return
        }
        serverSyncJob?.cancel()
        _serverSyncStatus.value = "Starting sync…"
        serverSyncJob = scope.launch {
            tournamentUpdates.collectLatest { current -> pushLiveState(account, current) }
        }
    }

    private suspend fun pushLiveState(account: OrganizerAccount, current: Tournament) {
        _serverSyncStatus.value = "Syncing…"
        val bodyJson = liveJson.encodeToString(Tournament.serializer(), current)
        val signed = UploadSigner.sign(account.privateKeySeed, current.id, bodyJson)
        val result = serverApi(account.serverUrl)
            .pushLiveTournament(current.id, account.accountId, signed.timestamp, signed.signatureBase64, bodyJson)
        _serverSyncStatus.value = when (result) {
            is ServerResult.Success -> "Synced"
            is ServerResult.Failure -> "Sync failed: ${result.message}"
        }
    }
}
