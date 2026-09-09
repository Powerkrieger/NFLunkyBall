package com.example.nflunkyball.ui.organizer

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.nflunkyball.ble.BleCapability
import com.example.nflunkyball.ble.EmojiPalette
import com.example.nflunkyball.ble.RoomCode
import com.example.nflunkyball.ble.TournamentBroadcaster
import com.example.nflunkyball.model.Group
import com.example.nflunkyball.model.Match
import com.example.nflunkyball.model.MatchResult
import com.example.nflunkyball.model.Team
import com.example.nflunkyball.model.Tournament
import com.example.nflunkyball.model.TournamentPhase
import com.example.nflunkyball.model.generateRoundRobinMatches
import com.example.nflunkyball.persistence.TournamentRepository
import com.example.nflunkyball.server.Ed25519
import com.example.nflunkyball.server.InvitePayloadCodec
import com.example.nflunkyball.server.OrganizerAccount
import com.example.nflunkyball.server.ServerCredentialsStore
import com.example.nflunkyball.server.ServerApi
import com.example.nflunkyball.server.ServerResult
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class OrganizerViewModel(application: Application) : AndroidViewModel(application) {

    private val uploadJson = Json { encodeDefaults = true }
    private val repository = TournamentRepository(application)
    private val credentialsStore = ServerCredentialsStore(application)
    private val bluetoothAdapter: BluetoothAdapter? =
        application.getSystemService(BluetoothManager::class.java)?.adapter
    private val broadcaster = bluetoothAdapter?.let { TournamentBroadcaster(it) }

    val tournament: StateFlow<Tournament?> = repository.tournament

    var organizerAccount by mutableStateOf(credentialsStore.loadAccount())
        private set

    var readPassword by mutableStateOf(credentialsStore.loadReadPassword())
        private set

    var uploadStatus by mutableStateOf<String?>(null)
        private set

    var knownCompetitorNames by mutableStateOf<List<String>>(emptyList())
        private set

    private val _emojiEvents = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val emojiEvents: SharedFlow<String> = _emojiEvents

    fun canHost(): Boolean = BleCapability.canAdvertise(getApplication())

    /** Known players from past tournaments this group has recorded, so the organizer can pick
     *  existing ones instead of retyping — also how a returning organizer confirms their
     *  account is actually registered with the group before starting a new tournament. */
    fun loadKnownCompetitors() {
        val account = organizerAccount ?: return
        val password = readPassword ?: return
        viewModelScope.launch {
            when (val result = ServerApi(account.serverUrl).listCompetitors(password)) {
                is ServerResult.Success -> knownCompetitorNames = result.value.map { it.name }.sorted()
                is ServerResult.Failure -> Unit
            }
        }
    }

    fun startTournament(name: String, teams: List<Team>, groupAssignments: Map<String, List<String>>) {
        val groups = groupAssignments.map { (groupName, teamIds) ->
            val bareGroup = Group(id = UUID.randomUUID().toString(), name = groupName, teamIds = teamIds)
            bareGroup.copy(matches = bareGroup.generateRoundRobinMatches())
        }
        repository.start(
            Tournament(
                id = UUID.randomUUID().toString(),
                name = name,
                teams = teams,
                groups = groups,
                phase = TournamentPhase.GROUP_STAGE
            )
        )
    }

    fun startHosting() {
        val adapter = bluetoothAdapter ?: return
        val id = tournament.value?.id?.let { RoomCode.forTournament(it) } ?: return
        broadcaster?.start(id, repository.tournament.filterNotNull(), viewModelScope)
        viewModelScope.launch {
            broadcaster?.emojiEvents?.collect { packet ->
                _emojiEvents.emit(EmojiPalette.emojiFor(packet.emojiCode))
            }
        }
    }

    fun stopHosting() {
        broadcaster?.stop()
    }

    fun recordGroupMatchResult(groupId: String, matchId: String, result: MatchResult) {
        repository.update { t ->
            t.copy(
                groups = t.groups.map { g ->
                    if (g.id != groupId) g
                    else g.copy(matches = g.matches.map { m -> if (m.id == matchId) m.copy(result = result) else m })
                }
            )
        }
    }

    fun advanceToBracket() {
        repository.update { it.copy(phase = TournamentPhase.BRACKET) }
    }

    fun addBracketMatch(teamAId: String, teamBId: String, roundLabel: String) {
        repository.update { t ->
            t.copy(
                bracketMatches = t.bracketMatches + Match(
                    id = UUID.randomUUID().toString(),
                    teamAId = teamAId,
                    teamBId = teamBId,
                    roundLabel = roundLabel
                )
            )
        }
    }

    fun recordBracketMatchResult(matchId: String, result: MatchResult) {
        repository.update { t ->
            t.copy(bracketMatches = t.bracketMatches.map { m -> if (m.id == matchId) m.copy(result = result) else m })
        }
    }

    fun finishTournament() {
        repository.update { it.copy(phase = TournamentPhase.FINISHED) }
    }

    /** Called once the organizer is done with a finished tournament (after upload) so the next
     *  "Host a tournament" starts fresh instead of resuming a dead one. */
    fun clearTournament() {
        stopHosting()
        repository.clear()
    }

    /** [inviteCode] is the whole code an admin generated (bundles the server URL + token) —
     *  see InvitePayload for why the app never hardcodes a server address itself. */
    fun linkAccount(
        displayName: String,
        inviteCode: String,
        onResult: (Boolean, String) -> Unit
    ) {
        val invite = InvitePayloadCodec.decode(inviteCode)
        if (invite == null) {
            onResult(false, "That doesn't look like a valid invite code")
            return
        }
        viewModelScope.launch {
            val keyPair = Ed25519.generateKeyPair()
            val publicKeyB64 = Base64.encodeToString(keyPair.publicKeyBytes, Base64.NO_WRAP)
            when (val result = ServerApi(invite.server).register(displayName, invite.token, publicKeyB64)) {
                is ServerResult.Success -> {
                    val account = OrganizerAccount(
                        accountId = result.value.accountId,
                        displayName = displayName,
                        serverUrl = invite.server,
                        privateKeySeed = keyPair.privateKeySeed,
                        publicKeyBytes = keyPair.publicKeyBytes
                    )
                    credentialsStore.saveAccount(account)
                    credentialsStore.saveReadPassword(result.value.readPassword)
                    organizerAccount = account
                    readPassword = result.value.readPassword
                    onResult(true, "Linked as $displayName")
                }
                is ServerResult.Failure -> onResult(false, result.message)
            }
        }
    }

    fun uploadToHistory() {
        val account = organizerAccount ?: return
        val current = tournament.value ?: return
        viewModelScope.launch {
            uploadStatus = "Uploading…"
            val bodyJson = uploadJson.encodeToString(Tournament.serializer(), current)
            val timestamp = System.currentTimeMillis() / 1000
            val message = "${current.id}|$timestamp|${sha256Hex(bodyJson)}"
            val signature = Ed25519.sign(account.privateKeySeed, message.toByteArray())
            val signatureB64 = Base64.encodeToString(signature, Base64.NO_WRAP)
            val result = ServerApi(account.serverUrl)
                .uploadTournament(account.accountId, timestamp, signatureB64, bodyJson)
            uploadStatus = when (result) {
                is ServerResult.Success -> "Uploaded"
                is ServerResult.Failure -> "Failed: ${result.message}"
            }
        }
    }

    private fun sha256Hex(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) }

    override fun onCleared() {
        broadcaster?.stop()
    }
}
