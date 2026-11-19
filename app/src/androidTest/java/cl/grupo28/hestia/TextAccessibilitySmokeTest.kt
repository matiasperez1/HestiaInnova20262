package cl.grupo28.hestia

import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Exercises the Android accessibility tree without starting an accessibility service. */
class TextAccessibilitySmokeTest {
    @Test fun mainDestinationsExposeLabelsAtTheRequestedSystemScale() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("surfaceAudit") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val scale = requireNotNull(args.getString("fontScale")).toFloat()
        assertEquals(scale, context.resources.configuration.fontScale, 0.01f)
        val launch = requireNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName))
        context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

        val automation = instrumentation.uiAutomation
        fun nodes(root: AccessibilityNodeInfo): Sequence<AccessibilityNodeInfo> = sequence {
            yield(root)
            for (index in 0 until root.childCount) root.getChild(index)?.let { yieldAll(nodes(it)) }
        }
        fun currentNodes() = automation.rootInActiveWindow?.let(::nodes).orEmpty()
        fun clickTarget(node: AccessibilityNodeInfo) =
            generateSequence(node) { it.parent }.firstOrNull { it.isClickable && it.isEnabled }

        fun find(label: String, clickable: Boolean = false, heading: Boolean = false): AccessibilityNodeInfo {
            val deadline = SystemClock.uptimeMillis() + 15_000
            do {
                currentNodes().firstOrNull { node ->
                    (node.text?.toString() == label || node.contentDescription?.toString() == label) &&
                        node.isVisibleToUser && (!heading || node.isHeading) && (!clickable || clickTarget(node) != null)
                }?.let { return it }
                SystemClock.sleep(200)
            } while (SystemClock.uptimeMillis() < deadline)
            val labels = currentNodes().mapNotNull { it.text ?: it.contentDescription }.toList()
            error("No se encontró '$label' al ${scale * 100} %. Etiquetas visibles: $labels")
        }

        fun tap(label: String) {
            val target = requireNotNull(clickTarget(find(label, clickable = true)))
            assertTrue(target.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        }

        find("Tu semana")
        find("Teléfono", clickable = true)
        tap("Ajustes")
        find("Ajustes", heading = true)
        tap("Volver")
        find("Tu semana")
        tap("Familia")
        find("Familia", heading = true)
        tap("Aprender")
        find("Tu progreso")
        tap("Resumen")
        find("Tu semana")
    }
}
