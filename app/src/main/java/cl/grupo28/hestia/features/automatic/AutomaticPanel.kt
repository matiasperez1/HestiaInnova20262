package cl.grupo28.hestia.features.automatic

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cl.grupo28.hestia.core.components.*
import cl.grupo28.hestia.domain.HelpRequest
import cl.grupo28.hestia.domain.HelpTopic
import cl.grupo28.hestia.domain.WeekRange
import cl.grupo28.hestia.features.family.TrustHelpDialog
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun AutomaticPanel(model:AutomaticViewModel,authorize:(String,String)->Unit,revoke:(String,(Boolean)->Unit)->Unit,email:String?, onLearnEmail: (() -> Unit)? = null) {
    AutomaticEnvironmentPanel(model, onLearnEmail) { ProductionAutomaticPanel(model,authorize,revoke,email,onLearnEmail) }
}

@Composable
private fun ProductionAutomaticPanel(model:AutomaticViewModel,authorize:(String,String)->Unit,revoke:(String,(Boolean)->Unit)->Unit,email:String?, onLearnEmail: (() -> Unit)?) {
    val state by model.state.collectAsState()
    var consent by rememberSaveable{mutableStateOf(false)};var disconnect by rememberSaveable{mutableStateOf(false)}
    var renew by rememberSaveable{mutableStateOf(false)}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){model.enableNotifications()}
    val context=LocalContext.current
    val active=state.eligible&&state.checked&&state.status?.active==true&&runCatching{Instant.parse(state.status!!.expiration).isAfter(Instant.now())}.getOrDefault(false)
    HestiaCard {
        SectionHeader("Revisión automática",when{active->"Gmail activo";state.status?.status=="REAUTH_REQUIRED"->"Necesita volver a conectarse";else->"Sin conexión automática verificada"})
        Text(when {
            active -> "Revisamos los correos nuevos de tu bandeja de entrada."
            !state.configured -> "La revisión automática aún no está disponible. Puedes usar la revisión manual."
            else -> "Puedes autorizar la revisión de correos nuevos aunque Hestia esté cerrada."
        }, style=MaterialTheme.typography.bodyLarge)
        if(state.status?.status=="TEMP_ERROR")Text("La revisión no está completa. Conservamos los resultados disponibles.",style=MaterialTheme.typography.bodyLarge)
        state.status?.lastSync?.let{ Text("Última revisión confirmada: ${date(it)}",style=MaterialTheme.typography.bodyMedium) }
        state.status?.expiration?.let{ Text("Seguimiento vigente hasta: ${date(it)}",style=MaterialTheme.typography.bodyMedium) }
        if(state.status?.startedAt!=null) Text("El seguimiento comenzó el ${date(state.status!!.startedAt!!)}. No incluye todos tus correos anteriores.",style=MaterialTheme.typography.bodyMedium)
        if(!active)PrimaryButton(if(state.busy)"Un momento…" else "Activar revisión automática",{consent=true},enabled=state.configured&&state.eligible&&!state.busy)
        else SecondaryButton("Revisar ahora",model::sync,enabled=!state.busy)
        if(state.configured&&state.eligible)SecondaryButton("Actualizar estado",model::refresh,enabled=!state.busy)
        if(active) {
            Text(if(!AutomaticNotifications.ready())"Los avisos aún no están disponibles. Puedes consultar las alertas aquí." else if(AutomaticNotifications.allowed(context))"Avisos permitidos en este teléfono. Actívalos para vincularlo." else "Avisos desactivados. Las alertas siguen disponibles dentro de Hestia.",style=MaterialTheme.typography.bodyMedium)
            SecondaryButton("Activar avisos",{if(Build.VERSION.SDK_INT>=33)permission.launch(Manifest.permission.POST_NOTIFICATIONS)else model.enableNotifications()},enabled=!state.busy&&AutomaticNotifications.ready())
        }
        if(state.status?.status in listOf("ACTIVE","TEMP_ERROR","REAUTH_REQUIRED"))SecondaryButton("Desconectar revisión automática",{disconnect=true},enabled=!state.busy)
        state.message?.let { Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyLarge) }
        if(state.reconsent)SecondaryButton("Renovar permiso de Google",{renew=true},enabled=!state.busy)
        Text("Las señales son orientativas; no garantizan que un correo sea auténtico o una estafa.",style=MaterialTheme.typography.bodyMedium)
    }
    state.summary?.let{summary->HestiaCard {
        val week = WeekRange.current()
        val current = runCatching { Instant.parse(summary.weekStart).atZone(week.zone).toLocalDate() == week.start }.getOrDefault(false)
        SectionHeader(if (current) "Correos de esta semana" else "Correos de un período anterior",
            if(active && current) "Revisión automática" else "Resultados guardados")
        Text("${summary.analyzed} analizados · ${summary.suspicious} con señales de riesgo",style=MaterialTheme.typography.titleMedium)
        Text("Desde ${date(summary.weekStart)}. Los resultados se conservan durante 30 días.",style=MaterialTheme.typography.bodyMedium)}}
    AutomaticAlertResults(model, onLearnEmail = onLearnEmail)
    if(consent)AlertDialog(onDismissRequest={consent=false},title={Text("¿Revisar correos nuevos automáticamente?")},text={Text("El servidor de Hestia tendrá acceso de solo lectura a Gmail y guardará un permiso cifrado. Analizará los correos nuevos de Entrada y conservará resultados mínimos por 30 días; no guardará cuerpos ni adjuntos. Puedes desconectarlo cuando quieras. Cerrar sesión detiene los avisos en este teléfono; para detener la revisión usa Desconectar.",modifier=Modifier.verticalScroll(rememberScrollState()))},confirmButton={TextButton({consent=false;model.begin(authorize)}){Text("Autorizar Gmail")}},dismissButton={TextButton({consent=false}){Text("Ahora no")}})
    if (disconnect) DisconnectAutomaticDialog(onDismiss = { disconnect = false }, onConfirm = { delete ->
        disconnect = false
        model.disconnect(delete)
    })
    if(renew)AlertDialog(onDismissRequest={renew=false},title={Text("¿Renovar el permiso de Google?")},text={Text("Primero revocaremos todos los permisos de Hestia sobre tu cuenta Google, incluida la revisión manual. Luego podrás autorizar Gmail nuevamente. Tu sesión de Hestia se conserva.",modifier=Modifier.verticalScroll(rememberScrollState()))},confirmButton={TextButton({renew=false;email?.let{revoke(it){ok->if(ok)model.begin(authorize)else model.refresh()}}}){Text("Revocar y autorizar")}},dismissButton={TextButton({renew=false}){Text("Cancelar")}})

}

