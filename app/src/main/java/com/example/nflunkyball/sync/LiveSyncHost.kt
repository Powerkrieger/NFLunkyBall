package com.example.nflunkyball.sync

import com.example.nflunkyball.model.AppJson
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

/** Server-mode sync status — see [LiveSyncHost.serverSyncStatus]. */
sealed interface SyncState {
    /** Not hosting, or hosting over BLE (which reports via [LiveSyncHost.broadcastVersion]). */
    data object Off : SyncState
    /** Hosting in server mode with no organizer account to sign pushes with. */
    data object NotLinked : SyncState
    data object Starting : SyncState
    data object Syncing : SyncState
    data object Synced : SyncState
    data class Failed(val message: String) : SyncState
}

/**
 * Organizer side of live sync: pushes every new tournament state to viewers over whichever
 * transport [AppSettings.useBleSync] selects at [start] time — BLE advertising via
 * [LiveBroadcaster], or signed PUTs to the server's live endpoint. Also relays viewers' emoji
 * reactions (BLE only; the server path has no back-channel).
 */
class LiveSyncHost(
    private val settings: AppSettings,
    private val broadcaster: LiveBroadcaster?,
    private val account: StateFlow<OrganizerAccount?>,
    private val serverApi: ServerApiFactory,
    private val scope: CoroutineScope
) {
    private val liveJson = AppJson.lenient

    /** The version currently on air, or null while hosting is off — BLE mode only. */
    val broadcastVersion: StateFlow<Int?> = broadcaster?.broadcastVersion ?: MutableStateFlow(null)

    /** Server-mode counterpart to [broadcastVersion]. */
    private val _serverSyncStatus = MutableStateFlow<SyncState>(SyncState.Off)
    val serverSyncStatus: StateFlow<SyncState> = _serverSyncStatus

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

    private fun stopServerSync() {
        serverSyncJob?.cancel()
        serverSyncJob = null
        _serverSyncStatus.value = SyncState.Off
    }

    private fun stopBle() {
        broadcaster?.stop()
        emojiBridgeJob?.cancel()
        emojiBridgeJob = null
    }

    /** Needs a linked account to sign with — unlike BLE, a tournament can exist locally with
     *  none linked yet (credentials lost, or linking skipped for later — see SettingsScreen's
     *  account section and TournamentSettingsScreen's "Add invite token" row), so this surfaces
     *  that state instead of silently doing nothing. Follows [account], so linking or unlinking
     *  (from any screen) starts or stops the pushes without anyone having to call [start] again. */
    private fun startServerSync(tournamentUpdates: Flow<Tournament>) {
        serverSyncJob?.cancel()
        serverSyncJob = scope.launch {
            account.collectLatest { current ->
                if (current == null) {
                    _serverSyncStatus.value = SyncState.NotLinked
                    return@collectLatest
                }
                _serverSyncStatus.value = SyncState.Starting
                tournamentUpdates.collectLatest { tournament -> pushLiveState(current, tournament) }
            }
        }
    }

    private suspend fun pushLiveState(account: OrganizerAccount, current: Tournament) {
        _serverSyncStatus.value = SyncState.Syncing
        val bodyJson = liveJson.encodeToString(Tournament.serializer(), current)
        val signed = UploadSigner.sign(account.privateKeySeed, current.id, bodyJson)
        val result = serverApi(account.serverUrl)
            .pushLiveTournament(current.id, account.accountId, signed.timestamp, signed.signatureBase64, bodyJson)
        _serverSyncStatus.value = when (result) {
            is ServerResult.Success -> SyncState.Synced
            is ServerResult.Failure -> SyncState.Failed(result.message)
        }
    }
}
