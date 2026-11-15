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
    primary = Action, onPrimary = CardSurface,
    primaryContainer = Color(0xFFF4DDB8), onPrimaryContainer = Ink,
    secondary = Positive, onSecondary = CardSurface,
    secondaryContainer = Color(0xFFE7EBDD), onSecondaryContainer = Ink,
    tertiary = Amber, onTertiary = Ink,
    tertiaryContainer = Color(0xFFF4DDB8), onTertiaryContainer = Color(0xFF24211F),
    background = Background, onBackground = Ink,
    surface = Color(0xFFFFF9F0), onSurface = Ink,
    surfaceVariant = Color(0xFFF4DDB8), onSurfaceVariant = Color(0xFF514A44),
    outline = Color(0xFF514A44), outlineVariant = Color(0xFFCFC2B2),
    error = Color(0xFF963B32), onError = CardSurface,
    errorContainer = Color(0xFFF4DEDA), onErrorContainer = Color(0xFF963B32),
    surfaceContainer = CardSurface,
    surfaceContainerLowest = Background, surfaceContainerLow = CardSurface,
    surfaceContainerHigh = CardSurface, surfaceContainerHighest = CardSurface,
    surfaceBright = CardSurface, surfaceDim = Background, surfaceTint = Action,
    inverseSurface = Ink, inverseOnSurface = CardSurface, inversePrimary = Color(0xFFF4DDB8),
    scrim = Ink,
)

@Composable
fun HestiaTheme(preferences: DisplayPreferences = DisplayPreferences(), content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val colors = if (preferences.highContrast) Colors.copy(
        onSurfaceVariant = Ink, outline = Ink, outlineVariant = Ink,
        secondary = Color(0xFF435A3B), background = CardSurface, surface = CardSurface,
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
