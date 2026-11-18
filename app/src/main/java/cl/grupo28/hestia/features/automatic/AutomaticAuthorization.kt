package cl.grupo28.hestia.features.automatic

import android.accounts.Account
import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import cl.grupo28.hestia.BuildConfig
import cl.grupo28.hestia.domain.gmail.GMAIL_READONLY
import com.google.android.gms.auth.api.identity.*
import com.google.android.gms.common.api.Scope

/** The one-use code is passed directly to the gateway; saved state contains an opaque request id only. */
class AutomaticAuthorization(private val activity: ComponentActivity, private val model: AutomaticViewModel, saved: Bundle?) {
    private val client=Identity.getAuthorizationClient(activity)
    private var pending=saved?.getString("automatic_authorization_request")
    private val launcher=activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()){ r ->
        val id=pending?:return@registerForActivityResult;pending=null
        if(r.resultCode!=Activity.RESULT_OK)model.failed(id)
        else try{deliver(id,client.getAuthorizationResultFromIntent(r.data))}catch(_:Exception){model.failed(id)}
    }
    fun save(out:Bundle){out.putString("automatic_authorization_request",pending)}
    fun authorize(email:String,id:String){pending=id
        val request=AuthorizationRequest.builder().setAccount(Account(email,"com.google"))
            .setRequestedScopes(listOf(Scope(GMAIL_READONLY))).requestOfflineAccess(BuildConfig.GOOGLE_WEB_CLIENT_ID).build()
        client.authorize(request).addOnSuccessListener{result ->
            if(activity.isDestroyed){pending=null;model.failed(id);return@addOnSuccessListener}
            try{if(result.hasResolution())launcher.launch(IntentSenderRequest.Builder(requireNotNull(result.pendingIntent).intentSender).build())
                else{pending=null;deliver(id,result)}}catch(_:Exception){pending=null;model.failed(id)}
        }.addOnFailureListener{pending=null;model.failed(id)}
    }
    private fun deliver(id:String,result:AuthorizationResult){model.authorized(id,result.serverAuthCode,result.grantedScopes)
        // Offline authorization may also return a short-lived access token. Do not retain it.
        result.accessToken?.takeIf{it.isNotBlank()}?.let{client.clearToken(ClearTokenRequest.builder().setToken(it).build())}
    }
}
