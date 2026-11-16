package cl.grupo28.hestia.features.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cl.grupo28.hestia.core.components.*
import cl.grupo28.hestia.domain.auth.AuthState
import cl.grupo28.hestia.features.account.AccountCard

private data class SetupStep(val title: String, val text: String, val symbol: HestiaSymbol, val action: String? = null, val next: String = "Continuar")
private val steps = listOf(
    SetupStep("Bienvenido a Hestia", "Te ayudamos a identificar llamadas y correos sospechosos.", HestiaSymbol.HOME, next = "Comenzar"),
    SetupStep("Tu cuenta Google", "Tu cuenta te identifica en Hestia. Tus contactos y lecciones siguen guardados solo en este teléfono.", HestiaSymbol.LOCK, "Iniciar sesión con Google", "Continuar sin cuenta"),
    SetupStep("Conectar Gmail", "Hestia revisará tus correos para identificar posibles estafas.", HestiaSymbol.MAIL, "Autorizar lectura de Gmail", "Continuar sin Gmail"),
    SetupStep("Identificar llamadas", "Hestia puede identificar números sospechosos antes de que contestes.", HestiaSymbol.PHONE, "Activar identificación", "Continuar sin activar"),
    SetupStep("Llamadas de tus contactos", "Incluir tu agenda será opcional. Tus contactos de emergencia no necesitan ese permiso.", HestiaSymbol.FAMILY, "Incluir llamadas de mi agenda", "Continuar sin agenda"),
    SetupStep("Recibir avisos", "Te avisaremos si detectamos algo sospechoso.", HestiaSymbol.ALERT, "Activar notificaciones", "Continuar sin avisos"),
    SetupStep("A tu ritmo", "Puedes usar Hestia y configurar la protección más adelante.", HestiaSymbol.SHIELD, next = "Entrar a Hestia"),
)

@Composable
fun OnboardingScreen(step: Int, saving: Boolean, onNext: () -> Unit, onBack: () -> Unit, account: AuthState, onSignIn: () -> Unit, onSignOut: () -> Unit, onRetryAccount: () -> Unit) {
    val page = steps[step]
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text("Paso ${step + 1} de 7", style = MaterialTheme.typography.labelLarge)
        LinearProgressIndicator(progress = { (step + 1) / 7f }, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.secondary)
        IconTile(page.symbol)
        Text(page.title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
        Text(page.text, style = MaterialTheme.typography.bodyLarge)
        if (step == 6) HestiaCard {
            Text("Correos: sin conectar", style = MaterialTheme.typography.bodyLarge)
            Text("Llamadas: sin configurar", style = MaterialTheme.typography.bodyLarge)
        }
        if (step == 1) AccountCard(account, onSignIn, onSignOut, onRetryAccount)
        else page.action?.let {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(it, {}, enabled = false)
                Text("Disponible próximamente", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (step == 1 && account.account == null) {
            SecondaryButton(if (saving) "Guardando…" else page.next, onNext, enabled = !saving && !account.busy)
        } else PrimaryButton(if (saving) "Guardando…" else if (step == 1) "Continuar" else page.next, onNext, enabled = !saving && !account.busy)
        if (step > 0) TextButton(onBack, Modifier.heightIn(min = 48.dp), enabled = !saving && !account.busy) { Text("Atrás") }
    }
}
