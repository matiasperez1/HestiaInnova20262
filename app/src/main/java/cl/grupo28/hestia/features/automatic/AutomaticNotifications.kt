package cl.grupo28.hestia.features.automatic

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import cl.grupo28.hestia.BuildConfig
import cl.grupo28.hestia.MainActivity
import cl.grupo28.hestia.R
import cl.grupo28.hestia.data.auth.KeystoreSessionStore
import cl.grupo28.hestia.data.automatic.AutomaticApi
import cl.grupo28.hestia.domain.automatic.AutomaticGateway
import cl.grupo28.hestia.domain.automatic.AutomaticNotificationActions
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** Consent/installation metadata only, in noBackupFilesDir. No OAuth or FCM token is stored here. */
class AutomaticNotifications(private val context:Context,private val api:AutomaticGateway):AutomaticNotificationActions{
    companion object {
        private val consentLock=Any()
        const val CHANNEL="hestia_gmail_risk"
        fun pauseForLocalDemo(context:Context){
            if(FirebaseApp.getApps(context).isNotEmpty())FirebaseMessaging.getInstance().isAutoInitEnabled=false
            NotificationManagerCompat.from(context).cancelAll()
        }
        fun ready()=listOf(BuildConfig.FIREBASE_APP_ID,BuildConfig.FIREBASE_API_KEY,BuildConfig.FIREBASE_PROJECT_ID,BuildConfig.FIREBASE_SENDER_ID).all{it.isNotBlank()}
        fun allowed(context:Context)=NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT<33||ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)
        @Synchronized fun initialize(context:Context):Boolean {if(!ready())return false
            if(FirebaseApp.getApps(context).isEmpty())FirebaseApp.initializeApp(context,FirebaseOptions.Builder().setApplicationId(BuildConfig.FIREBASE_APP_ID).setApiKey(BuildConfig.FIREBASE_API_KEY).setProjectId(BuildConfig.FIREBASE_PROJECT_ID).setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID).build())
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL,"Alertas de correos",NotificationManager.IMPORTANCE_HIGH))
            return true
        }
        fun accountTag(id:String)=MessageDigest.getInstance("SHA-256").digest(id.toByteArray()).joinToString(""){"%02x".format(it)}
    }
    private val file=File(context.noBackupFilesDir,"hestia-notification-consent")
    private fun read():JSONObject=runCatching{JSONObject(file.readText())}.getOrElse{JSONObject().put("installation",UUID.randomUUID().toString())}
    @Synchronized private fun save(j:JSONObject){android.util.AtomicFile(file).let{a->val output=a.startWrite();try{output.write(j.toString().toByteArray());a.finishWrite(output)}catch(e:Exception){a.failWrite(output);throw e}}}
    fun restoreAfterProcessStart(){
        if(!ready())return
        val j=read();if(!j.optBoolean("enabled"))return
        val owner=runCatching{KeystoreSessionStore(context).load()?.account?.id}.getOrNull()?:return
        if(j.optString("owner")==owner&&initialize(context))FirebaseMessaging.getInstance().isAutoInitEnabled=true
    }
    override suspend fun enable(owner:String):Boolean=withContext(Dispatchers.IO){if(!initialize(context)||!allowed(context))return@withContext false
        val j=read().put("owner",owner).put("enabled",true);save(j);FirebaseMessaging.getInstance().isAutoInitEnabled=true
        registerIfEnabled(owner);true
    }
    override suspend fun registerIfEnabled(owner:String)=withContext(Dispatchers.IO){val j=read();if(j.optString("owner")!=owner||!j.optBoolean("enabled")||!initialize(context))return@withContext
        val token=FirebaseMessaging.getInstance().token.await();api.register(owner,j.getString("installation"),token,allowed(context))
    }
    suspend fun rotated(token:String)=withContext(Dispatchers.IO){val j=read();val session=KeystoreSessionStore(context).load()?:return@withContext
        if(j.optString("owner")==session.account.id&&j.optBoolean("enabled"))api.register(session.account.id,j.getString("installation"),token,allowed(context))
    }
    override suspend fun unregister(owner:String)=withContext(Dispatchers.IO){val j=read();if(j.optString("owner")==owner)try { disable(); api.unregister(owner,j.getString("installation")) } finally { if(FirebaseApp.getApps(context).isNotEmpty()) runCatching { FirebaseMessaging.getInstance().deleteToken().await() } }}
    override fun disable(){ synchronized(consentLock) {val j=read().put("enabled",false).removeOwner();save(j);if(FirebaseApp.getApps(context).isNotEmpty())FirebaseMessaging.getInstance().isAutoInitEnabled=false
        NotificationManagerCompat.from(context).cancelAll()
    } }
    private fun JSONObject.removeOwner():JSONObject{remove("owner");return this}
    fun receive(data:Map<String,String>){ synchronized(consentLock) {
        val event=data["eventId"]?:return;val tag=data["accountTag"]?:return
        if(data["type"]!="hestia_gmail_alert"||!event.matches(Regex("[a-fA-F0-9-]{36}"))||!allowed(context))return
        val session=runCatching{KeystoreSessionStore(context).load()}.getOrNull()?:return
        val j=read();if(j.optString("owner")!=session.account.id||!j.optBoolean("enabled")||accountTag(session.account.id)!=tag)return
        if(!initialize(context))return
        val seen=j.optJSONArray("seen")?:org.json.JSONArray()
        if((0 until seen.length()).any{seen.optString(it)==event})return
        val updated=org.json.JSONArray(); (maxOf(0,seen.length()-499) until seen.length()).forEach{updated.put(seen.getString(it))};updated.put(event);save(j.put("seen",updated))
        val intent=Intent(context,MainActivity::class.java).putExtra("hestia_event_id",event).putExtra("hestia_account_tag",tag).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending=PendingIntent.getActivity(context,event.hashCode(),intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification=NotificationCompat.Builder(context,CHANNEL).setSmallIcon(R.drawable.ic_hestia_notification)
            .setContentTitle("Hestia: revisa una alerta").setContentText("Un correo requiere tu atención. Abre Hestia para revisarlo.")
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setContentIntent(pending).setAutoCancel(true).setOnlyAlertOnce(true).build()
        try{NotificationManagerCompat.from(context).notify(event,0,notification)}catch(_:SecurityException){}
    } }
}
class HestiaMessagingService:FirebaseMessagingService(){
    override fun onMessageReceived(message:RemoteMessage){if(!AutomaticEnvironmentFactory.externalAllowed(this))return;AutomaticNotifications(this,AutomaticApi(KeystoreSessionStore(this))).receive(message.data)}
    override fun onNewToken(token:String){if(!AutomaticEnvironmentFactory.externalAllowed(this))return;runBlocking {try{withTimeout(8000){AutomaticNotifications(this@HestiaMessagingService,AutomaticApi(KeystoreSessionStore(this@HestiaMessagingService))).rotated(token)}}catch(_:Exception){/* Registration retries on next foreground refresh. */}}}
}
