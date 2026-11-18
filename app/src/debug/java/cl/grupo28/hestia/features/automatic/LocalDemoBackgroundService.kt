package cl.grupo28.hestia.features.automatic

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.AtomicFile
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import cl.grupo28.hestia.MainActivity
import cl.grupo28.hestia.R
import cl.grupo28.hestia.data.automatic.LocalDemoGateway
import cl.grupo28.hestia.domain.automatic.AutomaticError
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import java.io.File

/** Debug-only local monitor. It never accesses the real account or FCM. */
object DemoMonitor {
    val state=MutableStateFlow("Desactivados")
    private fun file(context:Context)=AtomicFile(File(context.noBackupFilesDir,"hestia-demo-monitor"))
    @Synchronized fun read(context:Context):JSONObject = runCatching { JSONObject(file(context).openRead().bufferedReader().use { it.readText() }) }.getOrElse { JSONObject() }
    @Synchronized fun save(context:Context,value:JSONObject) { val f=file(context);val out=f.startWrite();try { out.write(value.toString().toByteArray());f.finishWrite(out) } catch(e:Exception) { f.failWrite(out);throw e } }
    @Synchronized fun advance(context:Context,persona:String,cursor:String):Boolean {
        val current=read(context)
        if(!current.optBoolean("active") || current.optString("persona")!=persona)return false
        save(context,current.put("cursor",cursor));return true
    }
    fun localAllowed(context:Context)=Build.VERSION.SDK_INT<37 || ContextCompat.checkSelfPermission(context,"android.permission.ACCESS_LOCAL_NETWORK")==PackageManager.PERMISSION_GRANTED
    fun notificationsAllowed(context:Context)=NotificationManagerCompat.from(context).areNotificationsEnabled() && (context.getSystemService(NotificationManager::class.java).getNotificationChannel(LocalDemoBackgroundService.ALERT_CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE) && (Build.VERSION.SDK_INT<33 || ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)
    fun start(context:Context,persona:String) {
        if(!notificationsAllowed(context)){state.value="Permiso de notificaciones pendiente";return}
        if(!localAllowed(context)){state.value="Permiso de red local pendiente";return}
        save(context,JSONObject().put("active",true).put("persona",persona))
        state.value="Activando"
        try { ContextCompat.startForegroundService(context,Intent(context,LocalDemoBackgroundService::class.java)) }
        catch(_:Exception) { save(context,JSONObject().put("active",false));state.value="Servicio detenido por el sistema" }
    }
    fun stop(context:Context) { save(context,JSONObject().put("active",false));context.stopService(Intent(context,LocalDemoBackgroundService::class.java));state.value="Desactivados" }
}

class LocalDemoBackgroundService:Service() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private var monitor:Job?=null
    private val manager by lazy { getSystemService(NotificationManager::class.java) }
    override fun onBind(intent:Intent?):IBinder?=null
    override fun onCreate() { super.onCreate();manager.createNotificationChannel(NotificationChannel(STATE_CHANNEL,"Estado de DEMO LOCAL",NotificationManager.IMPORTANCE_LOW));manager.createNotificationChannel(NotificationChannel(ALERT_CHANNEL,"Alertas de DEMO LOCAL",NotificationManager.IMPORTANCE_HIGH)) }
    private fun status(text:String):Notification = NotificationCompat.Builder(this,STATE_CHANNEL).setSmallIcon(R.drawable.ic_hestia_notification).setContentTitle("Hestia · DEMO LOCAL").setContentText(text).setOngoing(true).setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        val saved=DemoMonitor.read(this); val persona=saved.optString("persona")
        val gateway=AutomaticEnvironmentFactory.demoGateway(this)
        if(!saved.optBoolean("active") || persona !in listOf("alice","bob") || !gateway.selection.value.enabled || gateway.selection.value.persona!=persona || !DemoMonitor.notificationsAllowed(this) || !DemoMonitor.localAllowed(this)) { DemoMonitor.state.value="Servicio detenido por el sistema";stopSelf();return START_NOT_STICKY }
        startForeground(STATUS_ID,status("Iniciando avisos de demostración"))
        if(monitor?.isActive!=true) monitor=scope.launch { runMonitor(gateway,persona) }
        return START_STICKY
    }
    private suspend fun runMonitor(gateway:LocalDemoGateway,persona:String) {
        val owner=LocalDemoGateway.owner(persona);var delayMs=5000L
        while(currentCoroutineContext().isActive) {
            try {
                val saved=DemoMonitor.read(this)
                if(!saved.optBoolean("active") || saved.optString("persona")!=persona || !gateway.selection.value.enabled || gateway.selection.value.persona!=persona || !DemoMonitor.notificationsAllowed(this) || !DemoMonitor.localAllowed(this)) { DemoMonitor.state.value="Servicio detenido por el sistema";stopSelf();return }
                var cursor=if(saved.has("cursor")) saved.getString("cursor") else {
                    val baseline=gateway.notifications(owner,null).nextCursor
                    if(!DemoMonitor.advance(this,persona,baseline)) { stopSelf();return };baseline
                }
                var more:Boolean
                do {
                    val page=gateway.notifications(owner,cursor)
                    for(item in page.items) {
                        if(!gateway.selection.value.enabled || gateway.selection.value.persona!=persona) { stopSelf();return }
                        val intent=Intent(this,MainActivity::class.java).putExtra("hestia_type","DEMO_LOCAL").putExtra("hestia_event_id",item.eventId).putExtra("hestia_account_tag","demo:$persona").setAction("cl.grupo28.hestia.DEMO.$persona.${item.eventId}").addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        val pending=PendingIntent.getActivity(this,0,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                        val n=NotificationCompat.Builder(this,ALERT_CHANNEL).setSmallIcon(R.drawable.ic_hestia_notification).setContentTitle("Hestia · DEMO LOCAL").setContentText("Hemos detectado un correo sospechoso. No compartas claves ni códigos.").setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setContentIntent(pending).setAutoCancel(true).setOnlyAlertOnce(true).build()
                        if(!DemoMonitor.notificationsAllowed(this)) throw SecurityException("NOTIFICATIONS_DISABLED")
                        try { NotificationManagerCompat.from(this).notify("demo:$persona:${item.eventId}",0,n) } catch(e:SecurityException) { throw e }
                        val now=DemoMonitor.read(this)
                        if(!now.optBoolean("active") || now.optString("persona")!=persona) { stopSelf();return }
                        cursor=item.cursor;if(!DemoMonitor.advance(this,persona,cursor)) { stopSelf();return }
                    }
                    more=page.hasMore
                } while(more && currentCoroutineContext().isActive)
                DemoMonitor.state.value="Activos";manager.notify(STATUS_ID,status("Avisos de demostración activos"));delayMs=5000L
            } catch(c:CancellationException) { throw c } catch(e:Exception) {
                if((e as? AutomaticError)?.code in listOf("DEMO_UNAUTHENTICATED","OWNER_CHANGED")) {
                    DemoMonitor.save(this,DemoMonitor.read(this).put("active",false));DemoMonitor.state.value="Servicio detenido por el sistema";stopSelf();return
                }
                DemoMonitor.state.value="Sin conexión con el computador";manager.notify(STATUS_ID,status("Conexión local interrumpida; reintentando"));delayMs=(delayMs*2).coerceAtMost(60000L)
            }
            delay(delayMs)
        }
    }
    override fun onTimeout(startId:Int,fgsType:Int) { DemoMonitor.save(this,DemoMonitor.read(this).put("active",false));DemoMonitor.state.value="Servicio detenido por el sistema";stopSelf() }
    override fun onDestroy() { scope.cancel();if(DemoMonitor.state.value!="Desactivados" && DemoMonitor.state.value!="Servicio detenido por el sistema")DemoMonitor.state.value="Servicio detenido por el sistema";super.onDestroy() }
    companion object { const val STATE_CHANNEL="hestia_demo_status";const val ALERT_CHANNEL="hestia_demo_alert";const val STATUS_ID=280222 }
}
