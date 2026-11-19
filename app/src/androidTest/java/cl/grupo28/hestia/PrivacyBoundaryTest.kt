package cl.grupo28.hestia

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.security.NetworkSecurityPolicy
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import cl.grupo28.hestia.core.theme.HestiaTheme
import cl.grupo28.hestia.features.calls.HestiaIncomingCall
import cl.grupo28.hestia.features.calls.dialer.PhoneKeypad
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PrivacyBoundaryTest {
    @get:Rule val compose = createComposeRule()

    @Test fun launcherAndTelecomHaveSeparateEntrypoints() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val pm = context.packageManager
        val pkg = context.packageName
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(pkg)
        assertEquals(HestiaLauncherActivity::class.java.name, pm.resolveActivity(launcher, 0)?.activityInfo?.name)
        assertFalse(pm.getActivityInfo(ComponentName(context, MainActivity::class.java), 0).exported)
        assertFalse(pm.getActivityInfo(ComponentName(pkg, "$pkg.features.calls.dialer.TelecomCallActivity"), 0).exported)
        assertTrue(pm.getActivityInfo(ComponentName(pkg, "$pkg.features.calls.dialer.HestiaDialerActivity"), 0).exported)
        assertEquals("android.permission.BIND_SCREENING_SERVICE",
            pm.getServiceInfo(ComponentName(pkg, "$pkg.services.HestiaCallScreeningService"), 0).permission)
        assertEquals("android.permission.BIND_INCALL_SERVICE",
            pm.getServiceInfo(ComponentName(pkg, "$pkg.features.calls.dialer.HestiaInCallService"), 0).permission)
        assertFalse(pm.getReceiverInfo(ComponentName(pkg, "$pkg.features.calls.dialer.CallActionReceiver"), 0).exported)
        assertFalse(NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted("example.invalid"))
        assertTrue(NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted("10.0.2.2"))
        assertEquals(PackageManager.PERMISSION_DENIED, pm.checkPermission("android.permission.READ_CALL_LOG", pkg))
    }

    @Test fun lockedCallKeepsActionsAndHidesTheNumberAndDetails() {
        val locked = mutableStateOf(false)
        compose.setContent {
            HestiaTheme {
                HestiaIncomingCall(
                    number = "202 555 0147",
                    reported = true,
                    testLabel = "Número de prueba",
                    onReject = {},
                    onAnswer = {},
                    showPrivateDetails = !locked.value,
                )
            }
        }
        compose.onNodeWithText("Ver por qué").performScrollTo().performClick()
        compose.onNodeWithText("Por qué Hestia la marcó").assertIsDisplayed()
        compose.runOnIdle { locked.value = true }
        compose.onNodeWithText("Número oculto").assertIsDisplayed()
        compose.onNodeWithText("SOSPECHOSO").assertIsDisplayed()
        listOf("Contestar", "Rechazar").forEach { action ->
            compose.onNodeWithText(action).performScrollTo().assertIsDisplayed()
                .assertHasClickAction().assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        }
        compose.onNodeWithText("202 555 0147").assertDoesNotExist()
        compose.onNodeWithText("Ver por qué").assertDoesNotExist()
        compose.onNodeWithText("Por qué Hestia la marcó").assertDoesNotExist()
    }

    @Test fun keypadExposesOneLabelPerKeyAndThePlusAction() {
        var pressed: Char? = null
        compose.setContent { HestiaTheme { PhoneKeypad({ pressed = it }, showLetters = true, onPlus = { pressed = '+' }) } }
        listOf("0", "2", "Asterisco", "Numeral").forEach { label ->
            compose.onNodeWithContentDescription(label).assertHasClickAction().assertWidthIsAtLeast(48.dp)
                .assertHeightIsAtLeast(48.dp)
        }
        compose.onNodeWithText("ABC").assertDoesNotExist()
        compose.onNodeWithContentDescription("Asterisco").performClick()
        compose.runOnIdle { assertEquals('*', pressed) }
        compose.onNodeWithContentDescription("0").performSemanticsAction(
            androidx.compose.ui.semantics.SemanticsActions.OnLongClick,
        )
        compose.runOnIdle { assertEquals('+', pressed) }
    }
}
