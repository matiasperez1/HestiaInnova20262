package cl.grupo28.hestia

import cl.grupo28.hestia.data.gmail.*
import cl.grupo28.hestia.domain.WeekRange
import cl.grupo28.hestia.domain.gmail.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.time.*
import java.util.Base64

class GmailApiTest {
    private val server = MockWebServer()
    private lateinit var api: GmailApi
    @Before fun before() { server.start(); api=GmailApi(server.url("/").toString()) }
    @After fun after() { server.shutdown() }
    @Test fun listingIsReadOnlyBoundedAndInboxScoped() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"messages":[{"id":"abc"}],"nextPageToken":"next"}"""))
        val week=WeekRange(LocalDate.of(2020,1,6),ZoneOffset.UTC)
        val result=api.list("test-token",week,null); val request=server.takeRequest()
        assertEquals("GET",request.method); assertEquals("INBOX",request.requestUrl?.queryParameter("labelIds"))
        assertTrue(request.requestUrl?.queryParameter("q").orEmpty().contains("after:"))
        assertEquals("100",request.requestUrl?.queryParameter("maxResults")); assertEquals("Bearer test-token",request.getHeader("Authorization"))
        assertEquals("next",result.nextPage)
    }
    @Test fun permissionRevocationIsNotAnEmptyMailbox() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        try { api.profile("test-token"); fail("Expected authorization error") }
        catch (failure: GmailAccessException) { assertEquals(GmailAccessException.Kind.AUTHORIZATION,failure.kind) }
    }
    @Test fun disabledApiHasDistinctRecoverableState() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403).setBody("accessNotConfigured"))
        try { api.profile("test-token"); fail("Expected API-disabled error") }
        catch (failure: GmailAccessException) { assertEquals(GmailAccessException.Kind.API_DISABLED,failure.kind) }
    }
    @Test fun deletedMessageDoesNotFailWholeReview() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404)); assertNull(api.message("test-token","abc"))
    }
    @Test fun nestedMimeDecodesTextButDoesNotAnalyzeAttachedMessage() {
        fun encoded(s:String)=Base64.getUrlEncoder().withoutPadding().encodeToString(s.toByteArray())
        val json=JSONObject("""{"id":"abc","internalDate":"1578391200000","payload":{"mimeType":"multipart/mixed","headers":[{"name":"Subject","value":"Prueba"}],"parts":[{"mimeType":"text/plain","body":{"data":"${encoded("Hola, mañana nos vemos.")}"}},{"mimeType":"message/rfc822","filename":"adjunto.eml","parts":[{"mimeType":"text/plain","body":{"data":"${encoded("Ingresa tu clave urgente")}"}}]}]}}""")
        val result=GmailMimeParser.parse(json)
        assertTrue(result.text.contains("Hola")); assertFalse(result.text.contains("clave")); assertEquals("Prueba",result.subject)
    }
    @Test fun missingInlineBodyIsMarkedLimited() {
        val result=GmailMimeParser.parse(JSONObject("""{"id":"abc","internalDate":"1578391200000","payload":{"mimeType":"text/plain","body":{"attachmentId":"not-downloaded"}}}"""))
        assertTrue(result.limitedContent); assertTrue(result.text.isBlank())
    }
    @Test fun quota403DoesNotInvalidateAuthorization() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403).setBody("userRateLimitExceeded"))
        try { api.profile("test-token"); fail("Expected quota error") }
        catch (failure:GmailAccessException) { assertEquals(GmailAccessException.Kind.QUOTA,failure.kind) }
    }

    private fun mimeMessage(payload:JSONObject)=JSONObject().put("id","test-id").put("internalDate","1578391200000").put("payload",payload)
    @Test fun attachmentDispositionIsSkippedEvenWithoutFilename() {
        val payload=JSONObject().put("mimeType","text/plain").put("headers",org.json.JSONArray().put(JSONObject().put("name","Content-Disposition").put("value","attachment")))
            .put("body",JSONObject().put("data",Base64.getUrlEncoder().encodeToString("private attachment".toByteArray())))
        val result=GmailMimeParser.parse(mimeMessage(payload))
        assertTrue(result.text.isBlank()); assertTrue(result.limitedContent)
    }
    @Test fun mimePartLimitIsExplicit() {
        val parts=org.json.JSONArray()
        repeat(250) { parts.put(JSONObject().put("mimeType","text/plain").put("body",JSONObject().put("data",Base64.getUrlEncoder().encodeToString("hello".toByteArray())))) }
        val result=GmailMimeParser.parse(mimeMessage(JSONObject().put("mimeType","multipart/mixed").put("parts",parts)))
        assertTrue(result.limitedContent); assertTrue(result.text.length < 250*6)
    }
    @Test fun textLimitNeverStoresOrAnalyzesUnboundedBody() {
        val payload=JSONObject().put("mimeType","text/plain").put("body",JSONObject().put("data",Base64.getUrlEncoder().encodeToString("a".repeat(70000).toByteArray())))
        val result=GmailMimeParser.parse(mimeMessage(payload))
        assertTrue(result.limitedContent); assertEquals(65536,result.text.length)
    }

}
