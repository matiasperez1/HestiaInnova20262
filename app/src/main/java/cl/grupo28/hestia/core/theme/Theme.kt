package cl.grupo28.hestia.core.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import cl.grupo28.hestia.core.accessibility.DisplayPreferences

private val Colors = lightColorScheme(
    primary = DeepBlue, onPrimary = Color.White,
    primaryContainer = Color(0xFFE1EDEA), onPrimaryContainer = DeepBlue,
    secondary = PetroleumInk, onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDEBE6), onSecondaryContainer = DeepBlue,
    tertiary = Color(0xFF765413), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF8E6BE), onTertiaryContainer = Color(0xFF4B350B),
    background = Ivory, onBackground = DeepBlue,
    surface = Color(0xFFFFFCF6), onSurface = DeepBlue,
    surfaceVariant = Color(0xFFECE8DE), onSurfaceVariant = Color(0xFF526068),
    outline = Color(0xFF79827E), outlineVariant = Color(0xFFD9DCD4),
    error = Color(0xFF963C35), onError = Color.White,
    errorContainer = Color(0xFFF8E4DE), onErrorContainer = Color(0xFF6F2C26),
    surfaceContainer = Color(0xFFFFFCF6),
)

@Composable
fun HestiaTheme(preferences: DisplayPreferences = DisplayPreferences(), content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val colors = if (preferences.highContrast) Colors.copy(
        onSurfaceVariant = DeepBlue, outline = DeepBlue, outlineVariant = DeepBlue,
        secondary = Color(0xFF164C47), background = Color.White, surface = Color.White,
    ) else Colors
    CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * preferences.textSize.scale)) {
        MaterialTheme(
            colorScheme = colors,
            typography = HestiaTypography,
            shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp)),
            content = content,
        )
    }
}
