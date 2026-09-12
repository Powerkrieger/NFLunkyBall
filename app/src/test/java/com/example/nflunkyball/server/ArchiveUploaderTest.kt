package com.example.nflunkyball.server

import com.example.nflunkyball.fakes.FakeServerApi
import com.example.nflunkyball.fakes.testAccount
import com.example.nflunkyball.model.MatchDrinks
import com.example.nflunkyball.model.Team
import com.example.nflunkyball.model.Tournament
import com.example.nflunkyball.model.TournamentFinishInfo
import com.example.nflunkyball.model.TournamentPhase
import com.example.nflunkyball.persistence.FinishInfoStore
import com.example.nflunkyball.persistence.JsonFile
import com.example.nflunkyball.persistence.MatchDrinkStore
import com.example.nflunkyball.persistence.TournamentRepository
import java.io.File
import java.nio.file.Files
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalEncodingApi::class)
class ArchiveUploaderTest {

    private val dir: File = Files.createTempDirectory("uploader").toFile()
    private val repository = TournamentRepository(dir)
    private val drinks = MatchDrinkStore(dir)
    private val finishInfo = FinishInfoStore(dir)
    private val api = FakeServerApi()
    private var account: OrganizerAccount? = testAccount()
    private val info = TournamentFinishInfo(dateMillis = 1_700_000_000_000L, location = "Garden", referees = "", comment = "")

    @After
    fun tearDown() {
        JsonFile.awaitIdle()
        dir.deleteRecursively()
    }

    private fun uploader(scope: CoroutineScope) =
        ArchiveUploader(repository, drinks, finishInfo, account = { account }, serverApi = { api }, scope = scope)

    private fun startTournament() {
        repository.start(Tournament(id = "t1", name = "T", teams = listOf(Team("a", "Anna")), phase = TournamentPhase.BRACKET))
        drinks.set("m1", MatchDrinks(teamA = "Beer"))
    }

    @Test
    fun `successful upload clears tournament, drinks and finish info`() = runTest {
        startTournament()
        api.uploadResult = ServerResult.Success(UploadResponse(1))
        val uploader = uploader(this)

        uploader.finishAndUpload(info)
        assertEquals(TournamentPhase.FINISHED, repository.tournament.value?.phase)
        assertTrue(uploader.hasPendingUpload)
        assertEquals("Uploading…", uploader.uploadStatus.value)
        advanceUntilIdle()

        assertNull(repository.tournament.value)
        assertTrue(drinks.all().isEmpty())
        assertNull(finishInfo.get())
        assertNull(uploader.uploadStatus.value)
        assertFalse(uploader.hasPendingUpload)

        // The payload is the drinks-augmented UploadTournament with the finish metadata, signed
        // by the account's key so the backend can verify it.
        val upload = api.uploads.single()
        assertEquals(7, upload.accountId)
        assertTrue(upload.bodyJson.contains("\"location\":\"Garden\""))
        assertTrue(upload.bodyJson.contains("\"drinkA\":\"Beer\"") || upload.bodyJson.contains("\"phase\":\"FINISHED\""))
        val message = UploadSigner.message("t1", upload.timestamp, upload.bodyJson).toByteArray()
        assertTrue(Ed25519.verify(account!!.publicKeyBytes, message, Base64.decode(upload.signatureBase64)))
    }

    @Test
    fun `failed upload keeps everything and reports the error`() = runTest {
        startTournament()
        api.uploadResult = ServerResult.Failure("offline")
        val uploader = uploader(this)

        uploader.finishAndUpload(info)
        advanceUntilIdle()

        assertEquals("Upload failed: offline", uploader.uploadStatus.value)
        assertTrue(uploader.hasPendingUpload)
        assertEquals(TournamentPhase.FINISHED, repository.tournament.value?.phase)
        assertEquals(1, drinks.all().size)
        assertEquals(info, finishInfo.get())
    }

    @Test
    fun `retry reuses the saved finish info and succeeds`() = runTest {
        startTournament()
        api.uploadResult = ServerResult.Failure("offline")
        val uploader = uploader(this)
        uploader.finishAndUpload(info)
        advanceUntilIdle()

        api.uploadResult = ServerResult.Success(UploadResponse(1))
        uploader.retry()
        advanceUntilIdle()

        assertEquals(2, api.uploads.size)
        assertTrue(api.uploads[1].bodyJson.contains("\"location\":\"Garden\""))
        assertNull(repository.tournament.value)
    }

    @Test
    fun `retry and discard are no-ops without a pending upload`() = runTest {
        startTournament()
        val uploader = uploader(this)
        uploader.retry()
        uploader.discard()
        assertTrue(api.uploads.isEmpty())
        assertNotNull(repository.tournament.value)
    }

    @Test
    fun `discard drops a pending tournament`() = runTest {
        startTournament()
        api.uploadResult = ServerResult.Failure("offline")
        val uploader = uploader(this)
        uploader.finishAndUpload(info)
        advanceUntilIdle()

        uploader.discard()
        assertNull(repository.tournament.value)
        assertTrue(drinks.all().isEmpty())
        assertNull(uploader.uploadStatus.value)
    }

    @Test
    fun `finishing without an account clears immediately and never uploads`() = runTest {
        startTournament()
        account = null
        val uploader = uploader(this)

        uploader.finishAndUpload(info)
        assertNull(repository.tournament.value)
        assertTrue(api.uploads.isEmpty())
        assertNull(finishInfo.get())
    }

    @Test
    fun `a retry while an upload is in flight is ignored`() = runTest {
        startTournament()
        api.uploadResult = ServerResult.Success(UploadResponse(1))
        val uploader = uploader(this)
        uploader.finishAndUpload(info)
        uploader.retry()
        uploader.retry()
        advanceUntilIdle()
        assertEquals(1, api.uploads.size)
    }
}
