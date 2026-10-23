package de.powerizzle.nflunkyball.fakes

import de.powerizzle.nflunkyball.server.CredentialsStore
import de.powerizzle.nflunkyball.server.OrganizerAccount

class FakeCredentialsStore(
    var account: OrganizerAccount? = null,
    var readPassword: String? = null,
    var viewerServerUrl: String? = null
) : CredentialsStore {
    override fun loadAccount() = account
    override fun saveAccount(account: OrganizerAccount) { this.account = account }
    override fun loadReadPassword() = readPassword
    override fun saveReadPassword(password: String) { readPassword = password }
    override fun loadViewerServerUrl() = viewerServerUrl
    override fun saveViewerServerUrl(serverUrl: String) { viewerServerUrl = serverUrl }
    override fun clearAccount() { account = null }
}
