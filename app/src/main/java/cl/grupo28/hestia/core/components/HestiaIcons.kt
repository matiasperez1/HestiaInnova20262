package cl.grupo28.hestia.core.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import cl.grupo28.hestia.core.theme.Amber
import cl.grupo28.hestia.core.theme.DeepBlue

enum class HestiaSymbol(val path: String) {
    HOME("M3,10 L12,3 L21,10 M5,9 L5,21 L10,21 L10,14 L14,14 L14,21 L19,21 L19,9"),
    SHIELD("M12,3 L21,7 L20,14 Q18,19 12,22 Q6,19 4,14 L3,7 Z M8,12 L11,15 L16,9"),
    BOOK("M12,6 Q6,2 3,5 L3,20 Q7,17 12,21 Q17,17 21,20 L21,5 Q18,2 12,6 Z M12,6 L12,21"),
    FAMILY("M8,11 A4,4 0,1 0,8,3 A4,4 0,1 0,8,11 M2,21 L2,18 Q2,14 8,14 Q14,14 14,18 L14,21 M17,4 Q22,5 20,10 M18,14 Q23,14 23,19 L23,21"),
    MAIL("M3,5 L21,5 L21,19 L3,19 Z M3,6 L12,13 L21,6"),
    PHONE("M8,3 L4,4 Q2,12 9,19 Q14,23 20,20 L21,16 L16,13 L13,16 Q9,14 8,10 L11,8 Z"),
    MESSAGE("M3,4 L21,4 L21,17 L9,17 L3,22 Z M7,9 L17,9 M7,13 L14,13"),
    SHARE("M7,12 L17,6 M7,12 L17,18 M7,12 A2,2 0,1 0,3,12 A2,2 0,1 0,7,12 M21,5 A2,2 0,1 0,17,5 A2,2 0,1 0,21,5 M21,19 A2,2 0,1 0,17,19 A2,2 0,1 0,21,19"),
    ACCESSIBILITY("M12,6 A2,2 0,1 0,12,2 A2,2 0,1 0,12,6 M3,9 L21,9 M12,9 L12,15 M12,15 L7,22 M12,15 L17,22"),
    SETTINGS("M12,3 L12,6 M12,18 L12,21 M3,12 L6,12 M18,12 L21,12 M5,5 L7,7 M17,17 L19,19 M5,19 L7,17 M17,7 L19,5 M18,12 A6,6 0,1 0,6,12 A6,6 0,1 0,18,12 M14,12 A2,2 0,1 0,10,12 A2,2 0,1 0,14,12"),
    ARROW("M5,12 L19,12 M13,6 L19,12 L13,18"),
    BACK("M19,12 L5,12 M11,6 L5,12 L11,18"),
    ALERT("M12,3 L22,21 L2,21 Z M12,9 L12,14 M12,17 L12,18"),
    CLOCK("M21,12 A9,9 0,1 0,3,12 A9,9 0,1 0,21,12 M12,6 L12,12 L16,14"),
    CHECK("M5,12 L10,17 L20,6"),
    LOCK("M5,10 L19,10 L19,22 L5,22 Z M8,10 L8,6 A4,4 0,0 1,16,6 L16,10 M12,15 L12,18"),
}

private val vectors = HestiaSymbol.entries.associateWith { symbol ->
    ImageVector.Builder(symbol.name, 24.dp, 24.dp, 24f, 24f).addPath(
        pathData = PathParser().parsePathString(symbol.path).toNodes(),
        stroke = SolidColor(Color.Black), strokeLineWidth = 1.7f,
        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
    ).build()
}

/** Icons are decorative: each use is paired with a visible text label. */
@Composable
fun HestiaIcon(symbol: HestiaSymbol, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.primary) {
    Icon(vectors.getValue(symbol), contentDescription = null, modifier = modifier.size(24.dp), tint = tint)
}

/** A small flame sheltered by a roof, drawn as a code-native vector. */
@Composable
fun HestiaMark(modifier: Modifier = Modifier, onDark: Boolean = false) {
    Canvas(modifier.size(52.dp)) {
        val unit = size.minDimension / 52f
        val roof = androidx.compose.ui.graphics.Path().apply {
            moveTo(7 * unit, 24 * unit); lineTo(26 * unit, 8 * unit); lineTo(45 * unit, 24 * unit)
            moveTo(12 * unit, 23 * unit); lineTo(12 * unit, 43 * unit); lineTo(40 * unit, 43 * unit); lineTo(40 * unit, 23 * unit)
        }
        drawPath(roof, if (onDark) Color.White else DeepBlue, style = androidx.compose.ui.graphics.drawscope.Stroke(2.6f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
        val flame = androidx.compose.ui.graphics.Path().apply {
            moveTo(27 * unit, 19 * unit)
            cubicTo(28 * unit, 26 * unit, 35 * unit, 27 * unit, 33 * unit, 34 * unit)
            cubicTo(31 * unit, 41 * unit, 20 * unit, 39 * unit, 20 * unit, 33 * unit)
            cubicTo(19 * unit, 28 * unit, 25 * unit, 26 * unit, 27 * unit, 19 * unit)
        }
        drawPath(flame, Amber)
    }
}
