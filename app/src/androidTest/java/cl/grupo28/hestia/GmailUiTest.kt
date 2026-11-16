package cl.grupo28.hestia

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import cl.grupo28.hestia.core.accessibility.*
import cl.grupo28.hestia.core.theme.HestiaTheme
import cl.grupo28.hestia.domain.WeekRange
import cl.grupo28.hestia.domain.gmail.*
import cl.grupo28.hestia.features.gmail.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class GmailUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun gmailPermissionCannotBeRequestedWithoutHestiaAccount() {
        compose.setContent { HestiaTheme { GmailPanel(GmailUiState(),{error("Disabled action")},{},{}) } }
        compose.onNodeWithText("Conectar Gmail").assertIsNotEnabled()
        compose.onNodeWithText("Sin conexión verificada").assertIsDisplayed()
    }
    @Test fun disconnectExplainsGlobalGoogleRevocationBeforeAction() {
        var disconnected=0
        compose.setContent { HestiaTheme(DisplayPreferences(textSize=TextSize.VERY_LARGE)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                GmailPanel(GmailUiState(eligible=true,verified=true),{},{},{disconnected++})
            }
        } }
        compose.onNodeWithText("Desconectar Gmail").performScrollTo().performClick()
        compose.onNodeWithText("Cancelar").performClick(); compose.runOnIdle { assertEquals(0,disconnected) }
        compose.onNodeWithText("Desconectar Gmail").performScrollTo().performClick()
        compose.onNode(hasText("Desconectar") and hasAnyAncestor(isDialog())).performClick()
        compose.runOnIdle { assertEquals(1,disconnected) }
    }
    @Test fun partialResultsAreNotPresentedAsEntireMailbox() {
        val snapshot=GmailSnapshot("test-owner",WeekRange.current(),Instant.now(),emptyList(),true)
        compose.setContent { HestiaTheme { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            GmailPanel(GmailUiState(eligible=true,snapshot=snapshot),{},{},{})
        } } }
        compose.onNodeWithText("Resultado parcial: se alcanzó un límite de revisión. No representa toda tu bandeja.").performScrollTo().assertIsDisplayed()
    }
    @Test fun actualEmailCountsDoNotInventCallMetrics() {
        val week=WeekRange.current()
        val record=EmailRecord("test-hash",Instant.now(),"test@example.invalid","Test",EmailAnalysis(65,EmailRisk.HIGH,listOf("Test signal")))
        compose.setContent { HestiaTheme {
            cl.grupo28.hestia.features.summary.SummaryScreen(week,null,GmailUiState(snapshot=GmailSnapshot("test-owner",week,Instant.now(),listOf(record),false)),{})
        } }
        compose.onNodeWithContentDescription("Registradas por Hestia: Sin datos").assertExists()
        compose.onNodeWithContentDescription("Analizados: 1").assertExists()
        compose.onNodeWithContentDescription("Sospechosos: 1").assertExists()
    }

    @Test fun reviewingStateIsExplicitAndDoesNotFabricateZero() {
        compose.setContent { HestiaTheme { GmailPanel(GmailUiState(eligible=true,busy=true,operation=GmailOperation.REVIEWING),{},{},{}) } }
        compose.onNodeWithText("Revisando correos…").assertIsNotEnabled()
        compose.onNodeWithText("Última revisión: 0 correos; 0 con señales de riesgo.").assertDoesNotExist()
    }

}
