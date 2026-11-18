package cl.grupo28.hestia.features.calls

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cl.grupo28.hestia.data.calls.CallPersona
import cl.grupo28.hestia.data.calls.DemoCallReports
import cl.grupo28.hestia.domain.automatic.AutomaticError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun ContextualCallReport(
    number: String, onClose: () -> Unit, postCall: Boolean = false, contextLabel: String? = null,
    onAnswered: (CallPersona) -> Unit = {},
    onSubmit: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val reports = remember(context) { DemoCallReports.from(context) }
    val persona by reports.persona.collectAsState()
    val ownership by reports.ownership.collectAsState()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }
    var duplicate by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }

    fun done() {
        onAnswered(persona)
        onClose()
    }

    // Local knowledge avoids opening a second editor. The server is the authority on duplicates.
    if (sent || duplicate || ownership[persona]?.containsKey(number) == true) {
        AlertDialog(
            onDismissRequest = ::done,
            title = { Text(if (sent) "Reporte enviado" else "Ya reportado por ti") },
            text = { Text(if (sent) feedback ?: "Tu reporte se guardó en DEMO LOCAL."
                else "Puedes corregir o retirar tu reporte en Protección, en Mis reportes.") },
            confirmButton = { TextButton(::done, Modifier.heightIn(min = 48.dp)) { Text("Entendido") } },
        )
    } else ReportDialog(
        ReportForm(number), persona, ::done,
        onSave = { confirmed, category ->
            if (!busy) {
                busy = true
                feedback = null
                val author = persona
                onSubmit?.invoke()
                scope.launch {
                    try {
                        reports.save(author, confirmed, category)
                        sent = true
                        onAnswered(author)
                        try { reports.sync(author) }
                        catch (error: CancellationException) { throw error }
                        catch (_: Exception) { feedback = "Tu reporte se guardó. La reputación se actualizará al sincronizar." }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        if (error is AutomaticError && error.code == "CALL_REPORT_ALREADY_EXISTS") {
                            duplicate = true
                            onAnswered(author)
                        } else feedback = "No pudimos confirmar el envío. Revisa la conexión y vuelve a intentar."
                    } finally { busy = false }
                }
            }
        }, fixed = true, postCall = postCall, contextLabel = contextLabel, busy = busy, feedback = feedback,
        submitLabel = if (onSubmit != null) "Reportar y colgar" else null,
    )
}
