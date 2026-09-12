package com.example.nflunkyball

import android.app.Application
import android.bluetooth.BluetoothManager
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.nflunkyball.ble.LiveBroadcaster
import com.example.nflunkyball.ble.LiveReceiver
import com.example.nflunkyball.ble.TournamentBroadcaster
import com.example.nflunkyball.ble.TournamentReceiver
import com.example.nflunkyball.persistence.AppSettings
import com.example.nflunkyball.persistence.AppSettingsStore
import com.example.nflunkyball.persistence.FinishInfoStore
import com.example.nflunkyball.persistence.MatchDrinkStore
import com.example.nflunkyball.persistence.TournamentRepository
import com.example.nflunkyball.persistence.ViewerTournamentsStore
import com.example.nflunkyball.server.AccountManager
import com.example.nflunkyball.server.CredentialsStore
import com.example.nflunkyball.server.KtorServerApi
import com.example.nflunkyball.server.ServerApiFactory
import com.example.nflunkyball.server.ServerCredentialsStore
import com.example.nflunkyball.ui.SettingsViewModel
import com.example.nflunkyball.ui.organizer.OrganizerViewModel
import com.example.nflunkyball.ui.viewer.ViewerViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Hand-wired dependency graph for the whole app — one instance of every store/transport, held
 * by [NfLunkyBallApplication] for the process lifetime, and the [viewModelFactory] that hands
 * them to the ViewModels. No DI framework: two ViewModels don't justify one, and keeping the
 * wiring in one readable place is the point. Everything here has a plain-JVM substitute (an
 * interface or a `File`-based constructor), which is what makes the ViewModel layer testable.
 */
class AppContainer(application: Application) {
    val tournamentRepository = TournamentRepository(application)
    val viewerTournamentsStore = ViewerTournamentsStore(application)
    val drinkStore = MatchDrinkStore(application)
    val finishInfoStore = FinishInfoStore(application)
    val settings: AppSettings = AppSettingsStore(application)
    val credentials: CredentialsStore = ServerCredentialsStore(application)
    val serverApi: ServerApiFactory = ::KtorServerApi

    /** Process-lifetime scope for the one piece of state shared by several ViewModels. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    /** Shared between the organizer flow and Settings, so linking/unlinking in one is seen by
     *  the other (and by live sync) immediately. */
    val accountManager = AccountManager(credentials, serverApi, appScope)

    private val bluetoothAdapter = application.getSystemService(BluetoothManager::class.java)?.adapter
    /** Null on a device without Bluetooth — BLE mode then simply does nothing, as before. */
    val broadcaster: LiveBroadcaster? = bluetoothAdapter?.let { TournamentBroadcaster(it, application) }
    val receiver: LiveReceiver? = bluetoothAdapter?.let { TournamentReceiver(it) }

    val viewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            OrganizerViewModel(
                repository = tournamentRepository,
                accountManager = accountManager,
                settings = settings,
                drinkStore = drinkStore,
                finishInfoStore = finishInfoStore,
                broadcaster = broadcaster,
                serverApi = serverApi
            )
        }
        initializer { SettingsViewModel(accountManager, settings) }
        initializer {
            ViewerViewModel(
                receiver = receiver,
                credentialsStore = credentials,
                settings = settings,
                tournamentsStore = viewerTournamentsStore,
                serverApi = serverApi
            )
        }
    }
}
