package cl.grupo28.hestia.data.automatic

import android.content.Context
import android.util.AtomicFile
import cl.grupo28.hestia.BuildConfig
import cl.grupo28.hestia.domain.auth.*
import cl.grupo28.hestia.domain.automatic.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.net.Proxy
import java.util.concurrent.TimeUnit

data class DemoDelivery(val eventId:String,val cursor:String)
data class DemoDeliveryPage(val items:List<DemoDelivery>,val nextCursor:String,val hasMore:Boolean)
interface DemoModeStore { fun read():DemoSelection?; fun save(selection:DemoSelection) }
class FileDemoModeStore(context:Context):DemoModeStore {
    private val file=AtomicFile(File(context.noBackupFilesDir,"hestia-local-demo-mode"))
    override fun read():DemoSelection?=runCatching{val j=JSONObject(file.openRead().bufferedReader().use{it.readText()});DemoSelection(j.getBoolean("enabled"),j.getString("persona"))}.getOrNull()
    override fun save(selection:DemoSelection){val output=file.startWrite();try{output.write(JSONObject().put("enabled",selection.enabled).put("persona",selection.persona).toString().toByteArray());file.finishWrite(output)}catch(e:Exception){file.failWrite(output);throw e}}
}
/** Debug source set only. Never opens the real session store or a Google endpoint. */
class LocalDemoGateway(private val modes:DemoModeStore,private val base:String=BuildConfig.LOCAL_DEMO_API_URL,defaultEnabled:Boolean=BuildConfig.LOCAL_DEMO_DEFAULT,private val localPermission:()->Boolean={true}):AutomaticGateway,DemoControls {
    constructor(context:Context,base:String=BuildConfig.LOCAL_DEMO_API_URL):this(FileDemoModeStore(context),base,BuildConfig.LOCAL_DEMO_DEFAULT,{android.os.Build.VERSION.SDK_INT<37||androidx.core.content.ContextCompat.checkSelfPermission(context,"android.permission.ACCESS_LOCAL_NETWORK")==android.content.pm.PackageManager.PERMISSION_GRANTED})

