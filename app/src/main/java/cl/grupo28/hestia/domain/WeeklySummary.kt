package cl.grupo28.hestia.domain

import java.time.*
import java.time.temporal.TemporalAdjusters

enum class EventKind { CALL_REGISTERED, EMAIL_ANALYZED, ALERT_REVIEWED }
data class SecurityEvent(val sourceId: String, val kind: EventKind, val occurredAt: Instant, val suspicious: Boolean = false)

data class WeekRange(val start: LocalDate, val zone: ZoneId) {
    val end: LocalDate get() = start.plusDays(6)
    fun contains(instant: Instant): Boolean = instant >= start.atStartOfDay(zone).toInstant() && instant < start.plusWeeks(1).atStartOfDay(zone).toInstant()
    companion object {
        fun current(clock: Clock = Clock.systemDefaultZone()): WeekRange = WeekRange(
            LocalDate.now(clock).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)), clock.zone,
        )
    }
}

data class WeeklySummary(val registeredCalls: Int, val suspiciousCalls: Int, val analyzedEmails: Int, val suspiciousEmails: Int, val reviewedAlerts: Int) {
    companion object {
        /** Callers must provide recorded events, never sample communications or device call history. */
        fun fromEvents(events: List<SecurityEvent>, week: WeekRange): WeeklySummary {
            val unique = events.filter { week.contains(it.occurredAt) }.distinctBy { it.kind to it.sourceId }
            val calls = unique.filter { it.kind == EventKind.CALL_REGISTERED }
            val emails = unique.filter { it.kind == EventKind.EMAIL_ANALYZED }
            return WeeklySummary(calls.size, calls.count { it.suspicious }, emails.size, emails.count { it.suspicious }, unique.count { it.kind == EventKind.ALERT_REVIEWED })
        }
    }
}
