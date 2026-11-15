package cl.grupo28.hestia

import cl.grupo28.hestia.domain.ContactRules
import org.junit.Assert.*
import org.junit.Test

class ContactRulesTest {
    @Test fun acceptsFormattedPhoneAndTrimsNames() {
        assertEquals("Ana María", ContactRules.name("  Ana   María  "))
        assertEquals("+56912345678", ContactRules.phone("+56 (9) 1234-5678"))
    }
    @Test fun rejectsServiceCodesLettersAndTooShortNumbers() {
        listOf("*123#", "tel:123456789", "123", "1234567890123456", "+56;12345678", "+56ABC12345").forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { ContactRules.phone(value) }
        }
    }
    @Test fun rejectsBlankAndOverlongNames() {
        assertThrows(IllegalArgumentException::class.java) { ContactRules.name("   ") }
        assertThrows(IllegalArgumentException::class.java) { ContactRules.name("A".repeat(61)) }
    }
}