@Composable
internal fun DisconnectAutomaticDialog(onDismiss: () -> Unit, onConfirm: (Boolean) -> Unit) {
    var deleteResults by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("¿Desconectar Gmail automático?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Se detendrá la revisión. Hestia solicitará a Google revocar el permiso de correo. Esto también puede afectar la revisión manual. Tu sesión de Hestia se mantiene.")
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .toggleable(deleteResults, role = Role.Checkbox, onValueChange = { deleteResults = it }),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(deleteResults, onCheckedChange = null)
                    Text("Eliminar también los resultados guardados")
                }
            }
        },
        confirmButton = { TextButton({ onConfirm(deleteResults) }, Modifier.heightIn(min = 48.dp)) { Text("Desconectar") } },
        dismissButton = { TextButton(onDismiss, Modifier.heightIn(min = 48.dp)) { Text("Cancelar") } },
    )
}

@Composable
fun AutomaticAlertResults(model:AutomaticViewModel,sourceLabel:String?=null, onLearnEmail: (() -> Unit)? = null) {
    val state by model.state.collectAsState()
    state.alerts.forEach { alert -> HestiaCard(color=if(alert.risk=="HIGH")MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer){
        if(sourceLabel!=null) Text(sourceLabel,style=MaterialTheme.typography.labelLarge)
        Text(if(alert.risk=="HIGH")"Señales de riesgo alto" else "Precaución",style=MaterialTheme.typography.titleLarge)
        Text(alert.subject.ifBlank{"Correo sin asunto"},style=MaterialTheme.typography.bodyLarge)
        SecondaryButton("Revisar alerta",{model.open(alert.id)},enabled=!state.busy)
    }}
    if(state.next!=null)SecondaryButton("Ver más alertas",model::more,enabled=!state.busy)
    state.selected?.let { alert ->
        var askingForHelp by remember(alert.id, state.demoSelected, state.demoPersona) { mutableStateOf(false) }
        if (askingForHelp) {
            TrustHelpDialog(
                HelpRequest(HelpTopic.EMAIL_WARNING, demonstration = state.demoSelected),
                { askingForHelp = false },
            )
        } else AlertDialog(
            onDismissRequest = model::closeDetail,
            title = { Text(if (alert.risk == "HIGH") "Señales de riesgo alto" else "Precaución") },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (sourceLabel != null) Text(sourceLabel)
                Text(alert.subject.ifBlank { "Sin asunto" })
                Text(alert.sender.ifBlank { "Remitente no disponible" })
                Text(date(alert.receivedAt))
                Text("El remitente puede ser suplantado.")
                alert.reasons.forEach { Text(it) }
                if (alert.limited) Text("El análisis del contenido fue limitado. No revisamos adjuntos.")
                Text("No sigas enlaces ni entregues claves. Verifica por un canal oficial que ya conozcas.")
                if (alert.risk == "HIGH") SecondaryButton("Pedir ayuda con este correo", { askingForHelp = true })
                if (onLearnEmail != null) SecondaryButton("Aprender a reconocer correos falsos", onLearnEmail)
            } },
            confirmButton = { TextButton(model::closeDetail, Modifier.heightIn(min = 48.dp)) { Text("Entendido") } },
        )
    }
}

private fun date(value:String)=runCatching{DateTimeFormatter.ofPattern("d MMM, HH:mm",java.util.Locale.forLanguageTag("es-CL")).withZone(ZoneId.systemDefault()).format(Instant.parse(value))}.getOrDefault("Sin datos")