    companion object {fun owner(persona:String)=when(persona){"alice"->"d0000000-0000-4000-8000-000000000001";"bob"->"d0000000-0000-4000-8000-000000000002";else->throw AutomaticError("UNKNOWN_DEMO_PERSONA")}}
    private val saved=modes.read()?:DemoSelection()
    private val mutable=MutableStateFlow(DemoSelection(defaultEnabled||saved.enabled,saved.persona.takeIf{it in listOf("alice","bob")}?:"alice"))
    override val selection=mutable.asStateFlow()
    val enabled=MutableStateFlow(selection.value.enabled)
    override val configured=true
    private val lock=Mutex();private var token:String?=null;private var tokenOwner:String?=null;private var expires=0L
    private val url=java.net.URI(base).also{require(it.scheme=="http"&&it.host in listOf("10.0.2.2","127.0.0.1","localhost")&&it.userInfo==null&&it.query==null&&it.fragment==null&&it.path.orEmpty() in listOf("","/"))}
    private val client=OkHttpClient.Builder().proxy(Proxy.NO_PROXY).followRedirects(false).followSslRedirects(false).callTimeout(4,TimeUnit.SECONDS).addInterceptor{chain->
        val request=chain.request();if(request.url.host!=url.host||request.url.port!=url.port||request.url.scheme!="http")throw java.io.IOException("DEMO_EXTERNAL_NETWORK_BLOCKED")
        chain.proceed(request.newBuilder().header("X-Hestia-Demo","true").build())
    }.build()
    private val credentials=object:AutomaticCredentials {
        override fun bearer(owner:String):String {if(!isCurrent(owner)||tokenOwner!=owner)throw AutomaticError("OWNER_CHANGED");return token?:throw AutomaticError("DEMO_UNAUTHENTICATED")}
        override fun isCurrent(owner:String)=selection.value.enabled&&owner==LocalDemoGateway.owner(selection.value.persona)
    }
    private val forbiddenSessions=object:SessionStore {override fun load():AuthSession?=error("REAL_SESSION_FORBIDDEN_IN_DEMO");override fun save(session:AuthSession){error("REAL_SESSION_FORBIDDEN_IN_DEMO")};override fun clear(){error("REAL_SESSION_FORBIDDEN_IN_DEMO")}}
    private val api=AutomaticApi(forbiddenSessions,base,client,credentials)
    override fun select(enabled:Boolean,persona:String){owner(persona);token=null;tokenOwner=null;expires=0
        val state=DemoSelection(enabled,persona);modes.save(state)
        mutable.value=state;this.enabled.value=enabled
    }
    private suspend fun request(path:String,body:JSONObject?=null,bearer:String?=null):JSONObject=withContext(Dispatchers.IO){
        val builder=Request.Builder().url(base.trimEnd('/')+path);if(bearer!=null)builder.header("Authorization","Bearer $bearer")
        builder.method(if(body==null)"GET" else "POST",body?.toString()?.toRequestBody("application/json".toMediaType()))
        client.newCall(builder.build()).execute().use{response->val source=response.body?.source()?:throw AutomaticError("DEMO_UNAVAILABLE");if(source.request(512001))throw AutomaticError("INVALID_RESPONSE");val json=JSONObject(source.readUtf8());if(!response.isSuccessful)throw AutomaticError(json.optString("error","DEMO_UNAVAILABLE"));json}
    }
    private suspend fun ensure(expected:String)=lock.withLock {
        if(!localPermission())throw AutomaticError("DEMO_LOCAL_PERMISSION_REQUIRED")
        if(!credentials.isCurrent(expected))throw AutomaticError("OWNER_CHANGED")
        if(tokenOwner!=expected||token==null||expires<System.currentTimeMillis()){
            val current=selection.value;val j=request("/demo/session",JSONObject().put("persona",current.persona));if(j.getString("userId")!=expected||selection.value!=current)throw AutomaticError("OWNER_CHANGED")
            token=j.getString("token");tokenOwner=expected;expires=System.currentTimeMillis()+11*3600000
        }
    }
    internal suspend fun call(owner:String,path:String,body:JSONObject?=null):JSONObject {ensure(owner);val result=request(path,body,credentials.bearer(owner));if(!credentials.isCurrent(owner))throw AutomaticError("OWNER_CHANGED");return result}
    suspend fun notifications(owner:String,after:String?):DemoDeliveryPage {
        require(after==null || after.matches(Regex("[A-Za-z0-9_-]{1,128}")))
        val j=call(owner,"/demo/notifications?limit=50"+(after?.let{"&after=$it"}?:""))
        if(j.getString("identity")!=owner)throw AutomaticError("OWNER_CHANGED")
        val items=j.getJSONArray("items")
        return DemoDeliveryPage(List(items.length()){val n=items.getJSONObject(it);DemoDelivery(n.getString("event_id"),n.getString("cursor"))},j.getString("nextCursor"),j.getBoolean("hasMore"))
    }
    override suspend fun status(owner:String):AutomaticStatus{ensure(owner);return api.status(owner)}
    override suspend fun summary(owner:String):AutomaticSummary{ensure(owner);return api.summary(owner)}
    override suspend fun alerts(owner:String,before:String?):AlertPage{ensure(owner);return api.alerts(owner,before)}
    override suspend fun alert(owner:String,id:String):GmailAlert{ensure(owner);return api.alert(owner,id)}
    override suspend fun connect(owner:String,code:String):AutomaticStatus=throw AutomaticError("OAUTH_DISABLED_IN_DEMO")
    override suspend fun sync(owner:String){throw AutomaticError("USE_DEMO_SIMULATION")}
    override suspend fun disconnect(owner:String,deleteEvents:Boolean):Boolean=throw AutomaticError("USE_DEMO_CLEANUP")
    override suspend fun register(owner:String,installation:String,token:String,enabled:Boolean){throw AutomaticError("FCM_DISABLED_IN_DEMO")}
    override suspend fun unregister(owner:String,installation:String){throw AutomaticError("FCM_DISABLED_IN_DEMO")}
    override suspend fun info(owner:String):DemoInfo {val j=call(owner,"/demo/status");val n=j.getJSONArray("notifications");val s=j.getJSONArray("scenarios");return DemoInfo(j.getInt("processed"),j.getInt("pending"),j.getInt("retries"),j.getInt("duplicates"),j.getBoolean("workerRunning"),if(j.isNull("last_simulation"))null else j.getString("last_simulation"),List(n.length()){val v=n.getJSONObject(it);DemoNotice(v.getString("event_id"),v.getString("created_at"))},j.getInt("notificationCount"),List(s.length()){val v=s.getJSONObject(it);DemoScenario(v.getString("id"),v.getString("label"))})}
    override suspend fun emit(owner:String,scenario:String,retryOnce:Boolean){call(owner,"/demo/emit",JSONObject().put("scenario",scenario).put("retryOnce",retryOnce))}
    override suspend fun repeat(owner:String){call(owner,"/demo/repeat",JSONObject())}
    override suspend fun clear(owner:String){call(owner,"/demo/clear",JSONObject().put("confirm",true))}
}
