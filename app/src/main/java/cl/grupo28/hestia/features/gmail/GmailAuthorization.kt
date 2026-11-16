package cl.grupo28.hestia.features.gmail

import android.accounts.Account
import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import cl.grupo28.hestia.domain.gmail.GMAIL_READONLY
import com.google.android.gms.auth.api.identity.*
import com.google.android.gms.common.api.Scope

/** Tokens only cross a callback in memory, never saved state or custom intents. */
class GmailAuthorization(private val activity: ComponentActivity, private val model: GmailViewModel, saved: Bundle?) {
    private val client = Identity.getAuthorizationClient(activity)
    private var requestId: String? = saved?.getString("gmail_authorization_request")
    private val launcher = activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val id = requestId ?: return@registerForActivityResult
        requestId = null
        if (result.resultCode != Activity.RESULT_OK) model.failed(id, "Cancelaste la conexión a Gmail. Puedes seguir usando Hestia.")
        else try { deliver(id, client.getAuthorizationResultFromIntent(result.data)) }
        catch (_: Exception) { model.failed(id, "No pudimos completar la autorización de Gmail.") }
    }
    fun save(out: Bundle) { out.putString("gmail_authorization_request", requestId) }
    fun authorize(email: String, id: String) {
        requestId = id
        val request = AuthorizationRequest.builder().setAccount(Account(email, "com.google"))
            .setRequestedScopes(listOf(Scope(GMAIL_READONLY))).build()
        client.authorize(request).addOnSuccessListener { result ->
            if (activity.isDestroyed) { model.failed(id, "Se interrumpió la conexión a Gmail. Inténtalo nuevamente."); return@addOnSuccessListener }
            try {
                if (result.hasResolution()) {
                    launcher.launch(IntentSenderRequest.Builder(requireNotNull(result.pendingIntent).intentSender).build())
                } else { requestId = null; deliver(id, result) }
            } catch (_: Exception) { model.failed(id, "No pudimos abrir el permiso de Gmail.") }
        }.addOnFailureListener { model.failed(id, "No pudimos autorizar Gmail. Revisa tu conexión y la cuenta del teléfono.") }
    }
    private fun deliver(id: String, result: AuthorizationResult) { model.authorized(id, result.accessToken, result.grantedScopes) }
    fun revoke(email: String, done: (Boolean) -> Unit) {
        val account = Account(email,"com.google")
        fun revokeAndClear(token: String?) {
            val request = RevokeAccessRequest.builder().setAccount(account).setScopes(listOf(Scope(GMAIL_READONLY))).build()
            client.revokeAccess(request).addOnCompleteListener { revocation ->
                // Successful revokeAccess also clears Google's cache. Explicitly clear the
                // operation-local token even when remote revocation was not confirmed.
                if (token.isNullOrBlank()) done(revocation.isSuccessful)
                else client.clearToken(ClearTokenRequest.builder().setToken(token).build())
                    .addOnCompleteListener { done(revocation.isSuccessful) }
            }
        }
        // Obtain only a token already available without user interaction; never launch a
        // consent resolution while disconnecting. This token is discarded with this operation.
        val cached = AuthorizationRequest.builder().setAccount(account).setRequestedScopes(listOf(Scope(GMAIL_READONLY))).build()
        client.authorize(cached).addOnSuccessListener { result -> revokeAndClear(if (result.hasResolution()) null else result.accessToken) }
            .addOnFailureListener { revokeAndClear(null) }
    }
}
