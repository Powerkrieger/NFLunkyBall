package com.example.nflunkyball.server

import com.example.nflunkyball.fakes.FakeCredentialsStore
import com.example.nflunkyball.fakes.FakeServerApi
import com.example.nflunkyball.fakes.inviteCode
import com.example.nflunkyball.fakes.testAccount
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountManagerTest {

    private val credentials = FakeCredentialsStore()
    private val api = FakeServerApi()

    private fun manager(scope: kotlinx.coroutines.CoroutineScope) =
        AccountManager(credentials, serverApi = { api }, scope = scope)

    @Test
    fun `organizer invite stores account, keypair and read password`() = runTest {
        api.registerResult = ServerResult.Success(RegisterResponse(accountId = 3, displayName = "Anna", readPassword = "pw"))
        val manager = manager(this)

        val result = manager.link(inviteCode(server = "https://srv", token = "abc"))

        assertEquals("Linked as Anna", result.getOrThrow())
        assertEquals("abc", api.registrations.single().first)
        val account = manager.account.value!!
        assertEquals(3, account.accountId)
        assertEquals("https://srv", account.serverUrl)
        assertEquals(32, account.publicKeyBytes.size)
        assertEquals(account, credentials.account)
        assertEquals("pw", credentials.readPassword)
        assertEquals("pw", manager.readPassword.value)
        assertNull(credentials.viewerServerUrl)
    }

    @Test
    fun `viewer invite stores only read password and server url`() = runTest {
        api.registerResult = ServerResult.Success(RegisterResponse(accountId = null, displayName = null, readPassword = "pw"))
        val manager = manager(this)

        assertEquals("Logged in as viewer", manager.link(inviteCode(server = "https://srv")).getOrThrow())
        assertNull(manager.account.value)
        assertEquals("pw", manager.readPassword.value)
        assertEquals("https://srv", credentials.viewerServerUrl)
    }

    @Test
    fun `garbage invite code fails without touching the server`() = runTest {
        val result = manager(this).link("not-an-invite")
        assertTrue(result.isFailure)
        assertTrue(api.registrations.isEmpty())
    }

    @Test
    fun `server rejection surfaces its message`() = runTest {
        api.registerResult = ServerResult.Failure("Invite already used")
        assertEquals("Invite already used", manager(this).link(inviteCode()).exceptionOrNull()?.message)
    }

    @Test
    fun `unlink clears the account but keeps the read password`() = runTest {
        credentials.account = testAccount()
        credentials.readPassword = "pw"
        val manager = manager(this)

        manager.unlink()
        assertNull(manager.account.value)
        assertNull(credentials.account)
        assertEquals("pw", manager.readPassword.value)
        assertNull(manager.syncStatus.value)
    }

    @Test
    fun `sync status reflects revocation and reachability`() = runTest {
        credentials.account = testAccount()
        credentials.readPassword = "pw"
        val manager = manager(this)

        api.accountStatusResult = ServerResult.Success(AccountStatus(7, "Orga", revoked = false))
        manager.checkSyncStatus()
        assertEquals(AccountSyncStatus.CHECKING, manager.syncStatus.value)
        advanceUntilIdle()
        assertEquals(AccountSyncStatus.CAN_SYNC, manager.syncStatus.value)

        api.accountStatusResult = ServerResult.Success(AccountStatus(7, "Orga", revoked = true))
        manager.checkSyncStatus(); advanceUntilIdle()
        assertEquals(AccountSyncStatus.REVOKED, manager.syncStatus.value)

        api.accountStatusResult = ServerResult.Failure("offline")
        manager.checkSyncStatus(); advanceUntilIdle()
        assertEquals(AccountSyncStatus.UNKNOWN, manager.syncStatus.value)
    }

    @Test
    fun `sync status is null with nothing linked`() = runTest {
        val manager = manager(this)
        manager.checkSyncStatus()
        assertNull(manager.syncStatus.value)
    }
}
