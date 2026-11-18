package cl.grupo28.hestia.features.calls

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import cl.grupo28.hestia.core.components.HestiaCard
import cl.grupo28.hestia.core.components.HestiaIcon
import cl.grupo28.hestia.core.components.HestiaSymbol
import cl.grupo28.hestia.core.components.Page
import cl.grupo28.hestia.core.components.PrimaryButton
import cl.grupo28.hestia.core.components.SecondaryButton
import cl.grupo28.hestia.core.components.SectionHeader
import cl.grupo28.hestia.core.theme.HestiaTheme

private const val PreviewCaller = "202 555 0147"

private enum class SimulatedAnswer(val title: String) {
    Rejected("Rechazaste la llamada de prueba"),
    Answered("Contestaste la llamada de prueba"),
}

@Composable
internal fun CallWarningPreviewScreen(onClose: () -> Unit) {
    var answer by rememberSaveable { mutableStateOf<SimulatedAnswer?>(null) }
    val outcome = answer
    if (outcome != null) {
        Page("Simulación terminada", "Llamada de prueba") {
            HestiaCard { SectionHeader(outcome.title) }
            PrimaryButton("Probar de nuevo", { answer = null })
            SecondaryButton("Volver a Teléfono", onClose)
        }
        return
    }

    HestiaIncomingCall(
        number = PreviewCaller,
        reported = true,
        testLabel = "Llamada de prueba",
        onReject = { answer = SimulatedAnswer.Rejected },
        onAnswer = { answer = SimulatedAnswer.Answered },
    )
}

@Composable
internal fun HestiaIncomingCall(
    number: String,
    reported: Boolean,
    testLabel: String?,
    onReject: () -> Unit,
    onAnswer: () -> Unit,
) {
    var showDetails by rememberSaveable { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val screenHeight = maxHeight
        Column(
            Modifier.widthIn(max = 464.dp).fillMaxWidth()
                .verticalScroll(rememberScrollState()).heightIn(min = screenHeight)
                .padding(horizontal = 22.dp).padding(top = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier.padding(bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Llamada entrante",
                    Modifier.semantics { heading() },
                    style = type.titleLarge,
                    textAlign = TextAlign.Center,
                )
                Text(number, style = type.displaySmall, textAlign = TextAlign.Center)
                testLabel?.let { Text(it, style = type.bodyMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center) }
            }

            if (reported) CallRiskCard({ showDetails = true }, Modifier.padding(bottom = 20.dp), inclusive = true)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CallAction("Rechazar", colors.error, colors.onError, Modifier.weight(1f), rotation = 135f, diameter = 88.dp) {
                    onReject()
                }
                CallAction("Contestar", colors.secondary, colors.onSecondary, Modifier.weight(1f), diameter = 88.dp) {
                    onAnswer()
                }
            }
        }
    }

    if (reported && showDetails) CallRiskDetails(number, testLabel != null, { showDetails = false })
}

@Composable
internal fun CallRiskCard(onDetails: () -> Unit, modifier: Modifier = Modifier, inclusive: Boolean = false, onReport: (() -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    Surface(
        modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = colors.errorContainer,
        border = BorderStroke(1.dp, colors.outlineVariant),
        shadowElevation = 2.dp,
    ) {
        Column(
            Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HestiaIcon(HestiaSymbol.ALERT, tint = colors.onErrorContainer)
                Text(
                    if (inclusive) "SOSPECHOSO" else "Posible llamada fraudulenta",
                    Modifier.semantics { heading() },
                    style = type.titleLarge,
                    color = colors.onErrorContainer,
                    textAlign = TextAlign.Center,
                )
            }
            Text(
                if (inclusive) "No compartas claves ni códigos" else "Este número tiene reportes de posibles estafas",
                style = type.bodyLarge,
                color = colors.onSurface,
                textAlign = TextAlign.Center,
            )
            TextButton(
                onClick = onDetails,
                modifier = Modifier.heightIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
            ) {
                Text("Ver por qué", style = type.labelLarge)
            }
            if (onReport != null) SecondaryButton("Reportar este número", onReport)
        }
    }
}

@Composable
internal fun CallRiskDetails(number: String, synthetic: Boolean, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Por qué Hestia la marcó", style = MaterialTheme.typography.titleLarge) },
        text = {
            Text(
                (if (synthetic) "El $number figura como sospechoso en la lista de prueba.\n\n"
                else "Este número tiene reportes de posibles estafas.\n\n") +
                    "No compartas claves ni códigos. Si dice ser de tu banco, verifica por sus canales oficiales.",
                modifier = Modifier.verticalScroll(rememberScrollState()),
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        confirmButton = { PrimaryButton("Entendido", onClose) },
    )
}

@Composable
internal fun CallAction(
    label: String,
    color: Color,
    iconColor: Color,
    modifier: Modifier = Modifier,
    rotation: Float = 0f,
    enabled: Boolean = true,
    diameter: Dp = 72.dp,
    onClick: () -> Unit,
) {
    Column(
        modifier.clip(MaterialTheme.shapes.medium).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(Modifier.size(diameter), shape = CircleShape,
            color = if (enabled) color else MaterialTheme.colorScheme.surfaceVariant,
            shadowElevation = if (enabled) 2.dp else 0.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                HestiaIcon(HestiaSymbol.PHONE, modifier = Modifier.size(if (diameter > 72.dp) 36.dp else 28.dp).rotate(rotation),
                    tint = if (enabled) iconColor else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

@Preview(name = "Alerta de llamada", widthDp = 393, heightDp = 780, showBackground = true)
@Preview(name = "Alerta con texto ampliado", widthDp = 360, heightDp = 640, fontScale = 1.3f, showBackground = true)
@Composable
private fun CallWarningPreview() {
    HestiaTheme {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            CallWarningPreviewScreen(onClose = {})
        }
    }
}
