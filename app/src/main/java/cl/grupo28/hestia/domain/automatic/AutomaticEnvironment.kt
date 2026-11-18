package cl.grupo28.hestia.domain.automatic

import cl.grupo28.hestia.domain.auth.AuthState
import kotlinx.coroutines.flow.StateFlow

data class DemoSelection(val enabled:Boolean=false,val persona:String="alice")
data class DemoScenario(val id:String,val label:String)
data class DemoNotice(val eventId:String,val createdAt:String)
data class DemoInfo(val processed:Int,val pending:Int,val retries:Int,val duplicates:Int,val workerRunning:Boolean,val lastSimulation:String?,val notifications:List<DemoNotice>,val notificationCount:Int,val scenarios:List<DemoScenario>)
interface DemoControls {
    val selection:StateFlow<DemoSelection>
    fun select(enabled:Boolean,persona:String=selection.value.persona)
    suspend fun info(owner:String):DemoInfo
    suspend fun emit(owner:String,scenario:String,retryOnce:Boolean)
    suspend fun repeat(owner:String)
    suspend fun clear(owner:String)
}
interface AutomaticNotificationActions {
    suspend fun registerIfEnabled(owner:String)
    suspend fun enable(owner:String):Boolean
    suspend fun unregister(owner:String)
    fun disable()
}
data class AutomaticEnvironment(val gateway:AutomaticGateway,val auth:StateFlow<AuthState>,val notifications:AutomaticNotificationActions?,val demo:DemoControls?=null)
interface AutomaticCredentials {
    fun bearer(owner:String):String
    fun isCurrent(owner:String):Boolean
}
