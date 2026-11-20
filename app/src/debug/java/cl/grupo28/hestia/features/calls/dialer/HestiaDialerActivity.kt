package cl.grupo28.hestia.features.calls.dialer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager
import android.telephony.PhoneNumberUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import cl.grupo28.hestia.core.components.*
import cl.grupo28.hestia.core.theme.HestiaTheme
import cl.grupo28.hestia.core.accessibility.DisplayPreferences
import cl.grupo28.hestia.data.DataStoreSettingsRepository
import cl.grupo28.hestia.features.calls.CallAction
import cl.grupo28.hestia.features.calls.PhoneRecentList
import cl.grupo28.hestia.features.contacts.AgendaMatches
import cl.grupo28.hestia.features.contacts.AgendaUse
import cl.grupo28.hestia.features.contacts.AgendaWindow
import cl.grupo28.hestia.features.contacts.rememberAgendaAccess
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.catch
import java.io.IOException
import java.util.Locale

class HestiaDialerActivity : ComponentActivity() {
    private var number by mutableStateOf("")
    private var intentRevision by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        number = savedInstanceState?.getString("number") ?: dialedNumber(intent)
        val display = DataStoreSettingsRepository(applicationContext).settings.map { it.display }
            .catch { if (it !is IOException) throw it }
        setContent {
            val prefs by display.collectAsState(initial = DisplayPreferences())
            HestiaTheme(prefs) {
                val colors = MaterialTheme.colorScheme
                val type = MaterialTheme.typography
                var selection by rememberSaveable { mutableStateOf<DialerSelection?>(null) }
                val agenda = if (number.isNotEmpty() || selection == DialerSelection.CONTACTS) rememberAgendaAccess() else null
                Scaffold(
                    containerColor = colors.background,
                    topBar = {
                        Row(
                            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton({ finish() }, Modifier.heightIn(min = 48.dp)) {
                                HestiaIcon(HestiaSymbol.BACK)
                                Spacer(Modifier.width(8.dp))
                                Text("Volver")
                            }
                            Text("Marcador", Modifier.semantics { heading() }, style = type.titleLarge)
                        }
                    },
                ) { padding ->
                    Column(
                        Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
                            .verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        key(intentRevision) {
                            DialerPad(number, { number = it }, matches = {
                                agenda?.let { AgendaMatches(it, number) { picked -> number = cleanDialNumber(picked) } }
                            })
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            SecondaryButton("Recientes", { selection = DialerSelection.RECENT }, Modifier.weight(1f))
                            SecondaryButton("Contactos", { selection = DialerSelection.CONTACTS }, Modifier.weight(1f))
                        }
                    }
                }
                selection?.let { source ->
                    val select: (String) -> Unit = { picked -> number = cleanDialNumber(picked); selection = null }
                    if (source == DialerSelection.CONTACTS && agenda != null) {
                        AgendaWindow(agenda, AgendaUse.PickNumber, onClose = { selection = null }, onChooseNumber = select)
                    } else AlertDialog(
                        onDismissRequest = { selection = null },
                        title = { Text(source.label) },
                        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                            PhoneRecentList(select)
                        } },
                        confirmButton = { TextButton({ selection = null }) { Text("Cerrar") } },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        number = dialedNumber(intent)
        intentRevision++
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("number", number)
        super.onSaveInstanceState(outState)
    }

    private fun dialedNumber(intent: Intent): String =
        cleanDialNumber(intent.data?.takeIf { intent.action == Intent.ACTION_DIAL && it.scheme == "tel" }?.schemeSpecificPart.orEmpty())

}

internal fun cleanDialNumber(input: String): String =
    input.filter { it in "0123456789*#+,;" }.take(32)

internal enum class DialAttempt(val message: String?) {
    Started(null),
    NeedsRole("Elige Hestia como aplicación de teléfono en Protección."),
    NeedsPermission(null),
    Forbidden("Android no autorizó la llamada. Revisa el permiso y la aplicación predeterminada."),
    Failed("No se pudo iniciar la llamada. Revisa el número y la línea."),
}

internal data class PhoneCalling(val feedback: String?, val dial: (String) -> DialAttempt)

@Composable
internal fun rememberPhoneCalling(): PhoneCalling {
    val context = LocalContext.current
    var feedback by remember { mutableStateOf<String?>(null) }
    val requestPhone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        feedback = if (it) "Permiso concedido. Pulsa Llamar para continuar."
            else "Concede el permiso de teléfono para realizar llamadas con Hestia."
    }
    return PhoneCalling(feedback) { number ->
        feedback = null
        when {
            !context.holdsDialerRole() -> DialAttempt.NeedsRole
            ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED -> {
                requestPhone.launch(Manifest.permission.CALL_PHONE)
                DialAttempt.NeedsPermission
            }
            else -> try {
                val telecom = context.getSystemService(TelecomManager::class.java)
                if (telecom == null) DialAttempt.Failed else {
                    telecom.placeCall(Uri.fromParts("tel", number, null), Bundle())
                    DialAttempt.Started
                }
            } catch (_: SecurityException) {
                DialAttempt.Forbidden
            } catch (_: RuntimeException) {
                DialAttempt.Failed
            }
        }
    }
}

@Composable
internal fun DialerPad(
    number: String,
    onNumberChange: (String) -> Unit,
    onSetup: (() -> Unit)? = null,
    matches: @Composable () -> Unit = {},
) {
    val calling = rememberPhoneCalling()
    var attempt by remember(number) { mutableStateOf<DialAttempt?>(null) }
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    val canDial = number.any(Char::isDigit)
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val formatted = PhoneNumberUtils.formatNumber(number, Locale.getDefault().country.ifEmpty { "CL" })
        Text(
            if (canDial) formatted ?: number else number.ifEmpty { "Ingresa un número" },
            style = if (number.isEmpty()) type.titleLarge else type.displaySmall,
            textAlign = TextAlign.Center,
        )
        PhoneKeypad(
            onKey = { onNumberChange(cleanDialNumber(number + it)) },
            showLetters = true,
            onPlus = { if (number.isEmpty()) onNumberChange("+") },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CallAction("Llamar", colors.secondary, colors.onSecondary, enabled = canDial, diameter = 88.dp) {
                attempt = calling.dial(number)
            }
            TextButton(
                onClick = { onNumberChange(number.dropLast(1)) },
                modifier = Modifier.sizeIn(minWidth = 56.dp, minHeight = 56.dp)
                    .semantics { contentDescription = "Borrar último dígito" },
                enabled = number.isNotEmpty(),
            ) { PhoneIcon(PhoneGlyph.Delete, modifier = Modifier.size(30.dp)) }
        }
        val message = attempt?.message ?: calling.feedback.takeIf { attempt == DialAttempt.NeedsPermission }
        message?.let { Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            color = colors.onSurfaceVariant, style = type.bodyMedium, textAlign = TextAlign.Center) }
        if (attempt == DialAttempt.NeedsRole && onSetup != null) {
            SecondaryButton("Ir a Protección", onSetup)
        }
        matches()
    }
}

private enum class DialerSelection(val label: String) { RECENT("Recientes"), CONTACTS("Contactos") }
