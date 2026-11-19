package cl.grupo28.hestia.features.automatic

import android.content.Context
import androidx.lifecycle.*
import cl.grupo28.hestia.domain.auth.AuthState
import cl.grupo28.hestia.domain.automatic.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.IOException
import java.util.UUID

enum class ConsultationIssue { OFFLINE, ERROR, PERMISSION_REQUIRED }

data class AutomaticUiState(val configured: Boolean=false,val eligible: Boolean=false,val busy: Boolean=false,val checked: Boolean=false,val status: AutomaticStatus?=null,
    val summary: AutomaticSummary?=null,val alerts: List<GmailAlert> = emptyList(),val next: String?=null,val message: String?=null,val reconsent: Boolean=false,val selected: GmailAlert?=null,val demoSelected:Boolean=false,val demoPersona:String="alice",val demoInfo:DemoInfo?=null,
    val consultationIssue: ConsultationIssue? = null)
class AutomaticViewModel(private val gateway: AutomaticGateway, private val auth: StateFlow<AuthState>,private val notifications: AutomaticNotificationActions?=null,private val demo:DemoControls?=null): ViewModel() {
    private val mutable=MutableStateFlow(AutomaticUiState(configured=gateway.configured,demoSelected=demo?.selection?.value?.enabled==true,demoPersona=demo?.selection?.value?.persona?:"alice"));val state=mutable.asStateFlow()
    private var owner: String?=null;private var job: Job?=null;private var pending: String?=null
    private var foreground:Job?=null
    private var polling:Job?=null
    val demoAvailable get()=demo!=null
    init {viewModelScope.launch {auth.collect { a ->
        if(owner!=a.account?.id){job?.cancel();polling?.cancel();pending=null;owner=a.account?.id;mutable.value=AutomaticUiState(configured=gateway.configured,demoSelected=demo?.selection?.value?.enabled==true,demoPersona=demo?.selection?.value?.persona?:"alice")}
        mutable.update{it.copy(eligible=a.verified&&a.account!=null&&!a.busy,configured=gateway.configured)}
        if(a.verified&&!a.busy&&gateway.configured&&state.value.status==null&&!state.value.busy)refresh()
    }}}
    private fun action(run: suspend (String)->Unit){val id=owner?:return;if(!state.value.eligible||state.value.busy)return
        polling?.cancel();polling=null
        mutable.update{it.copy(busy=true,message=null)};job=viewModelScope.launch {try{run(id)}catch(c:CancellationException){throw c}catch(e:Exception){
            if(owner==id) mutable.update { current ->
                val code = (e as? AutomaticError)?.code
                current.copy(
                    message=message(code), reconsent=code=="REFRESH_TOKEN_REQUIRED",
                    checked=current.checked && e !is IOException,
                    consultationIssue=if (e is IOException) ConsultationIssue.OFFLINE else current.consultationIssue,
                )
            }
        }finally{if(owner==id)mutable.update{it.copy(busy=false)}}}}
    private suspend fun load(id: String) {
        try {
            loadResults(id)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (owner != id) return
            val code = (failure as? AutomaticError)?.code
            mutable.update {
                it.copy(checked = false, consultationIssue = when {
                    code == "DEMO_LOCAL_PERMISSION_REQUIRED" -> ConsultationIssue.PERMISSION_REQUIRED
                    failure is IOException -> ConsultationIssue.OFFLINE
                    else -> ConsultationIssue.ERROR
                }, message = message(code))
            }
        }
    }
    private suspend fun loadResults(id:String){val s=gateway.status(id);if(owner!=id)return
        val summary=if(s.startedAt!=null||s.status.startsWith("DISCONNECTED"))gateway.summary(id)else null;val alerts=gateway.alerts(id)
        val info=if(demo?.selection?.value?.enabled==true)demo.info(id)else null
        if(owner==id){mutable.update{it.copy(status=s,checked=true,consultationIssue=null,message=null,demoInfo=info,summary=summary?.takeIf { it.analyzed>0||s.startedAt!=null },alerts=alerts.items,next=alerts.next)};try { notifications?.registerIfEnabled(id) } catch (c:CancellationException) { throw c } catch (_:Exception) { mutable.update { it.copy(message="No pudimos vincular los avisos de este teléfono. Las alertas siguen disponibles aquí.") } }}
    }
    fun refresh()=action {load(it)}
    fun begin(authorize:(String,String)->Unit){if(state.value.demoSelected||!gateway.configured||!state.value.eligible||state.value.busy)return;val email=auth.value.account?.email?:return
        val id=UUID.randomUUID().toString();pending=id;mutable.update{it.copy(busy=true,message=null,reconsent=false)};try{authorize(email,id)}catch(_:Exception){failed(id)}}
    fun authorized(id:String,code:String?,scopes:List<String>){if(pending!=id)return;pending=null;mutable.update{it.copy(busy=false)}
        if(code.isNullOrBlank()||cl.grupo28.hestia.domain.gmail.GMAIL_READONLY !in scopes){mutable.update{it.copy(message="No se concedió la autorización para la revisión automática.")};return}
        action { user -> val s=gateway.connect(user,code);if(owner==user){mutable.update{it.copy(status=s)};load(user)} }
    }
    fun failed(id:String){if(pending!=id)return;pending=null;mutable.update{it.copy(busy=false,message="No se completó la autorización. Puedes continuar con la revisión manual.")}}
    fun sync()=action {id->gateway.sync(id);mutable.update{it.copy(message="Solicitamos una revisión. Actualiza el estado en unos momentos.")}}
    fun more()=action{id->val cursor=state.value.next?:return@action;val page=gateway.alerts(id,cursor);if(owner==id)mutable.update{it.copy(alerts=(it.alerts+page.items).distinctBy{a->a.id},next=page.next)}}
    fun open(id:String)=action{user->
        mutable.update { if (it.selected?.id == id) it else it.copy(selected = null) }
        val a=gateway.alert(user,id)
        if(owner==user)mutable.update{it.copy(selected=a)}
    }
    fun closeDetail(){mutable.update{it.copy(selected=null)}}
    fun disconnect(deleteEvents:Boolean)=action {id->val remote=gateway.disconnect(id,deleteEvents);notifications?.disable();mutable.update{it.copy(status=null,summary=null,alerts=emptyList(),selected=null,message=if(remote)"Revisión automática desconectada." else "Desconectamos Hestia. No se confirmó la revocación en Google; revisa los permisos de tu cuenta Google.")}}
    fun enableNotifications()=action{id->if(notifications?.enable(id)!=true)mutable.update{it.copy(message="Los avisos no están disponibles. Puedes consultar las alertas aquí.")}}
    suspend fun disconnectForManualRevocation():Boolean? {
        if(state.value.demoSelected)return null
        val id=owner?:return null
        if(!gateway.configured)return null
        job?.cancel();pending=null
        return try {
            val current=gateway.status(id)
            if(current.status in listOf("PENDING","DISCONNECTED"))return null
            val confirmed=gateway.disconnect(id,false)
            if(owner==id)mutable.update{it.copy(busy=false,checked=true,status=AutomaticStatus(if(confirmed) "DISCONNECTED" else "DISCONNECTED_REVOCATION_PENDING",false,null,null,null),message="La revisión automática se desconectó junto con el permiso de Google. Los resultados guardados se conservan.")}
            notifications?.disable();confirmed
        } catch(c:CancellationException){throw c} catch(_:Exception){
            if(owner==id)mutable.update{it.copy(busy=false,checked=false,message="No se confirmó la desconexión automática. Revisa su estado cuando vuelvas a tener conexión.")};false
        }
    }
    suspend fun beforeLogout():String? {if(state.value.demoSelected)return null;job?.cancel();pending=null;val id=owner?:return null;var warning:String?=null;try{withTimeout(5000){notifications?.unregister(id)}}catch(_:Exception){warning="No pudimos confirmar la desvinculación remota de los avisos. Este teléfono dejó de mostrarlos; al volver a entrar puedes desvincularlo nuevamente."}finally{notifications?.disable()};return warning}
    fun selectDemo(enabled:Boolean,persona:String=state.value.demoPersona){demo?.select(enabled,persona)}
    fun emitDemo(scenario:String,retryOnce:Boolean=false)=action {id->require(state.value.demoSelected);demo?.emit(id,scenario,retryOnce);load(id)}
    fun repeatDemo()=action{id->require(state.value.demoSelected);demo?.repeat(id);load(id)}
    fun clearDemo()=action{id->require(state.value.demoSelected);demo?.clear(id);mutable.update{it.copy(selected=null)};load(id)}
    fun foreground(active:Boolean){foreground?.cancel();foreground=null;polling?.cancel();polling=null;if(!active||demo==null)return
        foreground=viewModelScope.launch{demo.selection.collectLatest{selection->if(selection.enabled)while(isActive){if(!state.value.busy)pollDemo();delay(2000)}}}
    }
    private fun pollDemo(){
        val id=owner?:return
        if(!state.value.eligible||state.value.busy||polling?.isActive==true)return
        polling=viewModelScope.launch { load(id) }
    }
    private fun message(code:String?)=when(code){"DEMO_LOCAL_PERMISSION_REQUIRED"->"DEMO LOCAL: permite la conexión al computador para consultar las simulaciones.";"REFRESH_TOKEN_REQUIRED"->"Google necesita renovar el permiso para revisar correos automáticamente.";"REAUTH_REQUIRED"->"Vuelve a conectar Gmail para continuar la revisión automática.";"UNAUTHENTICATED"->"Actualiza tu sesión de Hestia e inténtalo de nuevo.";"TRY_LATER"->"Espera un minuto antes de solicitar otra revisión.";else->if(state.value.demoSelected)"No pudimos consultar los correos de demostración. Puedes volver a actualizar." else "No pudimos actualizar la revisión automática. Conservamos los resultados que ya estaban disponibles."}
    class Factory(context:Context,private val auth:StateFlow<AuthState>):ViewModelProvider.Factory{
        private val app=context.applicationContext
        @Suppress("UNCHECKED_CAST") override fun <T:ViewModel> create(modelClass:Class<T>):T {require(modelClass.isAssignableFrom(AutomaticViewModel::class.java));val environment=AutomaticEnvironmentFactory.create(app,auth);return AutomaticViewModel(environment.gateway,environment.auth,environment.notifications,environment.demo) as T}
    }
}
