package cl.grupo28.hestia.core.accessibility

import androidx.compose.runtime.Immutable

enum class TextSize(val label: String, val scale: Float) {
    STANDARD("Estándar", 1f), LARGE("Grande", 1.15f), VERY_LARGE("Muy grande", 1.3f)
}

/** Local appearance preferences; spoken reading remains outside this stage. */
@Immutable
data class DisplayPreferences(
    val textSize: TextSize = TextSize.STANDARD,
    val highContrast: Boolean = false,
    val reduceMotion: Boolean = false,
)
