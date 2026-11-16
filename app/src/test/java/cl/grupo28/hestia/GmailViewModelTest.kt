package cl.grupo28.hestia

import cl.grupo28.hestia.data.gmail.*
import cl.grupo28.hestia.domain.WeekRange
import cl.grupo28.hestia.domain.auth.*
import cl.grupo28.hestia.domain.gmail.*
import cl.grupo28.hestia.features.gmail.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class GmailViewModelTest {
    private val dispatcher=StandardTestDispatcher()
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { Dispatchers.resetMain() }
    private val auth=MutableStateFlow(AuthState(true,Account("owner","test@example.invalid"),verified=true))
    private class Store : GmailStore {
        var value:GmailSnapshot?=null
        override fun load(ownerId:String):GmailSnapshot? { if(value?.ownerId != ownerId) value=null; return value }
        override fun save(snapshot:GmailSnapshot) { value=snapshot }
        override fun clear() { value=null }
    }
    private class Api : GmailGateway {
        var profiles=0
        var lists=0
        var address="test@example.invalid"
        override suspend fun profile(token:String):String { profiles++; return address }
        override suspend fun list(token:String,week:WeekRange,page:String?):GmailPage { lists++; return GmailPage(emptyList(),null) }
        override suspend fun message(token:String,id:String):GmailMessage?=null
    }
    private fun model(api:Api=Api(),store:Store=Store())=GmailViewModel(GmailRepository(api,store,io=dispatcher),auth)
    @Test fun requiresVerifiedHestiaAccountBeforeAskingGmailPermission()=runTest(dispatcher) {
        auth.value=AuthState(true)
        val model=model(); advanceUntilIdle()
        model.begin(false) { _,_->error("Must not ask authorization") }
        assertFalse(model.state.value.eligible); assertFalse(model.state.value.verified)
    }
    @Test fun cancellationDoesNotVerifyGmail()=runTest(dispatcher) {
        val model=model(); advanceUntilIdle(); var id=""
        model.begin(false) { _,request->id=request }
        model.failed(id,"Cancelaste la conexión"); assertFalse(model.state.value.verified); assertFalse(model.state.value.busy)
    }
    @Test fun refusedScopeCannotReachGmailApi()=runTest(dispatcher) {
        val api=Api(); val model=model(api); advanceUntilIdle(); var id=""
        model.begin(false) { _,request->id=request }; model.authorized(id,"test-token",emptyList()); advanceUntilIdle()
        assertEquals(0,api.profiles); assertFalse(model.state.value.verified)
    }
    @Test fun permissionAloneDoesNotVerifyDifferentMailbox()=runTest(dispatcher) {
        val api=Api().apply { address="other@example.invalid" }; val model=model(api); advanceUntilIdle(); var id=""
        model.begin(false) { _,request->id=request }; model.authorized(id,"test-token",listOf(GMAIL_READONLY)); advanceUntilIdle()
        assertFalse(model.state.value.verified); assertTrue(model.state.value.message.orEmpty().contains("no coincide"))
    }
    @Test fun staleAuthorizationCannotConnectAfterAccountChanges()=runTest(dispatcher) {
        val api=Api(); val model=model(api); advanceUntilIdle(); var oldId=""
        model.begin(false) { _,request->oldId=request }
        auth.value=AuthState(true,Account("new-owner","other@example.invalid"),verified=true); advanceUntilIdle()
        model.authorized(oldId,"old-token-test",listOf(GMAIL_READONLY)); advanceUntilIdle()
        assertEquals(0,api.profiles); assertFalse(model.state.value.verified); assertNull(model.state.value.snapshot)
    }
    @Test fun restoredResultsDoNotPretendAuthorizationWasRechecked()=runTest(dispatcher) {
        val store=Store().apply { value=GmailSnapshot("owner",WeekRange.current(),Instant.now(),emptyList(),false) }
        val api=Api(); val model=model(api,store); advanceUntilIdle()
        assertNotNull(model.state.value.snapshot); assertFalse(model.state.value.verified); assertEquals(0,api.profiles)
    }
    @Test fun manualReviewProducesRealEmptySnapshotAndLogoutClearsIt()=runTest(dispatcher) {
        val store=Store(); val model=model(store=store); advanceUntilIdle(); var id=""
        model.begin(true) { _,request->id=request }; model.authorized(id,"test-token",listOf(GMAIL_READONLY)); advanceUntilIdle()
        assertTrue(model.state.value.verified); assertNotNull(model.state.value.snapshot)
        auth.value=AuthState(true); advanceUntilIdle()
        assertNull(model.state.value.snapshot); assertNull(store.value)
    }
    @Test fun offlineRevocationDoesNotClaimRemotePermissionRemoved()=runTest(dispatcher) {
        val store=Store().apply { value=GmailSnapshot("owner",WeekRange.current(),Instant.now(),emptyList(),false) }
        val model=model(store=store); advanceUntilIdle()
        model.disconnect { _,done->done(false) }; advanceUntilIdle()
        assertNull(store.value); assertFalse(model.state.value.verified)
        assertTrue(model.state.value.message.orEmpty().contains("No pudimos confirmar")); assertEquals("owner",auth.value.account?.id)
    }
    @Test fun connectingOnlyVerifiesProfileAndNeverReadsMailbox()=runTest(dispatcher) {
        val api=Api(); val model=model(api); advanceUntilIdle(); var id=""
        model.begin(false) { _,request->id=request }; model.authorized(id,"test-token",listOf(GMAIL_READONLY)); advanceUntilIdle()
        assertTrue(model.state.value.verified); assertEquals(1,api.profiles); assertEquals(0,api.lists); assertNull(model.state.value.snapshot)
    }

}
