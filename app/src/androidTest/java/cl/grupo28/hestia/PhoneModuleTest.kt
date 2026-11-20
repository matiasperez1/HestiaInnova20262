package cl.grupo28.hestia

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import cl.grupo28.hestia.core.theme.HestiaTheme
import cl.grupo28.hestia.data.calls.RecentCall
import cl.grupo28.hestia.data.calls.RecentKind
import cl.grupo28.hestia.features.calls.CallSetup
import cl.grupo28.hestia.features.calls.CallSetupState
import cl.grupo28.hestia.features.calls.PhoneScreen
import cl.grupo28.hestia.features.calls.RecentCallOptions
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PhoneModuleTest {
    @get:Rule val compose = createComposeRule()

    @Test fun startsInRecentsAndKeepsTheNumberWhenSwitchingTabs() {
        val calls = CallSetup(CallSetupState(available = true, active = false, noticesEnabled = false), {}, {})
        compose.setContent { HestiaTheme { PhoneScreen(calls, {}, {}) } }
        compose.onNodeWithTag("phone_section_recent").assertIsSelected()
        val tabs = listOf("recent", "contacts", "keypad", "protection").map { compose.onNodeWithTag("phone_section_$it") }
        tabs.map { it.getUnclippedBoundsInRoot() }.zipWithNext().forEach { (first, second) ->
            assertEquals(first.top.value, second.top.value, 1f)
            assertTrue(first.right <= second.left)
        }
        tabs.forEach { it.performScrollTo().assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp) }
        compose.onNodeWithTag("phone_section_keypad").performScrollTo().performClick()
        compose.onNodeWithContentDescription("2").performScrollTo().performClick()
        compose.onNodeWithContentDescription("0").performScrollTo().performClick()
        compose.onNodeWithTag("phone_section_contacts").performScrollTo().performClick()
        compose.onNodeWithText("Contactos de confianza").assertExists()
        compose.onNodeWithTag("phone_section_keypad").performScrollTo().performClick()
        compose.onNodeWithText("20").assertExists()
        compose.onNodeWithTag("phone_section_protection").performScrollTo().performClick()
        compose.onNodeWithText("Identificación de llamadas").assertExists()
    }

    @Test fun completedCallOptionsExposeDetailsWithoutHelpOrLessons() {
        val call = RecentCall("test-recent", "+56900000001", Instant.EPOCH, RecentKind.MISSED, false)
        compose.setContent { HestiaTheme { RecentCallOptions(call, null, {}, {}, {}) } }
        compose.onNodeWithText("Llamar").assertExists()
        compose.onNodeWithText("Agregar a contactos").assertExists()
        compose.onNodeWithText("Pedir ayuda", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Aprender", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Ver detalles de llamada").performScrollTo().performClick()
        compose.onNodeWithText("Guardada por el teléfono de Hestia.").assertExists()
    }
}
