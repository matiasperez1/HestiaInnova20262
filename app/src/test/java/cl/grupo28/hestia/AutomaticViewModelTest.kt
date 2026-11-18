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
class AutomaticViewModelTest {
 private val dispatcher=StandardTestDispatcher()
 @Before fun before(){Dispatchers.setMain(dispatcher)}
 @After fun after(){Dispatchers.resetMain()}
 private class Api:AutomaticGateway {
  override var configured=true;var disconnects=0;var lastDelete:Boolean?=null;var current=AutomaticStatus("DISCONNECTED",false,null,null,null);var connects=0;var failure:String?=null;var suspended:CompletableDeferred<AutomaticStatus>?=null
  override suspend fun status(owner:String)=suspended?.await()?:current
  override suspend fun connect(owner:String,code:String):AutomaticStatus {connects++;failure?.let{throw AutomaticError(it)};return AutomaticStatus("ACTIVE",true,null,"2099-01-01T00:00:00Z","2026-11-16T00:00:00Z")}
  override suspend fun sync(owner:String){}
  override suspend fun disconnect(owner:String,deleteEvents:Boolean):Boolean {disconnects++;lastDelete=deleteEvents;return false}
  override suspend fun summary(owner:String)=AutomaticSummary(0,0,"2026-11-16T00:00:00Z","2026-11-23T00:00:00Z")
  override suspend fun alerts(owner:String,before:String?)=AlertPage(emptyList(),null)
  override suspend fun alert(owner:String,id:String)=GmailAlert(id,"2026-11-16T00:00:00Z","HIGH","Subject","Sender",listOf("Reason"),false)
  override suspend fun register(owner:String,installation:String,token:String,enabled:Boolean){}
  override suspend fun unregister(owner:String,installation:String){}
 }
 private val auth=MutableStateFlow(AuthState(true,Account("owner","a@example.invalid"),verified=true))
 @Test fun neverAuthorizesBeforeBackendAndVerifiedAccount()=runTest(dispatcher){val api=Api().apply{configured=false};val m=AutomaticViewModel(api,auth);advanceUntilIdle();m.begin{_,_->error("must not authorize")};assertFalse(m.state.value.busy);assertEquals(0,api.connects)}
 @Test fun onlyOnePendingCodeAndRequiredReadonlyScope()=runTest(dispatcher){val api=Api();val m=AutomaticViewModel(api,auth);advanceUntilIdle();var id="";m.begin{_,r->id=r};m.authorized("wrong","code",listOf());assertTrue(m.state.value.busy);m.authorized(id,"code",emptyList());advanceUntilIdle();assertEquals(0,api.connects);assertFalse(m.state.value.busy)}
 @Test fun missingRefreshRequiresExplicitReconsent()=runTest(dispatcher){val api=Api().apply{failure="REFRESH_TOKEN_REQUIRED"};val m=AutomaticViewModel(api,auth);advanceUntilIdle();var id="";m.begin{_,r->id=r};m.authorized(id,"code",listOf("https://www.googleapis.com/auth/gmail.readonly"));advanceUntilIdle();assertTrue(m.state.value.reconsent);assertFalse(m.state.value.status?.active==true)}
 @Test fun logoutDiscardsInFlightPrivateData()=runTest(dispatcher){val api=Api().apply{suspended=CompletableDeferred()};val m=AutomaticViewModel(api,auth);runCurrent();auth.value=AuthState(true);runCurrent();api.suspended!!.complete(AutomaticStatus("ACTIVE",true,null,null,null));advanceUntilIdle();assertNull(m.state.value.status);assertTrue(m.state.value.alerts.isEmpty())}
 @Test fun disconnectDoesNotClaimRemoteRevocation()=runTest(dispatcher){val m=AutomaticViewModel(Api(),auth);advanceUntilIdle();m.disconnect(true);advanceUntilIdle();assertTrue(m.state.value.message!!.contains("No se confirmó"));assertNotNull(auth.value.account)}
 @Test fun manualRevocationAlsoDisconnectsServerWithoutDeletingUnconfirmedEvents()=runTest(dispatcher){val api=Api().apply{current=AutomaticStatus("ACTIVE",true,null,"2099-01-01T00:00:00Z","2026-11-16T00:00:00Z")};val m=AutomaticViewModel(api,auth);advanceUntilIdle();assertEquals(false,m.disconnectForManualRevocation());assertEquals(1,api.disconnects);assertEquals(false,api.lastDelete);assertFalse(m.state.value.status!!.active);assertNotNull(auth.value.account)}
}
