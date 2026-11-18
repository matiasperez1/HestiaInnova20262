package cl.grupo28.hestia

import android.app.Instrumentation
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.Role
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cl.grupo28.hestia.features.education.lessons
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class HestiaNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun waitText(text: String) { compose.waitUntil(15_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
    private fun tap(text: String) { waitText(text); compose.onNodeWithText(text).performScrollTo().performClick() }
    private fun capture(name: String) {
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val file = File(compose.activity.externalMediaDirs.first(), "stage11-$name.png")
        val automation = instrumentation.uiAutomation
        val bitmap = requireNotNull(automation.takeScreenshot())
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        listOf("mkdir -p /sdcard/Download/hestia-stage11", "cp ${file.absolutePath} /sdcard/Download/hestia-stage11/$name.png").forEach { command ->
            automation.executeShellCommand(command).use { descriptor -> java.io.FileInputStream(descriptor.fileDescriptor).use { it.readBytes() } }
        }
    }

    @Test fun simplifiedNavigationPersistedSetupEducationAndContacts() {
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Bienvenido a Hestia").fetchSemanticsNodes().isNotEmpty() || compose.onAllNodesWithTag("tab_summary").fetchSemanticsNodes().isNotEmpty() }
        if (compose.onAllNodesWithText("Bienvenido a Hestia").fetchSemanticsNodes().isNotEmpty()) {
            capture("onboarding")
            tap("Comenzar")
            waitText("Tu cuenta Google")
            compose.activityRule.scenario.recreate()
            waitText("Tu cuenta Google")
            compose.onNodeWithText("Bienvenido a Hestia").assertDoesNotExist()
            listOf("Continuar sin cuenta", "Continuar sin Gmail", "Continuar sin activar", "Continuar sin agenda", "Continuar sin avisos", "Entrar a Hestia").forEach { tap(it) }
        }
        waitText("Tu semana")
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).assertCountEquals(3)
        compose.onNodeWithTag("tab_summary").assertIsSelected()
        compose.onNodeWithText("Llamadas").assertIsDisplayed()
        compose.onNodeWithText("Mensajes SMS").assertDoesNotExist()
        capture("resumen")
        compose.activityRule.scenario.recreate()
        waitText("Tu semana")
        compose.onNodeWithText("Bienvenido a Hestia").assertDoesNotExist()
        compose.onNodeWithText("Ajustes").performClick()
        tap("Muy grande")
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Muy grande").fetchSemanticsNodes().any { it.config.getOrElse(SemanticsProperties.Selected) { false } } }
        tap("Alto contraste")
        compose.onNodeWithTag("tab_summary").performClick()
        waitText("Tu semana")
        capture("resumen-texto-grande")
        compose.onNodeWithText("Ajustes").performClick()
        tap("Restablecer apariencia")
        compose.onNodeWithTag("tab_education").performClick()
        waitText(lessons.first().title)
        capture("aprender")
        tap(lessons.first().title)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val sourceMonitor = Instrumentation.ActivityMonitor(IntentFilter(Intent.ACTION_VIEW).apply { addDataScheme("https") }, null, true)
        instrumentation.addMonitor(sourceMonitor)
        try { tap(lessons.first().sources.first().title); assertEquals(1, sourceMonitor.hits) }
        finally { instrumentation.removeMonitor(sourceMonitor) }
        lessons.forEachIndexed { index, lesson ->
            waitText(lesson.title)
            lesson.sources.forEach { compose.onNodeWithText(it.title).assertExists() }
            tap("Practicar")
            tap(lesson.correctAnswer)
            compose.onNodeWithText(lesson.feedback).assertExists()
            tap(if (index < 4) "Siguiente lección" else "Terminar")
        }
        waitText("5 de 5 completadas")
        compose.onNodeWithTag("tab_family").performClick()
        waitText("Tu lista está vacía")
        capture("familia")
        fun addContact(name: String, phone: String) {
            tap("Agregar contacto")
            compose.onNodeWithText("Nombre").performTextInput(name)
            compose.onNodeWithText("Teléfono").performTextInput(phone)
            compose.onNodeWithText("Guardar contacto").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Guardar contacto").fetchSemanticsNodes().isEmpty() }
            waitText(name)
        }
        addContact("Ana (prueba)", "+56900000001")
        addContact("Luis (prueba)", "+56900000002")
        compose.activityRule.scenario.recreate()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("tab_family").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("tab_family").performClick()
        waitText("Ana (prueba)")
        compose.onNodeWithText("Luis (prueba)").assertExists()
        val dialMonitor = Instrumentation.ActivityMonitor(IntentFilter(Intent.ACTION_DIAL).apply { addDataScheme("tel") }, null, true)
        instrumentation.addMonitor(dialMonitor)
        try { compose.onNodeWithContentDescription("Llamar a Ana (prueba)").performScrollTo().performClick(); assertEquals(1, dialMonitor.hits) }
        finally { instrumentation.removeMonitor(dialMonitor) }
        listOf("Ana (prueba)", "Luis (prueba)").forEach { name ->
            compose.onNodeWithContentDescription("Eliminar a $name").performScrollTo().performClick()
            compose.onNodeWithText("Eliminar contacto").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText(name).fetchSemanticsNodes().isEmpty() }
        }
        waitText("Tu lista está vacía")
        compose.onNodeWithTag("tab_summary").performClick()
        waitText("Tu semana")
        compose.activityRule.scenario.recreate()
        waitText("Tu semana")
        compose.onNodeWithText("Bienvenido a Hestia").assertDoesNotExist()
    }
}
