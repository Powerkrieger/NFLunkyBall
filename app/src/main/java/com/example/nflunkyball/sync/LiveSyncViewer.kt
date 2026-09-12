package com.example.nflunkyball.sync

import com.example.nflunkyball.ble.ChunkProgress
import com.example.nflunkyball.ble.EmojiPalette
import com.example.nflunkyball.ble.LiveReceiver
import com.example.nflunkyball.ble.RoomCode
import com.example.nflunkyball.model.Tournament
import com.example.nflunkyball.persistence.AppSettings
import com.example.nflunkyball.qr.JoinPayload
import com.example.nflunkyball.server.ServerApiFactory
import com.example.nflunkyball.server.ServerResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/** How often a server-mode viewer re-fetches live state — no push channel, so this trades
 *  freshness for simplicity; BLE mode (the fallback for offline venues) is still near-instant. */
const val LIVE_POLL_INTERVAL_MS = 12_000L

/**
 * Viewer side of live sync: feeds [tournament] from whichever transport [AppSettings.useBleSync]
 * selects at [join] time — a bridge over [LiveReceiver.tournament] (BLE) or a polling loop on
 * the server's live endpoint. Not aliased directly to `receiver.tournament` because the mode
 * can change between joins (the Settings toggle).
 */
class LiveSyncViewer(
    private val settings: AppSettings,
    private val receiver: LiveReceiver?,
    private val serverApi: ServerApiFactory,
    private val scope: CoroutineScope
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val _tournament = MutableStateFlow<Tournament?>(null)
    val tournament: StateFlow<Tournament?> = _tournament

    /** BLE reassembly progress for a "reading state of version N" bar; always null in server mode. */
    val receiveProgress: StateFlow<ChunkProgress?> = receiver?.receiveProgress ?: MutableStateFlow(null)

    private var roomId: Int? = null
    private var syncJob: Job? = null

    /** Starts following [payload]'s room, replacing whatever was being followed before. Returns
     *  false (and does nothing) if the room code doesn't decode. */
    fun join(payload: JoinPayload): Boolean {
        val roomId = RoomCode.decode(payload.room) ?: return false
        this.roomId = roomId
        stop()
        syncJob = if (settings.useBleSync()) {
            receiver?.start(roomId, scope)
            scope.launch { receiver?.tournament?.collect { _tournament.value = it } }
        } else {
            startServerPolling(payload)
        }
        return true
    }

    fun stop() {
        syncJob?.cancel()
        syncJob = null
        receiver?.stop()
        _tournament.value = null
    }

    /** BLE only — there's no viewer→organizer channel in server mode, so this is a no-op there. */
    fun sendEmoji(emoji: String) {
        val roomId = roomId ?: return
        scope.launch { receiver?.sendEmoji(roomId, EmojiPalette.codeFor(emoji)) }
    }

    fun decode(tournamentJson: String): Tournament? =
        runCatching { json.decodeFromString(Tournament.serializer(), tournamentJson) }.getOrNull()

    /** Re-fetches the organizer's live-pushed state every [LIVE_POLL_INTERVAL_MS] until the join
     *  target changes. Silently does nothing if [payload] lacks what it needs (manually-typed
     *  room codes only ever carry [JoinPayload.room] — see its doc — so server mode simply can't
     *  work for those; BLE mode remains available as the fallback). */
    private fun startServerPolling(payload: JoinPayload): Job {
        val server = payload.server
        val tournamentId = payload.tid
        val password = payload.pw
        return scope.launch {
            if (server == null || tournamentId == null || password == null) return@launch
            val api = serverApi(server)
            while (isActive) {
                when (val result = api.getLiveTournamentJson(tournamentId, password)) {
                    is ServerResult.Success -> decode(result.value)?.let { _tournament.value = it }
                    is ServerResult.Failure -> Unit
                }
                delay(LIVE_POLL_INTERVAL_MS)
            }
        }
    }
}
