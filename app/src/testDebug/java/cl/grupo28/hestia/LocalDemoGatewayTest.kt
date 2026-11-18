package cl.grupo28.hestia

import cl.grupo28.hestia.data.automatic.*
import cl.grupo28.hestia.domain.automatic.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.MockResponse
import org.junit.*
import org.junit.Assert.*

class LocalDemoGatewayTest {
 private class Modes:DemoModeStore {var value=DemoSelection(true);override fun read()=value;override fun save(selection:DemoSelection){value=selection}}
 private val server=MockWebServer()
 @Before fun before(){server.start()}
 @After fun after(){server.shutdown()}
 private fun gateway(modes:Modes=Modes())=LocalDemoGateway(modes,server.url("/").toString().trimEnd('/'),false)
 private fun session(persona:String="alice")=MockResponse().setBody("""{"userId":"${LocalDemoGateway.owner(persona)}","token":"demo-synthetic-local-token"}""")
 @Test fun localSessionUsesServerPersonaAndNeverSupabaseCredentials()=runBlocking {
  server.enqueue(session());server.enqueue(MockResponse().setBody("""{"status":"DEMO_LOCAL","automaticActive":false,"lastSync":null,"watchExpiration":null,"monitoringStartedAt":null}"""))
  val g=gateway();assertFalse(g.status(LocalDemoGateway.owner("alice")).active)
  val first=server.takeRequest();assertEquals("/demo/session",first.path);assertEquals("true",first.getHeader("X-Hestia-Demo"));assertEquals("""{"persona":"alice"}""",first.body.readUtf8())
  val second=server.takeRequest();assertEquals("Bearer demo-synthetic-local-token",second.getHeader("Authorization"));assertEquals("true",second.getHeader("X-Hestia-Demo"))
 }
 @Test fun rejectsExternalHostsBeforeAnyRequest(){for(base in listOf("http://googleapis.com:8787","https://localhost:8787","http://127.0.0.1:8787/?secret=a"))try{LocalDemoGateway(Modes(),base,false);fail()}catch(_:IllegalArgumentException){};assertEquals(0,server.requestCount)}
 @Test fun oauthAndFcmOperationsRemainForbidden()=runBlocking {val g=gateway();try{g.connect(LocalDemoGateway.owner("alice"),"must-not-send");fail()}catch(e:AutomaticError){assertEquals("OAUTH_DISABLED_IN_DEMO",e.code)};try{g.register(LocalDemoGateway.owner("alice"),"installation","must-not-send",true);fail()}catch(e:AutomaticError){assertEquals("FCM_DISABLED_IN_DEMO",e.code)};assertEquals(0,server.requestCount)}
 @Test fun identityChangeNeverReusesOtherUsersSession()=runBlocking {val g=gateway();g.select(true,"bob");try{g.status(LocalDemoGateway.owner("alice"));fail()}catch(e:AutomaticError){assertEquals("OWNER_CHANGED",e.code)};assertEquals(0,server.requestCount)}
 @Test fun modePersistsSeparatelyAndDoesNotEraseOtherSettings(){val modes=Modes();val g=gateway(modes);g.select(true,"bob");assertEquals(DemoSelection(true,"bob"),gateway(modes).selection.value);g.select(false,"bob");assertFalse(gateway(modes).selection.value.enabled)}
 @Test fun backendUnavailableNeverProducesInventedResults()=runBlocking {val g=gateway();server.shutdown();try{g.status(LocalDemoGateway.owner("alice"));fail()}catch(_:java.io.IOException){} }
 @Test fun missingLocalPermissionStopsBeforeOpeningSocket()=runBlocking {val g=LocalDemoGateway(Modes(),server.url("/").toString().trimEnd('/'),false,{false});try{g.status(LocalDemoGateway.owner("alice"));fail()}catch(e:AutomaticError){assertEquals("DEMO_LOCAL_PERMISSION_REQUIRED",e.code)};assertEquals(0,server.requestCount)}
 @Test fun incrementalCursorAndOwnerUseLocalAuthenticatedSession()=runBlocking {
  val owner=LocalDemoGateway.owner("alice");server.enqueue(session());server.enqueue(MockResponse().setBody("""{"identity":"$owner","items":[{"event_id":"123","cursor":"YWJj"}],"nextCursor":"YWJj","hasMore":true}"""))
  val g=gateway();val page=g.notifications(owner,"YWJj");assertEquals("123",page.items.single().eventId);assertTrue(page.hasMore)
  server.takeRequest();assertEquals("/demo/notifications?limit=50&after=YWJj",server.takeRequest().path)
 }
 @Test fun incrementalRejectsOtherIdentity()=runBlocking {
  server.enqueue(session());server.enqueue(MockResponse().setBody("""{"identity":"${LocalDemoGateway.owner("bob")}","items":[],"nextCursor":"YWJj","hasMore":false}"""))
  try{gateway().notifications(LocalDemoGateway.owner("alice"),null);fail()}catch(e:AutomaticError){assertEquals("OWNER_CHANGED",e.code)}
 }
}
