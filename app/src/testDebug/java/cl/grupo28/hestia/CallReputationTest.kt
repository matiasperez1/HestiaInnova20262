package cl.grupo28.hestia

import cl.grupo28.hestia.data.localCallReputation
import cl.grupo28.hestia.domain.CallVerdict
import cl.grupo28.hestia.domain.normalizeCallNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CallReputationTest {
    @Test fun reportedFixtureMatchesFormattedAndInternationalNumbers() {
        for (input in listOf("2025550147", "+1 (202) 555-0147", "0012025550147")) {
            assertEquals(CallVerdict.DEBUG_REPORTED, localCallReputation.lookup(requireNotNull(normalizeCallNumber(input))))
        }
        assertEquals(CallVerdict.DEBUG_NO_MATCH, localCallReputation.lookup("2025550148"))
        assertEquals(CallVerdict.NO_MATCH, localCallReputation.lookup("2025550149"))
    }

    @Test fun rejectsNonTelephoneHandlesAndNormalizesChileanMobile() {
        assertNull(normalizeCallNumber("private"))
        assertNull(normalizeCallNumber("123"))
        assertNull(normalizeCallNumber("2025550147;ext=5"))
        assertEquals("+56912345678", normalizeCallNumber("9 1234 5678"))
    }
}
