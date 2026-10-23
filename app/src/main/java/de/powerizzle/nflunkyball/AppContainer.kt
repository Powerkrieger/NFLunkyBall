package de.powerizzle.nflunkyball

import android.app.Application
import android.bluetooth.BluetoothManager
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.powerizzle.nflunkyball.ble.LiveBroadcaster
import de.powerizzle.nflunkyball.ble.LiveReceiver
import de.powerizzle.nflunkyball.ble.TournamentBroadcaster
import de.powerizzle.nflunkyball.ble.TournamentReceiver
import de.powerizzle.nflunkyball.persistence.AppSettings
import de.powerizzle.nflunkyball.persistence.AppSettingsStore
import de.powerizzle.nflunkyball.persistence.FinishInfoStore
import de.powerizzle.nflunkyball.persistence.MatchDrinkStore
import de.powerizzle.nflunkyball.persistence.TournamentRepository
import de.powerizzle.nflunkyball.persistence.ViewerTournamentsStore
import de.powerizzle.nflunkyball.server.AccountManager
import de.powerizzle.nflunkyball.server.CredentialsStore
import de.powerizzle.nflunkyball.server.KtorServerApi
import de.powerizzle.nflunkyball.server.ServerApiFactory
import de.powerizzle.nflunkyball.server.KeystoreCredentialsStore
import de.powerizzle.nflunkyball.ui.SettingsViewModel
import de.powerizzle.nflunkyball.ui.organizer.OrganizerViewModel
import de.powerizzle.nflunkyball.ui.viewer.ViewerViewModel
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
    val credentials: CredentialsStore = KeystoreCredentialsStore(application)
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
