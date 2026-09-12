package com.example.nflunkyball.server

import com.example.nflunkyball.model.Tournament
import com.example.nflunkyball.model.TournamentFinishInfo
import com.example.nflunkyball.model.TournamentPhase
import com.example.nflunkyball.model.withPhase
import com.example.nflunkyball.persistence.FinishInfoStore
import com.example.nflunkyball.persistence.MatchDrinkStore
import com.example.nflunkyball.persistence.TournamentRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Ends a tournament and gets it into the history server. The local copy (tournament, drinks,
 * finish info) is only cleared once the server has confirmed the upload — on failure (offline,
 * revoked account, killed mid-upload) it stays put with [uploadStatus] carrying the error, and My
 * tournaments offers [retry] / [discard]. This is the one and only place drink choices and finish
 * metadata ever leave the device (see [MatchDrinkStore], [Tournament.toUploadPayload]).
 */
class ArchiveUploader(
    private val repository: TournamentRepository,
    private val drinkStore: MatchDrinkStore,
    private val finishInfoStore: FinishInfoStore,
    private val account: () -> OrganizerAccount?,
    private val serverApi: ServerApiFactory,
    private val scope: CoroutineScope
) {
    private val uploadJson = Json { encodeDefaults = true }

    /** Outcome of the last upload attempt for the current tournament, or null if none yet. */
    private val _uploadStatus = MutableStateFlow<String?>(null)
    val uploadStatus: StateFlow<String?> = _uploadStatus

    /** True while a finished tournament is still on this device because its upload hasn't
     *  succeeded yet — it stays in My tournaments with retry/discard until it has. */
    val hasPendingUpload: Boolean
        get() = repository.tournament.value?.phase == TournamentPhase.FINISHED

    /** Marks the tournament finished and uploads it. With no account linked there's nothing to
     *  upload to (the organizer explicitly chose "finish without saving" to get here), so the
     *  tournament is simply cleared. */
    fun finishAndUpload(finishInfo: TournamentFinishInfo) {
        repository.update { it.withPhase(TournamentPhase.FINISHED) }
        if (account() == null) {
            clearLocal()
            return
        }
        finishInfoStore.set(finishInfo)
        upload(finishInfo)
    }

    /** Re-attempts the upload of a finished tournament using the finish info saved with it. */
    fun retry() {
        if (!hasPendingUpload) return
        // A tournament finished by an older version (or whose finish-info file was lost) has no
        // saved info: upload it with just today's date rather than blocking on it.
        val info = finishInfoStore.get() ?: TournamentFinishInfo(System.currentTimeMillis(), "", "", "")
        upload(info)
    }

    /** Gives up on uploading a finished tournament and drops it from this device. */
    fun discard() {
        if (!hasPendingUpload) return
        clearLocal()
    }

    /** Drops the tournament and everything recorded alongside it, whether finished or abandoned. */
    fun clearLocal() {
        repository.clear()
        drinkStore.clear()
        finishInfoStore.clear()
        _uploadStatus.value = null
    }

    private fun upload(finishInfo: TournamentFinishInfo) {
        val account = account() ?: run {
            _uploadStatus.value = "Not linked — link an organizer account to upload"
            return
        }
        val current = repository.tournament.value ?: return
        if (_uploadStatus.value == UPLOADING) return
        // Set before launching so a second tap on Retry can't queue a duplicate upload.
        _uploadStatus.value = UPLOADING
        scope.launch {
            val payload = current.toUploadPayload(drinkStore.all(), finishInfo)
            val bodyJson = uploadJson.encodeToString(UploadTournament.serializer(), payload)
            val signed = UploadSigner.sign(account.privateKeySeed, current.id, bodyJson)
            val result = serverApi(account.serverUrl)
                .uploadTournament(account.accountId, signed.timestamp, signed.signatureBase64, bodyJson)
            when (result) {
                is ServerResult.Success -> clearLocal()
                is ServerResult.Failure -> _uploadStatus.value = "Upload failed: ${result.message}"
            }
        }
    }

    private companion object {
        const val UPLOADING = "Uploading…"
    }
}
