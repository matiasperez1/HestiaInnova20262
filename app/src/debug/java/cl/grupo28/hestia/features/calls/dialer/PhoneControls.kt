package cl.grupo28.hestia.features.calls.dialer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

internal enum class PhoneGlyph(val path: String) {
    Mic("M9,9 V5 A3,3 0,0 1 15,5 V9 A3,3 0,0 1 9,9 M6,9 V10 A6,6 0,0 0 18,10 V9 M12,16 V21 M9,21 H15"),
    Muted("M9,9 V5 A3,3 0,0 1 15,5 V9 A3,3 0,0 1 9,9 M6,9 V10 A6,6 0,0 0 18,10 V9 M12,16 V21 M9,21 H15 M3,3 L21,21"),
    Digits("M5,5 h1 M11.5,5 h1 M18,5 h1 M5,11 h1 M11.5,11 h1 M18,11 h1 M5,17 h1 M11.5,17 h1 M18,17 h1"),
    Speaker("M3,9 H7 L12,5 V19 L7,15 H3 Z M15,9 Q18,12 15,15 M18,6 Q24,12 18,18"),
    Contacts("M6,3 H19 V21 H6 Z M3,7 H7 M3,12 H7 M3,17 H7 M14,12 A2.5,2.5 0,1 0 14,7 A2.5,2.5 0,1 0 14,12 M10,17 Q10,14 14,14 Q18,14 18,17"),
    Help("M12,22 A10,10 0,1 0 12,2 A10,10 0,1 0 12,22 M9,9 A3,3 0,1 1 14,11 Q12,12 12,15 M12,18 h0.1"),
    Report("M5,21 V3 M5,4 Q8,2 12,4 Q16,6 20,4 V14 Q16,16 12,14 Q8,12 5,14"),
    Delete("M3,12 L9,6 H21 V18 H9 Z M12,10 L16,14 M16,10 L12,14"),
}

private val glyphs = PhoneGlyph.entries.associateWith { glyph ->
    ImageVector.Builder(glyph.name, 24.dp, 24.dp, 24f, 24f).addPath(
        pathData = PathParser().parsePathString(glyph.path).toNodes(),
        stroke = SolidColor(Color.Black), strokeLineWidth = 1.7f,
        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
    ).build()
}

@Composable
internal fun PhoneIcon(
    glyph: PhoneGlyph,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier,
) {
    Icon(glyphs.getValue(glyph), contentDescription = null, tint = tint, modifier = modifier)
}

@Composable
internal fun PhoneKeypad(onKey: (Char) -> Unit, showLetters: Boolean = false, onPlus: (() -> Unit)? = null) {
    val letters = listOf("", "", "ABC", "DEF", "GHI", "JKL", "MNO", "PQRS", "TUV", "WXYZ")
    val gap = if (showLetters) 16.dp else 12.dp
    BoxWithConstraints(Modifier.widthIn(max = 368.dp).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        val diameter = minOf((maxWidth - gap * 2) / 3, if (showLetters) 100.dp else 88.dp)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            for (row in listOf("123", "456", "789", "*0#")) {
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { digit ->
                        val plus = onPlus.takeIf { digit == '0' }
                        Surface(
                            modifier = Modifier.width(diameter).heightIn(min = diameter).fillMaxHeight().clip(CircleShape)
                                .combinedClickable(
                                    role = Role.Button,
                                    onClick = { onKey(digit) },
                                    onLongClickLabel = "Introducir +".takeIf { plus != null },
                                    onLongClick = plus,
                                ),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Column(
                                Modifier.padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(digit.toString(), style = MaterialTheme.typography.displaySmall)
                                val caption = if (plus != null) "+" else if (digit.isDigit()) letters[digit.digitToInt()] else ""
                                if (showLetters && caption.isNotEmpty()) {
                                    Text(caption, style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun PhoneControl(
    label: String,
    glyph: PhoneGlyph,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier.clip(MaterialTheme.shapes.medium).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            Modifier.size(80.dp), shape = CircleShape,
            color = if (selected) colors.secondaryContainer else colors.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.outlineVariant),
        ) {
            Box(contentAlignment = Alignment.Center) {
                PhoneIcon(glyph, if (enabled) colors.onSurface else colors.onSurfaceVariant.copy(alpha = 0.48f),
                    Modifier.size(32.dp))
            }
        }
        Text(label, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge,
            color = if (enabled) colors.onSurface else colors.onSurfaceVariant)
    }
}
