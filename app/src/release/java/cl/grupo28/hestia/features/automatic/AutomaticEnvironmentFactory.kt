package cl.grupo28.hestia.features.automatic

import android.content.Context
import androidx.compose.runtime.Composable
import cl.grupo28.hestia.data.automatic.AutomaticApi
import cl.grupo28.hestia.data.auth.KeystoreSessionStore
import cl.grupo28.hestia.domain.auth.AuthState
import cl.grupo28.hestia.domain.automatic.AutomaticEnvironment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object AutomaticEnvironmentFactory {
    private val off=MutableStateFlow(false)
    fun externalAllowed(context:Context)=true
    fun mode(context:Context):StateFlow<Boolean> = off
    fun create(context:Context,auth:StateFlow<AuthState>):AutomaticEnvironment {
        val api=AutomaticApi(KeystoreSessionStore(context));return AutomaticEnvironment(api,auth,AutomaticNotifications(context,api))
    }
}
@Composable
fun AutomaticEnvironmentPanel(model:AutomaticViewModel,production:@Composable ()->Unit){production()}

@Composable fun AutomaticEnvironmentBadge() {}
