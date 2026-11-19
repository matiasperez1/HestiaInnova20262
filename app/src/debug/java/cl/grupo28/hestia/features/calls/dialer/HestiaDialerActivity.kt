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
import cl.grupo28.hestia.features.calls.PhoneContacts
import cl.grupo28.hestia.features.calls.PhoneRecentList
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.catch
import java.io.IOException
import java.util.Locale

class HestiaDialerActivity : ComponentActivity() {
    private var number by mutableStateOf("")
    private var error by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        number = savedInstanceState?.getString("number") ?: dialedNumber(intent)
        val display = DataStoreSettingsRepository(applicationContext).settings.map { it.display }
            .catch { if (it !is IOException) throw it }
        setContent {
            val prefs by display.collectAsState(initial = DisplayPreferences())
            val requestPhone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
                error = if (it) "Permiso concedido. Pulsa Llamar para continuar."
                    else "Concede permiso de llamadas en Ajustes para usar el marcador."
            }
            HestiaTheme(prefs) {
                val colors = MaterialTheme.colorScheme
                val type = MaterialTheme.typography
                val canDial = number.any(Char::isDigit)
                var selection by rememberSaveable { mutableStateOf<DialerSelection?>(null) }
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
                        Text(
                            number.ifEmpty { "Ingresa un número" }.let {
                                if (canDial) PhoneNumberUtils.formatNumber(number, Locale.getDefault().country.ifEmpty { "CL" }) ?: number else it
                            },
                            style = if (number.isEmpty()) type.titleLarge else type.displaySmall,
                            textAlign = TextAlign.Center,
                        )
                        PhoneKeypad(
                            onKey = { number = clean(number + it); error = null },
                            showLetters = true,
                            onPlus = { if (number.isEmpty()) number = "+" },
                        )
                        Box(Modifier.widthIn(max = 340.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CallAction("Llamar", colors.secondary, colors.onSecondary, enabled = canDial, diameter = 96.dp) {
                                when {
                                    !holdsDialerRole() -> error = "Selecciona Hestia como aplicación de teléfono en la pestaña Teléfono."
                                    ContextCompat.checkSelfPermission(this@HestiaDialerActivity, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED ->
                                        requestPhone.launch(Manifest.permission.CALL_PHONE)
                                    else -> placeCall()
                                }
                            }
                            TextButton(
                                onClick = { number = number.dropLast(1); error = null },
                                modifier = Modifier.align(Alignment.CenterEnd).sizeIn(minWidth = 56.dp, minHeight = 56.dp)
                                    .semantics { contentDescription = "Borrar último dígito" },
                                enabled = number.isNotEmpty(),
                            ) { PhoneIcon(PhoneGlyph.Delete, modifier = Modifier.size(30.dp)) }
                        }
                        error?.let { Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            color = colors.error, style = type.bodyMedium, textAlign = TextAlign.Center) }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            SecondaryButton("Recientes", { selection = DialerSelection.RECENT }, Modifier.weight(1f))
                            SecondaryButton("Contactos", { selection = DialerSelection.CONTACTS }, Modifier.weight(1f))
                        }
                    }
                }
                selection?.let { source ->
                    val select: (String) -> Unit = { picked -> number = clean(picked); error = null; selection = null }
                    AlertDialog(
                        onDismissRequest = { selection = null },
                        title = { Text(source.label) },
                        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                            when (source) {
                                DialerSelection.RECENT -> PhoneRecentList(select)
                                DialerSelection.CONTACTS -> PhoneContacts(select)
                            }
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
        error = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("number", number)
        super.onSaveInstanceState(outState)
    }

    private fun dialedNumber(intent: Intent): String =
        clean(intent.data?.takeIf { intent.action == Intent.ACTION_DIAL && it.scheme == "tel" }?.schemeSpecificPart.orEmpty())

    private fun clean(input: String): String = input.filter { it in "0123456789*#+" }.take(32)

    private fun placeCall() {
        try {
            // Telecom selects the line and uses the preloaded dialer for emergencies.
            getSystemService(TelecomManager::class.java).placeCall(Uri.fromParts("tel", number, null), Bundle())
            error = null
        } catch (_: SecurityException) {
            error = "Android no autorizó la llamada. Revisa el permiso de teléfono y la aplicación predeterminada."
        } catch (_: RuntimeException) {
            error = "No se pudo iniciar la llamada. Revisa el número y la línea."
        }
    }
}

private enum class DialerSelection(val label: String) { RECENT("Recientes"), CONTACTS("Contactos") }
