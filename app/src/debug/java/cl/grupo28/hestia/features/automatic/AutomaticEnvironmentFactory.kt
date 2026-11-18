package cl.grupo28.hestia.features.automatic

import android.content.Context
import cl.grupo28.hestia.data.auth.KeystoreSessionStore
import cl.grupo28.hestia.data.automatic.*
import cl.grupo28.hestia.domain.auth.*
import cl.grupo28.hestia.domain.automatic.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

object AutomaticEnvironmentFactory {
    private var local:LocalDemoGateway?=null
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    @Synchronized private fun local(context:Context):LocalDemoGateway=local?:LocalDemoGateway(context.applicationContext).also{demo->local=demo;scope.launch{demo.enabled.drop(1).collect{selected->
        if(selected)AutomaticNotifications.pauseForLocalDemo(context.applicationContext)
        else AutomaticNotifications(context.applicationContext,AutomaticApi(KeystoreSessionStore(context.applicationContext))).restoreAfterProcessStart()
    }};scope.launch { demo.selection.drop(1).collect { selected ->
        val saved=DemoMonitor.read(context.applicationContext)
        if(saved.optBoolean("active") && (!selected.enabled || saved.optString("persona")!=selected.persona)) DemoMonitor.stop(context.applicationContext)
    } }}
    fun demoGateway(context:Context)=local(context)
    fun mode(context:Context):StateFlow<Boolean> = local(context).enabled
    fun externalAllowed(context:Context)=!mode(context).value
    fun create(context:Context,auth:StateFlow<AuthState>):AutomaticEnvironment {
        val demo=local(context);val real=AutomaticApi(KeystoreSessionStore(context))
        val effective=combine(auth,demo.selection){a,s->if(s.enabled)AuthState(true,Account(LocalDemoGateway.owner(s.persona),if(s.persona=="alice")"Ana · DEMO LOCAL" else "Bruno · DEMO LOCAL"),verified=true)else a}
            .stateIn(scope,SharingStarted.Eagerly,if(demo.selection.value.enabled)AuthState(true,Account(LocalDemoGateway.owner(demo.selection.value.persona),"DEMO LOCAL"),verified=true)else auth.value)
        val routed=object:AutomaticGateway {
            private fun target()=if(demo.selection.value.enabled)demo else real
            override val configured get()=target().configured
            override suspend fun status(owner:String)=target().status(owner)
            override suspend fun summary(owner:String)=target().summary(owner)
            override suspend fun alerts(owner:String,before:String?)=target().alerts(owner,before)
            override suspend fun alert(owner:String,id:String)=target().alert(owner,id)
            override suspend fun connect(owner:String,code:String)=target().connect(owner,code)
            override suspend fun sync(owner:String)=target().sync(owner)
            override suspend fun disconnect(owner:String,deleteEvents:Boolean)=target().disconnect(owner,deleteEvents)
            override suspend fun register(owner:String,installation:String,token:String,enabled:Boolean)=target().register(owner,installation,token,enabled)
            override suspend fun unregister(owner:String,installation:String)=target().unregister(owner,installation)
        }
        val actual=AutomaticNotifications(context,real)
        val notifications=object:AutomaticNotificationActions {
            override suspend fun registerIfEnabled(owner:String){if(!demo.selection.value.enabled)actual.registerIfEnabled(owner)}
            override suspend fun enable(owner:String)=if(demo.selection.value.enabled)false else actual.enable(owner)
            override suspend fun unregister(owner:String){if(!demo.selection.value.enabled)actual.unregister(owner)}
            override fun disable(){if(!demo.selection.value.enabled)actual.disable()}
        }
        return AutomaticEnvironment(routed,effective,notifications,demo)
    }
}
