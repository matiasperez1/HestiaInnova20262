package cl.grupo28.hestia

import cl.grupo28.hestia.data.automatic.AutomaticApi
import cl.grupo28.hestia.domain.auth.*
import cl.grupo28.hestia.domain.automatic.AutomaticError
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*

class AutomaticApiTest {
 private val server=MockWebServer()
 private class Store:SessionStore {
  var value:AuthSession?=AuthSession("synthetic-jwt","synthetic-refresh",4102444800,Account("owner","a@example.invalid"))
  var loads=0;var switchAfterFirst=false
  override fun load():AuthSession? {loads++;return if(switchAfterFirst&&loads>1)null else value}
  override fun save(session:AuthSession){value=session}
  override fun clear(){value=null}
 }
 @Before fun before(){server.start()}
 @After fun after(){server.shutdown()}
 private fun api(store:Store)=AutomaticApi(store,server.url("/").toString().trimEnd('/'))
 private val status="""{"status":"ACTIVE","automaticActive":true,"lastSync":"2026-11-16T12:00:00Z","watchExpiration":"2026-11-20T12:00:00Z","monitoringStartedAt":"2026-11-16T12:00:00Z"}"""
 @Test fun oneUseCodeUsesSupabaseBearerAndNeverSuppliesOwner()=runBlocking {
  server.enqueue(MockResponse().setBody(status));assertTrue(api(Store()).connect("owner","synthetic-code").active)
  val request=server.takeRequest();assertEquals("Bearer synthetic-jwt",request.getHeader("Authorization"));assertEquals("POST",request.method)
  val json=JSONObject(request.body.readUtf8());assertEquals("synthetic-code",json.getString("serverAuthCode"));assertTrue(json.has("timeZone"));assertFalse(json.has("email"));assertFalse(json.has("userId"))
 }
 @Test fun expiredSessionCannotSendARequest()=runBlocking {
  val s=Store().apply{value=AuthSession("expired","refresh",0,Account("owner","a@example.invalid"))}
  try{api(s).status("owner");fail()}catch(e:AutomaticError){assertEquals("UNAUTHENTICATED",e.code)};assertEquals(0,server.requestCount)
 }
 @Test fun accountChangeDiscardsACompletedResponse()=runBlocking {
  server.enqueue(MockResponse().setBody(status));val s=Store().apply{switchAfterFirst=true}
  try{api(s).status("owner");fail()}catch(e:AutomaticError){assertEquals("OWNER_CHANGED",e.code)}
 }
 @Test fun missingRefreshResponseTriggersControlledReconsent()=runBlocking {
  server.enqueue(MockResponse().setResponseCode(428).setBody("""{"error":"REFRESH_TOKEN_REQUIRED"}"""))
  try{api(Store()).connect("owner","code");fail()}catch(e:AutomaticError){assertEquals("REFRESH_TOKEN_REQUIRED",e.code)}
 }
 @Test fun onlySuccessfulConsultationReturnsActualZero()=runBlocking {
  server.enqueue(MockResponse().setBody("""{"analyzed":0,"suspicious":0,"weekStart":"2026-11-16T00:00:00Z","weekEnd":"2026-11-23T00:00:00Z"}"""))
  assertEquals(0,api(Store()).summary("owner").analyzed);assertTrue(server.takeRequest().path!!.contains("timeZone="))
 }
}
