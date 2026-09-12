package com.example.nflunkyball

import android.app.Application

class NfLunkyBallApplication : Application() {
    /** Built on first use rather than in onCreate so a cold start doesn't pay for
     *  EncryptedSharedPreferences/file reads before the first frame needs them. */
    val container: AppContainer by lazy { AppContainer(this) }
}
