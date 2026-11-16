package cl.grupo28.hestia.features.account

import android.content.Context
import androidx.credentials.*
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.security.MessageDigest
import java.util.UUID

class GoogleIdentity(val idToken: String, val rawNonce: String)
interface GoogleAccountPicker {
    suspend fun pick(activityContext: Context): GoogleIdentity
    suspend fun clear()
}
class CredentialGoogleAccountPicker(context: Context, private val webClientId: String) : GoogleAccountPicker {
    private val manager = CredentialManager.create(context.applicationContext)
    override suspend fun pick(activityContext: Context): GoogleIdentity {
        val raw = UUID.randomUUID().toString() + UUID.randomUUID().toString()
        val hash = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }
        val option = GetSignInWithGoogleOption.Builder(webClientId).setNonce(hash).build()
        val response = manager.getCredential(activityContext, GetCredentialRequest.Builder().addCredentialOption(option).build())
        val credential = response.credential
        require(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)
        return GoogleIdentity(GoogleIdTokenCredential.createFrom(credential.data).idToken, raw)
    }
    override suspend fun clear() { manager.clearCredentialState(ClearCredentialStateRequest()) }
}
