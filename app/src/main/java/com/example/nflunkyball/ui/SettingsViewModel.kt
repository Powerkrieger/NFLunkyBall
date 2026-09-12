package com.example.nflunkyball.ui

import androidx.lifecycle.ViewModel
import com.example.nflunkyball.persistence.AppLanguage
import com.example.nflunkyball.persistence.AppSettings
import com.example.nflunkyball.server.AccountManager
import com.example.nflunkyball.server.AccountSyncStatus
import com.example.nflunkyball.server.OrganizerAccount
import kotlinx.coroutines.flow.StateFlow

/** Backs [SettingsScreen]: the shared [AccountManager] (same instance the organizer flow and
 *  live sync use, so unlinking here is seen there immediately) plus the transport toggle. */
class SettingsViewModel(
    private val accountManager: AccountManager,
    private val settings: AppSettings
) : ViewModel() {
    val account: StateFlow<OrganizerAccount?> = accountManager.account
    val syncStatus: StateFlow<AccountSyncStatus?> = accountManager.syncStatus

    fun checkSyncStatus() = accountManager.checkSyncStatus()

    /** Local credentials only — see [AccountManager.unlink]. An in-progress tournament keeps
     *  hosting over BLE untouched and simply loses server sync until relinked. */
    fun unlinkAccount() = accountManager.unlink()

    fun useBleSync(): Boolean = settings.useBleSync()

    fun setUseBleSync(enabled: Boolean) = settings.setUseBleSync(enabled)

    fun language(): AppLanguage = settings.language()

    fun setLanguage(language: AppLanguage) = settings.setLanguage(language)
}
