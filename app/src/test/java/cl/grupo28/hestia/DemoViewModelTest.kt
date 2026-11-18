package cl.grupo28.hestia

import cl.grupo28.hestia.domain.auth.*
import cl.grupo28.hestia.domain.automatic.*
import cl.grupo28.hestia.features.automatic.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class DemoViewModelTest {
 private val dispatcher=StandardTestDispatcher()
 @Before fun before(){Dispatchers.setMain(dispatcher)}
 @After fun after(){Dispatchers.resetMain()}
 private class Demo:DemoControls {
  override val selection=MutableStateFlow(DemoSelection(true))
  var emitted=0;var repeated=0;var cleared=0
  override fun select(enabled:Boolean,persona:String){selection.value=DemoSelection(enabled,persona)}
  override suspend fun info(owner:String)=DemoInfo(emitted,0,0,repeated,true,null,emptyList(),emitted,listOf(DemoScenario("high-risk","Prueba")))
  override suspend fun emit(owner:String,scenario:String,retryOnce:Boolean){emitted++}
  override suspend fun repeat(owner:String){repeated++}
  override suspend fun clear(owner:String){cleared++;emitted=0;repeated=0}
 }
 private class Api:AutomaticGateway {
  override val configured=true;var offline=false;var hold:CompletableDeferred<AutomaticStatus>?=null
  override suspend fun status(owner:String):AutomaticStatus{if(offline)throw java.io.IOException();return hold?.await()?:AutomaticStatus("DEMO_LOCAL",false,null,null,"2026-11-16T00:00:00Z")}
  override suspend fun summary(owner:String)=AutomaticSummary(0,0,"2026-11-16T00:00:00Z","2026-11-23T00:00:00Z")
  override suspend fun alerts(owner:String,before:String?)=AlertPage(emptyList(),null)
  override suspend fun alert(owner:String,id:String)=GmailAlert(id,"2026-11-16T00:00:00Z","HIGH","[DEMO LOCAL] Prueba","sender@example.invalid",listOf("Prueba"),false)
  override suspend fun connect(owner:String,code:String):AutomaticStatus=error("OAuth must not be called")
  override suspend fun sync(owner:String)=error("Use demo emit")
  override suspend fun disconnect(owner:String,deleteEvents:Boolean):Boolean=error("Must not revoke real Gmail")
  override suspend fun register(owner:String,installation:String,token:String,enabled:Boolean)=error("No FCM")
  override suspend fun unregister(owner:String,installation:String)=error("No FCM")
 }
 private val auth=MutableStateFlow(AuthState(true,Account("demo","DEMO LOCAL"),verified=true))
 @Test fun simulationAndDuplicateActionsUseDemoPortOnly()=runTest(dispatcher){val demo=Demo();val model=AutomaticViewModel(Api(),auth,demo=demo);advanceUntilIdle();model.emitDemo("high-risk");advanceUntilIdle();model.repeatDemo();advanceUntilIdle();assertEquals(1,demo.emitted);assertEquals(1,demo.repeated);assertTrue(model.state.value.demoSelected);assertFalse(model.state.value.status!!.active)}
 @Test fun cleanupOnlyUsesSyntheticStore()=runTest(dispatcher){val demo=Demo();val model=AutomaticViewModel(Api(),auth,demo=demo);advanceUntilIdle();model.clearDemo();advanceUntilIdle();assertEquals(1,demo.cleared);assertEquals("demo",auth.value.account!!.id)}
 @Test fun unavailableBackendIsReportedWithoutInventingCounts()=runTest(dispatcher){val model=AutomaticViewModel(Api().apply{offline=true},auth,demo=Demo());advanceUntilIdle();assertFalse(model.state.value.checked);assertNull(model.state.value.summary);assertNull(model.state.value.demoInfo);assertTrue(model.state.value.message!!.contains("DEMO LOCAL"))}
 @Test fun demoCanNeverTriggerOAuthOrRealRevocation()=runTest(dispatcher){val model=AutomaticViewModel(Api(),auth,demo=Demo());advanceUntilIdle();model.begin{_,_->error("Must not authorize")};assertNull(model.disconnectForManualRevocation());assertNull(model.beforeLogout());assertFalse(model.state.value.busy)}
 @Test fun userSimulationPreemptsBackgroundPollingInsteadOfBeingDiscarded()=runTest(dispatcher){
  val api=Api();val demo=Demo();val model=AutomaticViewModel(api,auth,demo=demo);advanceUntilIdle();api.hold=CompletableDeferred();model.foreground(true);runCurrent();assertFalse(model.state.value.busy);model.emitDemo("high-risk");runCurrent();assertEquals(1,demo.emitted);model.foreground(false);api.hold!!.complete(AutomaticStatus("DEMO_LOCAL",false,null,null,"2026-11-16T00:00:00Z"));advanceUntilIdle();assertFalse(model.state.value.busy)
 }
}
