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
import cl.grupo28.hestia.domain.EducationIds
import cl.grupo28.hestia.features.education.EducationContent
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class HestiaNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun waitText(text: String) { compose.waitUntil(15_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
    private fun tap(text: String) {
        waitText(text)
        val node = compose.onNodeWithText(text)
        if (!node.isDisplayed()) node.performScrollTo()
        node.performClick()
    }
    private fun capture(name: String) {
        if (InstrumentationRegistry.getArguments().getString("textOnly") == "true") return
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
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).assertCountEquals(4)
        compose.onNodeWithTag("tab_summary").assertIsSelected()
        compose.onNodeWithText("Estado de protección").assertExists()
        waitText("Sin registros de llamadas reales.")
        tap("Pedir ayuda")
        waitText("Pedir ayuda")
        compose.onNode(hasText("Cerrar") and hasAnyAncestor(isDialog())).performClick()
        compose.onNodeWithTag("tab_phone").performClick()
        compose.onNodeWithTag("tab_phone").assertIsSelected()
        tap("Protección")
        compose.onNodeWithText("Llamadas").performScrollTo().assertIsDisplayed()
        tap("Aprender sobre llamadas sospechosas")
        waitText("¿Te llaman del banco?")
        compose.onNodeWithTag("tab_education").assertIsSelected()
        compose.onNodeWithText("Volver").performClick()
        waitText("Protección")
        compose.onNodeWithTag("tab_summary").performClick()
        waitText("Tu semana")
        tap("Consultar Teléfono")
        waitText("Aprender sobre llamadas sospechosas")
        compose.onNodeWithTag("tab_summary").performClick()
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
        waitText("Tu progreso")
        EducationContent.modules.forEach { compose.onNodeWithText(it.title).assertExists() }
        capture("aprender")
        val lesson = EducationContent.lessonsById.getValue(EducationIds.BANK_CALL)
        compose.onNodeWithContentDescription("Continuar: Estafas telefónicas").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Continuar lección: ${lesson.title}").performScrollTo().performClick()
        waitText("Situación")
        waitText(lesson.situation)
        waitText("Puedes leer a tu ritmo. Al final hay dos preguntas para repasar.")
        capture("educacion-introduccion")
        tap("Comenzar lección")
        waitText("¿En qué me fijo?")
        tap("Volver al paso anterior")
        waitText("Situación")
        tap("Comenzar lección")
        waitText("¿En qué me fijo?")
        compose.onNodeWithTag("tab_summary").performClick()
        compose.activityRule.scenario.recreate()
        waitText("Tu semana")
        compose.onNodeWithTag("tab_education").performClick()
        waitText("¿En qué me fijo?")
        tap("Ver qué hacer")
        waitText("¿Y qué debería hacer?")
        capture("educacion-que-hacer")
        tap("Ir a las preguntas")
        waitText("Pregunta 1 de 2")
        tap("Dictar el código")
        waitText("Hay una opción más segura")
        compose.onNodeWithText(lesson.exercise.explanation).assertExists()
        tap("Intentar de nuevo")
        tap("Cortar y verificar con el banco")
        waitText("Es una opción segura")
        tap("Siguiente pregunta")
        waitText("Pregunta 2 de 2")
        compose.onNodeWithText("Ver el consejo final").assertIsNotEnabled()
        capture("educacion-segunda-pregunta")
        tap("Que te pidan tu contraseña por teléfono")
        waitText(lesson.followUpExercise.explanation)
        tap("Volver al paso anterior")
        waitText("Pregunta 1 de 2")
        waitText(lesson.exercise.explanation)
        tap("Siguiente pregunta")
        waitText(lesson.followUpExercise.explanation)
        tap("Ver el consejo final")
        waitText("Un consejo para recordar")
        tap("Fuente de esta recomendación")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val sourceMonitor = Instrumentation.ActivityMonitor(IntentFilter(Intent.ACTION_VIEW).apply { addDataScheme("https") }, null, true)
        instrumentation.addMonitor(sourceMonitor)
        try { tap(lesson.sources.first().title); assertEquals(1, sourceMonitor.hits) }
        finally { instrumentation.removeMonitor(sourceMonitor) }
        tap("Terminar lección")
        waitText("Terminaste esta lección")
        tap("Volver a la pantalla anterior")
        compose.onNodeWithContentDescription("Repetir lección: ${lesson.title}").performScrollTo().performClick()
        waitText("Situación")
        tap("Volver")
        tap("Volver")
        compose.onNodeWithTag("tab_family").performClick()
        waitText("Tu lista está vacía")
        capture("familia")
        fun addContact(name: String, phone: String) {
            tap("Agregar contacto de confianza")
            compose.onNodeWithText("Nombre").performTextInput(name)
            compose.onNode(hasText("Teléfono") and hasSetTextAction()).performTextInput(phone)
            compose.onNodeWithText("Guardar contacto").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Guardar contacto").fetchSemanticsNodes().isEmpty() }
            waitText(name)
        }
        addContact("Ana (prueba)", "+56900000001")
        tap("Administrar contactos")
        addContact("Luis (prueba)", "+56900000002")
        compose.activityRule.scenario.recreate()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("tab_family").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("tab_family").performClick()
        waitText("Ana (prueba)")
        compose.onNodeWithText("Luis (prueba)").assertExists()
        tap("Volver a Familia")
        compose.onNodeWithTag("tab_education").performClick()
        compose.onNodeWithContentDescription("Comenzar: Qué hacer ante una posible estafa").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Abrir lección: Una advertencia y cómo pedir ayuda").performScrollTo().performClick()
        tap("Comenzar lección")
        waitText("¿En qué me fijo?")
        tap("Ver qué hacer")
        tap("Pedir ayuda a una persona de confianza")
        waitText("Elige a una persona de confianza.")
        compose.onNodeWithText("Ana (prueba)").assertExists()
        compose.onNodeWithText("Cerrar").performClick()
        compose.onNodeWithTag("tab_family").performClick()
        val dialMonitor = Instrumentation.ActivityMonitor(IntentFilter(Intent.ACTION_DIAL).apply { addDataScheme("tel") }, null, true)
        instrumentation.addMonitor(dialMonitor)
        try { compose.onNodeWithContentDescription("Llamar a Ana (prueba)").performScrollTo().performClick(); assertEquals(1, dialMonitor.hits) }
        finally { instrumentation.removeMonitor(dialMonitor) }
        tap("Administrar contactos")
        listOf("Ana (prueba)", "Luis (prueba)").forEach { name ->
            compose.onNodeWithContentDescription("Eliminar a $name").performScrollTo().performClick()
            compose.onNodeWithText("Eliminar contacto").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText(name).fetchSemanticsNodes().isEmpty() }
        }
        tap("Volver a Familia")
        waitText("Tu lista está vacía")
        compose.onNodeWithTag("tab_summary").performClick()
        waitText("Tu semana")
        compose.activityRule.scenario.recreate()
        waitText("Tu semana")
        compose.onNodeWithText("Bienvenido a Hestia").assertDoesNotExist()
    }
}
