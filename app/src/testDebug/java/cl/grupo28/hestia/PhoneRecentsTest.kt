package cl.grupo28.hestia

import cl.grupo28.hestia.data.calls.RecentCall
import cl.grupo28.hestia.data.calls.RecentKind
import cl.grupo28.hestia.data.calls.retainRecentCalls
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.Duration
import java.time.Instant

class PhoneRecentsTest {
    @Test fun limitsRetentionOrdersCallsAndDeduplicatesARepeatedTelecomId() {
        val now = Instant.parse("2026-11-18T12:00:00Z")
        val rows = (0..35).map { index ->
            RecentCall("id-$index", "2025550148", now.minusSeconds(index.toLong()), RecentKind.RECEIVED, true)
        }
        val duplicate = rows[0].copy(at = now.minusSeconds(2))
        val expired = rows[0].copy(id = "expired", at = now.minus(Duration.ofDays(31)))
        assertEquals(rows.take(30), retainRecentCalls(rows.reversed() + duplicate + expired, now))
        assertFalse(retainRecentCalls(listOf(expired), now).any())
    }
}
