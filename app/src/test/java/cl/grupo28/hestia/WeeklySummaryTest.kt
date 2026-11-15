package cl.grupo28.hestia

import cl.grupo28.hestia.domain.*
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class WeeklySummaryTest {
    private val zone = ZoneId.of("America/Santiago")
    private val week = WeekRange(LocalDate.of(2026, 11, 9), zone)
    private val start = week.start.atStartOfDay(zone).toInstant()
    @Test fun usesLocalMondayAndExcludesNextMonday() {
        assertEquals(week, WeekRange.current(Clock.fixed(Instant.parse("2026-11-15T22:00:00Z"), zone)))
        assertFalse(week.contains(start.minusNanos(1)))
        assertTrue(week.contains(start))
        assertTrue(week.contains(week.end.plusDays(1).atStartOfDay(zone).toInstant().minusNanos(1)))
        assertFalse(week.contains(week.start.plusWeeks(1).atStartOfDay(zone).toInstant()))
    }
    @Test fun separatesRegistrationDetectionAndReviewWithoutDuplicateEvents() {
        val call = SecurityEvent("call1", EventKind.CALL_REGISTERED, start, true)
        val events = listOf(call, call, SecurityEvent("call2", EventKind.CALL_REGISTERED, start),
            SecurityEvent("email1", EventKind.EMAIL_ANALYZED, start, true),
            SecurityEvent("email2", EventKind.EMAIL_ANALYZED, start),
            SecurityEvent("email1", EventKind.ALERT_REVIEWED, start), SecurityEvent("email1", EventKind.ALERT_REVIEWED, start),
            SecurityEvent("outside", EventKind.CALL_REGISTERED, start.minusSeconds(1), true))
        assertEquals(WeeklySummary(2, 1, 2, 1, 1), WeeklySummary.fromEvents(events, week))
    }
    @Test fun anEmptyRealEventSetProducesZeroCounts() {
        assertEquals(WeeklySummary(0, 0, 0, 0, 0), WeeklySummary.fromEvents(emptyList(), week))
    }
}
