package com.example.nflunkyball.ui.viewer

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.nflunkyball.ble.EmojiPalette
import com.example.nflunkyball.ble.RoomCode
import com.example.nflunkyball.ble.TournamentReceiver
import com.example.nflunkyball.model.Tournament
import com.example.nflunkyball.qr.JoinPayload
import com.example.nflunkyball.server.CompetitorStats
import com.example.nflunkyball.server.DEFAULT_SERVER_URL
import com.example.nflunkyball.server.ServerApi
import com.example.nflunkyball.server.ServerCredentialsStore
import com.example.nflunkyball.server.ServerResult
import com.example.nflunkyball.server.TournamentSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class ViewerViewModel(application: Application) : AndroidViewModel(application) {

    private val fetchJson = Json { ignoreUnknownKeys = true }
    private val bluetoothAdapter: BluetoothAdapter? =
        application.getSystemService(BluetoothManager::class.java)?.adapter
    private val receiver = bluetoothAdapter?.let { TournamentReceiver(it) }
    private val credentialsStore = ServerCredentialsStore(application)
    private val fallbackTournamentFlow = MutableStateFlow<Tournament?>(null)

    val tournament: StateFlow<Tournament?> = receiver?.tournament ?: fallbackTournamentFlow

    var joinPayload by mutableStateOf<JoinPayload?>(null)
        private set

    var historyTournaments by mutableStateOf<List<TournamentSummary>>(emptyList())
        private set
    var competitors by mutableStateOf<List<CompetitorStats>>(emptyList())
        private set
    var historyStatus by mutableStateOf<String?>(null)
        private set

    fun join(payload: JoinPayload) {
        joinPayload = payload
        payload.pw?.let { credentialsStore.saveReadPassword(it) }
        val roomId = RoomCode.decode(payload.room) ?: return
        receiver?.start(roomId, viewModelScope)
    }

    fun sendEmoji(emoji: String) {
        val roomId = joinPayload?.let { RoomCode.decode(it.room) } ?: return
        viewModelScope.launch { receiver?.sendEmoji(roomId, EmojiPalette.codeFor(emoji)) }
    }

    fun readPasswordAvailable(): Boolean =
        (joinPayload?.pw ?: credentialsStore.loadReadPassword()) != null

    fun loadHistory() {
        val server = joinPayload?.server ?: DEFAULT_SERVER_URL
        val password = joinPayload?.pw ?: credentialsStore.loadReadPassword() ?: return
        val api = ServerApi(server)
        viewModelScope.launch {
            historyStatus = "Loading…"
            when (val result = api.listTournaments(password)) {
                is ServerResult.Success -> {
                    historyTournaments = result.value
                    historyStatus = null
                }
                is ServerResult.Failure -> historyStatus = result.message
            }
            when (val result = api.listCompetitors(password)) {
                is ServerResult.Success -> competitors = result.value
                is ServerResult.Failure -> Unit
            }
        }
    }

    suspend fun fetchTournamentDetail(id: Int): ServerResult<Tournament> {
        val server = joinPayload?.server ?: DEFAULT_SERVER_URL
        val password = joinPayload?.pw ?: credentialsStore.loadReadPassword()
            ?: return ServerResult.Failure("No read password available")
        return when (val result = ServerApi(server).getTournamentJson(id, password)) {
            is ServerResult.Success -> runCatching {
                fetchJson.decodeFromString(Tournament.serializer(), result.value)
            }.fold(
                onSuccess = { ServerResult.Success(it) },
                onFailure = { ServerResult.Failure(it.message ?: "Failed to parse tournament") }
            )
            is ServerResult.Failure -> result
        }
    }

    override fun onCleared() {
        receiver?.stop()
    }
}
