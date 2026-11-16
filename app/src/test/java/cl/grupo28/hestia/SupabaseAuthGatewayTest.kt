package cl.grupo28.hestia

import cl.grupo28.hestia.data.auth.*
import cl.grupo28.hestia.domain.auth.InvalidSessionException
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.io.IOException

class SupabaseAuthGatewayTest {
    private val server = MockWebServer()
    private lateinit var gateway: SupabaseAuthGateway
    @Before fun before() {
        server.start()
        gateway = SupabaseAuthGateway(AuthConfiguration(server.url("/").toString(), "sb_publishable_test-public-value", "test.apps.googleusercontent.com"))
    }
    @After fun after() { server.shutdown() }
    private val sessionJson = """{"access_token":"access-test","refresh_token":"refresh-test","expires_at":2000000000,"user":{"id":"test-id","email":"test@example.invalid"}}"""
    @Test fun idTokenExchangeUsesRawNonceAndPublicKeyOnly() = runBlocking {
        server.enqueue(MockResponse().setBody(sessionJson))
        val result = gateway.exchange("google-token-test", "raw-nonce-test")
        val request = server.takeRequest()
        assertEquals("/auth/v1/token?grant_type=id_token", request.path)
        val body = JSONObject(request.body.readUtf8())
        assertEquals("google", body.getString("provider")); assertEquals("raw-nonce-test", body.getString("nonce"))
        assertEquals("google-token-test", body.getString("id_token"))
        assertEquals("sb_publishable_test-public-value", request.getHeader("apikey")); assertNull(request.getHeader("Authorization"))
        assertEquals("test-id", result.account.id)
    }
    @Test fun refreshPostsTokenInBodyAndReturnsRotation() = runBlocking {
        server.enqueue(MockResponse().setBody(sessionJson))
        assertEquals("refresh-test", gateway.refresh("previous-refresh-test").refreshToken)
        val request = server.takeRequest()
        assertEquals("/auth/v1/token?grant_type=refresh_token", request.path)
        assertEquals("previous-refresh-test", JSONObject(request.body.readUtf8()).getString("refresh_token"))
    }
    @Test fun revokedRefreshBecomesInvalidSession() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error_code":"refresh_token_not_found"}"""))
        try { gateway.refresh("revoked-test"); fail("Expected invalid session") } catch (_: InvalidSessionException) { }
    }
    @Test fun logoutRevokesOnlyCurrentSession() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        gateway.logout("access-test")
        val request = server.takeRequest()
        assertEquals("/auth/v1/logout?scope=local", request.path)
        assertEquals("Bearer access-test", request.getHeader("Authorization"))
    }
    @Test fun providerPrivateResponseIsNotExposed() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503).setBody("private-provider-response"))
        try { gateway.exchange("id-test", "nonce-test"); fail("Expected network error") }
        catch (failure: IOException) { assertFalse(failure.message.orEmpty().contains("private-provider-response")) }
    }
    @Test fun invalidPublicKeyIsNotMistakenForRevokedUserSession() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"message":"Invalid API key"}"""))
        try { gateway.validate("access-test"); fail("Expected configuration/network error") }
        catch (_: IOException) { }
    }

}
