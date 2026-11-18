package cl.grupo28.hestia

import android.app.Application
import cl.grupo28.hestia.features.automatic.AutomaticEnvironmentFactory
import cl.grupo28.hestia.data.auth.KeystoreSessionStore
import cl.grupo28.hestia.data.automatic.AutomaticApi
import cl.grupo28.hestia.features.automatic.AutomaticNotifications

class HestiaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Restore a previously consented registration before an FCM receiver/service starts.
        // With no real Firebase config or consent this performs no initialization or network call.
        if(AutomaticEnvironmentFactory.externalAllowed(this)) AutomaticNotifications(this, AutomaticApi(KeystoreSessionStore(this))).restoreAfterProcessStart()
    }
}
