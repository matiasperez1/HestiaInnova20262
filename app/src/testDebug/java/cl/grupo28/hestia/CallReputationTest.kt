package cl.grupo28.hestia

import cl.grupo28.hestia.data.calls.DemoReputationSnapshot
import cl.grupo28.hestia.data.calls.communityCallNumber
import cl.grupo28.hestia.data.demoCallReputation
import cl.grupo28.hestia.domain.CallClassification
import cl.grupo28.hestia.domain.CallVerdict
import cl.grupo28.hestia.domain.normalizeCallNumber
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallReputationTest {
    private val localCallReputation = demoCallReputation()

    @Test fun reportedFixtureMatchesFormattedAndInternationalNumbers() {
        for (input in listOf("2025550147", "+1 (202) 555-0147", "0012025550147")) {
            val verdict = localCallReputation.lookup(requireNotNull(normalizeCallNumber(input)))
            assertEquals(CallClassification.SUSPICIOUS, verdict.classification)
            assertTrue(verdict.reported)
            assertTrue(verdict.synthetic)
        }
        val noReports = localCallReputation.lookup("2025550148")
        assertEquals(CallClassification.NO_REPORTS, noReports.classification)
        assertFalse(noReports.reported)
        assertTrue(noReports.synthetic)
        assertEquals(CallVerdict.DEBUG_NO_MATCH, localCallReputation.lookup("2025550149"))
        assertEquals(CallVerdict.NO_MATCH, localCallReputation.lookup("2025550200"))
    }

    @Test fun rejectsNonTelephoneHandlesAndNormalizesChileanMobile() {
        assertNull(normalizeCallNumber("private"))
        assertNull(normalizeCallNumber("123"))
        assertNull(normalizeCallNumber("2025550147;ext=5"))
        assertEquals("+56912345678", normalizeCallNumber("9 1234 5678"))
    }

    @Test fun communityVerdictUsesLatestSnapshotAndItsConfiguredThreshold() {
        var snapshot = DemoReputationSnapshot(Instant.EPOCH, 2, mapOf("2025550199" to 1))
        val reputation = demoCallReputation { snapshot }
        assertEquals(CallVerdict.DEBUG_PENDING, reputation.lookup("+1 (202) 555-0199"))
        assertFalse(reputation.lookup("2025550199").reported)
        snapshot = snapshot.copy(counts = mapOf("2025550199" to 2), fraudCounts = mapOf("2025550199" to 2))
        assertEquals(CallVerdict.DEBUG_REPORTED, reputation.lookup("0012025550199"))
        snapshot = snapshot.copy(threshold = 3)
        assertEquals(CallVerdict.DEBUG_PENDING, reputation.lookup("2025550199"))
        snapshot = snapshot.copy(counts = emptyMap(), fraudCounts = emptyMap())
        assertEquals(CallVerdict.DEBUG_NO_MATCH, reputation.lookup("2025550199"))
        assertEquals(CallVerdict.DEBUG_REPORTED, reputation.lookup("2025550147"))
    }

    @Test fun communityReportsAllowFictionalFixturesAndRejectRealNumbers() {
        assertEquals("2025550199", communityCallNumber("+1 (202) 555-0199"))
        assertEquals("2025550147", communityCallNumber("2025550147"))
        assertEquals("2025550148", communityCallNumber("2025550148"))
        assertNull(communityCallNumber("+56912345678"))
        assertNull(communityCallNumber("+20 25550199"))
        assertNull(communityCallNumber("+112025550199"))
    }

    @Test fun nonFraudReportsNeverAdvanceFraudThresholdAndFixturesKeepTheirVerdict() {
        val snapshot = DemoReputationSnapshot(Instant.EPOCH, 2,
            mapOf("2025550199" to 2, "2025550148" to 2), mapOf("2025550199" to 1, "2025550148" to 2))
        val reputation = demoCallReputation { snapshot }
        assertEquals(CallVerdict.DEBUG_PENDING, reputation.lookup("2025550199"))
        assertEquals(CallVerdict.DEBUG_NO_MATCH, reputation.lookup("2025550148"))
        assertEquals(CallVerdict.DEBUG_REPORTED, reputation.lookup("2025550147"))
    }
}
