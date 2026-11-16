package cl.grupo28.hestia

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import cl.grupo28.hestia.core.accessibility.*
import cl.grupo28.hestia.core.theme.HestiaTheme
import cl.grupo28.hestia.domain.auth.*
import cl.grupo28.hestia.features.account.AccountCard
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AccountCardTest {
    @get:Rule val compose = createComposeRule()
    @Test fun missingConfigurationLeavesSignInDisabled() {
        compose.setContent { HestiaTheme { AccountCard(AuthState(false), { error("Disabled action executed") }, {}, {}) } }
        compose.onNodeWithText("Iniciar sesión con Google").assertIsNotEnabled()
        compose.onNodeWithText("Puedes usar Hestia sin cuenta.").assertIsDisplayed()
    }
    @Test fun largeTextLogoutRequiresExplicitConfirmation() {
        var closed = 0
        compose.setContent {
            HestiaTheme(DisplayPreferences(textSize = TextSize.VERY_LARGE)) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    AccountCard(AuthState(true, Account("test-id", "test@example.invalid"), verified = true), {}, { closed++ }, {})
                }
            }
        }
        compose.onNodeWithText("Cerrar sesión").performScrollTo().performClick()
        compose.onNodeWithText("Cancelar").performClick(); compose.runOnIdle { assertEquals(0, closed) }
        compose.onNodeWithText("Cerrar sesión").performScrollTo().performClick()
        compose.onNodeWithText("Tus contactos, lecciones y ajustes se conservarán en este teléfono.").assertIsDisplayed()
        compose.onNode(hasText("Cerrar sesión") and hasAnyAncestor(isDialog())).performClick()
        compose.runOnIdle { assertEquals(1, closed) }
    }
}
