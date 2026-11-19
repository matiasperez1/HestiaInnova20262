package cl.grupo28.hestia.features.automatic

import androidx.compose.foundation.layout.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import cl.grupo28.hestia.core.components.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** This entire control surface is absent from the release source set. */
@Composable
fun AutomaticEnvironmentPanel(model:AutomaticViewModel,onLearnEmail: (() -> Unit)?,production:@Composable ()->Unit) {
    if(!model.demoAvailable){production();return}
    val state by model.state.collectAsState()
    var leaving by rememberSaveable{mutableStateOf(false)}
    var tools by rememberSaveable { mutableStateOf(false) }
    if (!state.demoSelected) {
        HestiaCard {
            SectionHeader("Herramientas de demostración")
            SecondaryButton("Activar DEMO LOCAL", { model.selectDemo(true) }, enabled = !state.busy)
        }
        production()
        return
    }
    val context=androidx.compose.ui.platform.LocalContext.current
    val permitted=android.os.Build.VERSION.SDK_INT<37||androidx.core.content.ContextCompat.checkSelfPermission(context,"android.permission.ACCESS_LOCAL_NETWORK")==android.content.pm.PackageManager.PERMISSION_GRANTED
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){model.refresh()}
    val info=state.demoInfo
    var clearing by rememberSaveable{mutableStateOf(false)}
    val monitorState by DemoMonitor.state.collectAsState()
    LaunchedEffect(state.demoPersona,state.demoSelected) {
        val saved=DemoMonitor.read(context)
        if(saved.optBoolean("active") && (!state.demoSelected || saved.optString("persona")!=state.demoPersona)) DemoMonitor.stop(context)
        else if(saved.optBoolean("active") && DemoMonitor.state.value=="Desactivados") DemoMonitor.state.value="Servicio detenido por el sistema"
    }
    var permissionTick by remember { mutableIntStateOf(0) }
    val notificationsAllowed = remember(permissionTick) { DemoMonitor.notificationsAllowed(context) }
    val requestNotifications=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionTick++; if(it) DemoMonitor.start(context,state.demoPersona) else DemoMonitor.state.value="Permiso de notificaciones pendiente" }
    HestiaCard {
        SectionHeader("Correos de demostración", "DEMO LOCAL")
        Text(when {
            !permitted -> "Permiso de conexión local pendiente"
            state.consultationIssue == ConsultationIssue.OFFLINE -> "Sin conexión con el computador"
            state.consultationIssue == ConsultationIssue.ERROR -> "No pudimos consultar la demostración"
            !state.checked -> "Consultando la demostración…"
            info?.workerRunning == false -> "Conectada · procesamiento sin confirmar"
            else -> "Conectada al computador"
        }, style = MaterialTheme.typography.bodyLarge)
        if (!permitted) SecondaryButton("Permitir conexión local", { permission.launch("android.permission.ACCESS_LOCAL_NETWORK") })
        if (!state.checked && state.summary != null) Text("Puedes consultar los resultados de la última conexión.", style = MaterialTheme.typography.bodyMedium)
        if (state.checked && state.alerts.isEmpty()) Text("Aún no hay correos de prueba con señales de riesgo.", style = MaterialTheme.typography.bodyMedium)
        state.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        SecondaryButton("Actualizar estado", model::refresh, enabled = permitted && !state.busy)
        SecondaryButton(if (tools) "Ocultar herramientas de demostración" else "Herramientas de demostración", { tools = !tools })
    }
    if (tools) HestiaCard {
        SectionHeader("Avisos en segundo plano",monitorState)
        Text("La demo usa una conexión local con este computador. Los avisos pueden retrasarse cuando el teléfono ahorra energía.",style=MaterialTheme.typography.bodyMedium)
        if(monitorState in listOf("Activos","Activando","Sin conexión con el computador")) SecondaryButton("Detener avisos en segundo plano",{DemoMonitor.stop(context)})
        else SecondaryButton("Activar avisos en segundo plano",{
            when {
                !permitted -> DemoMonitor.state.value="Permiso de red local pendiente"
                !notificationsAllowed && android.os.Build.VERSION.SDK_INT>=33 && androidx.core.content.ContextCompat.checkSelfPermission(context,android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED -> requestNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                !notificationsAllowed -> DemoMonitor.state.value="Permiso de notificaciones pendiente"
                else -> DemoMonitor.start(context,state.demoPersona)
            }
        })
        if(monitorState=="Permiso de notificaciones pendiente") SecondaryButton("Abrir ajustes de notificaciones",{context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,context.packageName))})
    }
    if (tools) HestiaCard {
        SectionHeader("Diagnóstico y datos de prueba",if(state.demoPersona=="alice")"Ana · identidad sintética" else "Bruno · identidad sintética")
        Text("Backend: ${if(state.checked)"conectado" else "sin conexión confirmada"}",style=MaterialTheme.typography.bodyLarge)
        Text("Worker: ${if(!state.checked)"sin confirmar" else if(info?.workerRunning==true)"en ejecución" else "sin respuesta"}",style=MaterialTheme.typography.bodyLarge)
        if(!state.checked&&info!=null)Text("Resultados guardados; el backend no está confirmado.",style=MaterialTheme.typography.bodyMedium)
        Text("Eventos procesados: ${info?.processed?:"Sin datos"} · pendientes: ${info?.pending?:"Sin datos"}",style=MaterialTheme.typography.bodyMedium)
        Text("Reintentos: ${info?.retries?:"Sin datos"} · duplicados: ${info?.duplicates?:"Sin datos"}",style=MaterialTheme.typography.bodyMedium)
        Text("Última simulación: ${info?.lastSimulation?.let(::demoDate)?:"Sin datos"}",style=MaterialTheme.typography.bodyMedium)
        Text("Correos simulados analizados: ${state.summary?.analyzed?:"Sin datos"}",style=MaterialTheme.typography.bodyLarge)
        Text("Correos simulados sospechosos: ${state.summary?.suspicious?:"Sin datos"}",style=MaterialTheme.typography.bodyLarge)
        Text("Avisos simulados generados: ${info?.notificationCount?:"Sin datos"}",style=MaterialTheme.typography.bodyLarge)
        Text("Genera los correos sintéticos desde la consola del computador.",style=MaterialTheme.typography.bodyMedium)
        SecondaryButton(if(state.demoPersona=="alice")"Cambiar a Bruno (demo)" else "Cambiar a Ana (demo)",{DemoMonitor.stop(context);model.selectDemo(true,if(state.demoPersona=="alice")"bob" else "alice")},enabled=!state.busy)
        SecondaryButton("Limpiar datos demo",{clearing=true},enabled=state.checked&&!state.busy)
        Text("Estos avisos son de demostración; no utilizan FCM ni revisan Gmail real.",style=MaterialTheme.typography.bodyMedium)
        SecondaryButton("Volver al modo real", { leaving = true }, enabled = !state.busy)
    }
    if(info?.notifications?.isNotEmpty()==true) HestiaCard {
        SectionHeader("Bandeja de avisos simulados","DEMO LOCAL")
        info.notifications.take(5).forEach{notice->SecondaryButton("Ver aviso simulado · ${demoDate(notice.createdAt)}",{model.open(notice.eventId)},enabled=!state.busy)}
    }
    AutomaticAlertResults(model,"DEMO LOCAL · correo sintético", onLearnEmail)
    if(clearing)AlertDialog(onDismissRequest={clearing=false},title={Text("¿Limpiar datos demo?")},text={Text("Solo se eliminarán los correos, eventos y avisos sintéticos de esta identidad demo. La otra identidad, Gmail real y la sesión Supabase se conservan.")},confirmButton={TextButton({clearing=false;model.clearDemo()}){Text("Limpiar simulaciones")}},dismissButton={TextButton({clearing=false}){Text("Cancelar")}})
    if(leaving)AlertDialog(onDismissRequest={leaving=false},title={Text("¿Volver al modo real?")},text={Text("Se dejarán de consultar las simulaciones y volverá la configuración real de Hestia. Puede reanudarse la verificación de tu cuenta por internet. Los datos demo se conservan separados.")},confirmButton={TextButton({leaving=false;DemoMonitor.stop(context);model.selectDemo(false)}){Text("Volver al modo real")}},dismissButton={TextButton({leaving=false}){Text("Cancelar")}})
}
private fun demoDate(s:String)=runCatching{DateTimeFormatter.ofPattern("d MMM, HH:mm:ss",java.util.Locale.forLanguageTag("es-CL")).withZone(ZoneId.systemDefault()).format(Instant.parse(s))}.getOrDefault("Sin datos")

@Composable
fun AutomaticEnvironmentBadge(){
    val selected by AutomaticEnvironmentFactory.mode(androidx.compose.ui.platform.LocalContext.current).collectAsState()
    if(selected) Text("DEMO LOCAL",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
}
