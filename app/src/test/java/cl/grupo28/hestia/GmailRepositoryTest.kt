package cl.grupo28.hestia

import cl.grupo28.hestia.data.gmail.GmailRepository
import cl.grupo28.hestia.domain.WeekRange
import cl.grupo28.hestia.domain.gmail.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.time.*

class GmailRepositoryTest {
    private val week = WeekRange(LocalDate.of(2020,1,6), ZoneOffset.UTC)
    private class Store : GmailStore {
        var value: GmailSnapshot? = null
        override fun load(ownerId: String) = value?.takeIf { it.ownerId == ownerId }
        override fun save(snapshot: GmailSnapshot) { value = snapshot }
        override fun clear() { value = null }
    }
    private inner class Api : GmailGateway {
        var address = "test@example.invalid"
        var count = 2
        var fail = false
        var reads = 0
        var profiles = 0
        var listCalls = 0
        var endless = false
        var received = Instant.parse("2020-01-07T10:00:00Z")
        override suspend fun profile(token: String): String { profiles++; return address }
        override suspend fun list(token: String, week: WeekRange, page: String?): GmailPage {
            listCalls++
            if (endless) return GmailPage(emptyList(), "page$listCalls")
            return if (page == null) GmailPage((0 until count).map { "message$it" }, if(count == 2) "second-page" else null)
            else GmailPage(listOf("message0"), null)
        }
        override suspend fun message(token: String, id: String): GmailMessage {
            reads++; if (fail) throw IOException()
            return GmailMessage(id, received, "sender@example.invalid", "Saludo", "", "Hola", false)
        }
    }
    @Test fun pagesAreDeduplicatedAndOnlyOneSnapshotIsCommitted() = runBlocking {
        val store = Store(); val api = Api(); val result = GmailRepository(api,store).sync("test-token","owner","test@example.invalid",week)
        assertEquals(2,result.records.size); assertEquals(2,api.reads); assertFalse(result.partial)
        assertSame(result,store.value)
    }
    @Test fun failedReviewPreservesPreviousSnapshot() = runBlocking {
        val store = Store(); val api = Api(); val repo = GmailRepository(api,store)
        val previous = repo.sync("test-token","owner","test@example.invalid",week); api.fail = true
        try { repo.sync("test-token","owner","test@example.invalid",week); fail("Expected failure") } catch (_: IOException) { }
        assertSame(previous,store.value)
    }
    @Test fun differentAccountCannotReadMessagesOrCommitData() = runBlocking {
        val store = Store(); val api = Api().apply { address = "other@example.invalid" }
        try { GmailRepository(api,store).sync("test-token","owner","test@example.invalid",week); fail("Expected mismatch") } catch (_: IllegalArgumentException) { }
        assertEquals(0,api.reads); assertNull(store.value)
    }
    @Test fun realEmptyQueryCanProduceZero() = runBlocking {
        val api = Api().apply { count = 0 }; val result = GmailRepository(api,Store()).sync("test-token","owner","test@example.invalid",week)
        assertTrue(result.records.isEmpty()); assertEquals(1,api.profiles); assertFalse(result.partial)
    }
    @Test fun boundIsExplicitAndDoesNotReadBeyond200Messages() = runBlocking {
        val api = Api().apply { count = 250 }; val result = GmailRepository(api,Store()).sync("test-token","owner","test@example.invalid",week)
        assertEquals(200,result.records.size); assertEquals(200,api.reads); assertTrue(result.partial)
    }
    @Test fun exactWeekBoundariesExcludePreviousAndNextWeek() = runBlocking {
        for ((time,expected) in listOf("2020-01-05T23:59:59Z" to 0, "2020-01-06T00:00:00Z" to 2, "2020-01-12T23:59:59Z" to 2, "2020-01-13T00:00:00Z" to 0)) {
            val api=Api().apply { received=Instant.parse(time) }
            assertEquals(expected,GmailRepository(api,Store()).sync("test-token","owner","test@example.invalid",week).records.size)
        }
    }
    @Test fun filteringUsesDeviceZoneRatherThanUtcDate() = runBlocking {
        val local=WeekRange(LocalDate.of(2020,1,6),ZoneId.of("America/Santiago"))
        val before=local.start.atStartOfDay(local.zone).toInstant().minusSeconds(1)
        assertEquals(local.start,before.atZone(ZoneOffset.UTC).toLocalDate())
        val api=Api().apply { received=before }
        assertTrue(GmailRepository(api,Store()).sync("test-token","owner","test@example.invalid",local).records.isEmpty())
    }
    @Test fun futureDatedMessagesAreNotCounted() = runBlocking {
        val local=WeekRange.current()
        val api=Api().apply { received=Instant.now().plusSeconds(60) }
        assertTrue(GmailRepository(api,Store()).sync("test-token","owner","test@example.invalid",local).records.isEmpty())
    }
    @Test fun emptyPaginationCannotRunBeyondTenPages() = runBlocking {
        val api=Api().apply { endless=true }
        val result=GmailRepository(api,Store()).sync("test-token","owner","test@example.invalid",week)
        assertEquals(10,api.listCalls); assertTrue(result.partial)
    }

}
