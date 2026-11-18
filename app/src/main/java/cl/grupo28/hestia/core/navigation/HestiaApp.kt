package cl.grupo28.hestia.core.navigation

import android.content.Intent
import cl.grupo28.hestia.domain.auth.AuthState
import cl.grupo28.hestia.features.automatic.*
import kotlinx.coroutines.launch
import cl.grupo28.hestia.features.account.AccountViewModel
import cl.grupo28.hestia.features.gmail.*
import androidx.core.net.toUri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import cl.grupo28.hestia.core.HestiaViewModel
import cl.grupo28.hestia.core.components.*
import cl.grupo28.hestia.core.theme.HestiaTheme
import cl.grupo28.hestia.domain.WeekRange
import cl.grupo28.hestia.features.onboarding.OnboardingScreen
import cl.grupo28.hestia.features.summary.SummaryScreen
import cl.grupo28.hestia.features.education.*
import cl.grupo28.hestia.features.family.FamilyScreen
import cl.grupo28.hestia.features.settings.SettingsScreen
import cl.grupo28.hestia.features.calls.CallSetup
import cl.grupo28.hestia.features.calls.rememberCallSetup

private data class MainDestination(val route: String, val label: String, val icon: HestiaSymbol)
private val destinations = listOf(
    MainDestination("summary", "Resumen", HestiaSymbol.HOME),
    MainDestination("education", "Aprender", HestiaSymbol.BOOK),
    MainDestination("family", "Familia", HestiaSymbol.FAMILY),
)

@Composable
fun HestiaApp(viewModel: HestiaViewModel, accountViewModel: AccountViewModel, gmailViewModel: GmailViewModel, authorizeGmail: (String, String) -> Unit, revokeGmail: (String, (Boolean) -> Unit) -> Unit, automaticModel: AutomaticViewModel? = null, authorizeAutomatic: (String,String)->Unit = {_,_->}, notificationEvent: Pair<String,String>? = null, consumedNotification: ()->Unit = {}) {
    val scope = rememberCoroutineScope()
    val logout: () -> Unit = { scope.launch { val warning=automaticModel?.beforeLogout(); accountViewModel.signOut(warning) } }
    val revokeManual: (String,(Boolean)->Unit)->Unit = { email, done -> scope.launch {
        val backend=automaticModel?.disconnectForManualRevocation()
        revokeGmail(email) { native -> done(native || backend==true) }
    } }
    val state by viewModel.state.collectAsState()
    val realAccount by accountViewModel.state.collectAsState()
    val demoMode by AutomaticEnvironmentFactory.mode(LocalContext.current).collectAsState()
    val account=if(demoMode)AuthState(false,message="DEMO LOCAL: la cuenta real no se utiliza en este modo.")else realAccount
    val context = LocalContext.current
    val realGmail by gmailViewModel.state.collectAsState()
    val gmail=if(demoMode)GmailUiState()else realGmail
    val callSetup = rememberCallSetup()
    HestiaTheme(state.settings.display) {
        if (state.loading) {
            Surface(Modifier.fillMaxSize()) { Box(Modifier.systemBarsPadding(), contentAlignment = Alignment.Center) { LoadingState("Abriendo Hestia…") } }
        } else if (!state.ready) {
            Surface(Modifier.fillMaxSize()) { Box(Modifier.systemBarsPadding().padding(24.dp), contentAlignment = Alignment.Center) { ErrorState(state.error ?: "No pudimos abrir Hestia.", viewModel::reload) } }
        } else if (!state.settings.onboardingComplete) {
            BackHandler(state.settings.onboardingStep > 0 && !state.saving && !account.busy) { viewModel.previousOnboarding() }
            Scaffold(topBar = { BrandHeader() }) { padding ->
                Box(Modifier.padding(padding)) { OnboardingScreen(state.settings.onboardingStep, state.saving, viewModel::advanceOnboarding, viewModel::previousOnboarding, account, { accountViewModel.signIn(context) }, logout, accountViewModel::retry, gmail, { gmailViewModel.begin(false, authorizeGmail) }, { gmailViewModel.begin(true, authorizeGmail) }, { gmailViewModel.disconnect(revokeManual) }, callSetup) }
            }
        } else {
            MainNavigation(callSetup, viewModel, accountViewModel, gmailViewModel, authorizeGmail, revokeGmail, automaticModel, authorizeAutomatic, notificationEvent, consumedNotification)
        }
        if (state.ready && state.error != null) AlertDialog(onDismissRequest = viewModel::clearError,
            title = { Text("Revisemos este paso") }, text = { Text(state.error!!) },
            confirmButton = { TextButton(viewModel::clearError, Modifier.heightIn(min = 48.dp)) { Text("Entendido") } })
    }
}

@Composable
private fun BrandHeader(back: (() -> Unit)? = null, settings: (() -> Unit)? = null, callPreview: Boolean = false) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            if (back != null) TextButton(back, Modifier.heightIn(min = 48.dp)) { HestiaIcon(HestiaSymbol.BACK); Spacer(Modifier.width(8.dp)); Text("Volver") }
            if (back == null || callPreview) Row(verticalAlignment = Alignment.CenterVertically) { HestiaMark(Modifier.size(36.dp)); Spacer(Modifier.width(8.dp)); Text("Hestia", style = MaterialTheme.typography.titleLarge) }
            if (!callPreview) AutomaticEnvironmentBadge()
            if (settings != null) TextButton(settings, Modifier.heightIn(min = 48.dp)) {
                HestiaIcon(HestiaSymbol.SETTINGS); Spacer(Modifier.width(6.dp)); Text("Ajustes", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun MainNavigation(callSetup: CallSetup, viewModel: HestiaViewModel, accountViewModel: AccountViewModel, gmailViewModel: GmailViewModel, authorizeGmail: (String, String) -> Unit, revokeGmail: (String, (Boolean) -> Unit) -> Unit, automaticModel: AutomaticViewModel? = null, authorizeAutomatic: (String,String)->Unit = {_,_->}, notificationEvent: Pair<String,String>? = null, consumedNotification: ()->Unit = {}) {
    val scope = rememberCoroutineScope()
    val logout: () -> Unit = { scope.launch { val warning=automaticModel?.beforeLogout(); accountViewModel.signOut(warning) } }
    val revokeManual: (String,(Boolean)->Unit)->Unit = { email, done -> scope.launch {
        val backend=automaticModel?.disconnectForManualRevocation()
        revokeGmail(email) { native -> done(native || backend==true) }
    } }
    val state by viewModel.state.collectAsState()
    val realAccount by accountViewModel.state.collectAsState()
    val demoMode by AutomaticEnvironmentFactory.mode(LocalContext.current).collectAsState()
    val account=if(demoMode)AuthState(false,message="DEMO LOCAL: la cuenta real no se utiliza en este modo.")else realAccount
    val realGmail by gmailViewModel.state.collectAsState()
    val gmail=if(demoMode)GmailUiState()else realGmail
    val nav = rememberNavController()
    val notificationState = automaticModel?.state?.collectAsState()?.value
    LaunchedEffect(notificationEvent, account.account?.id, account.verified, notificationState?.eligible, notificationState?.busy, notificationState?.demoSelected, notificationState?.demoPersona) {
        notificationEvent?.let { (id, tag) ->
            if (tag.startsWith("demo:")) {
                if (automaticModel == null || !automaticModel.demoAvailable || !demoMode || notificationState?.demoPersona != tag.removePrefix("demo:")) {
                    consumedNotification()
                } else if (notificationState.demoSelected && notificationState.eligible && !notificationState.busy) {
                    nav.navigate("gmail") { launchSingleTop=true }
                    automaticModel.open(id) // gateway revalidates ownership against the authenticated demo session
                    consumedNotification()
                }
            } else {
                val owner=account.account?.id
                if (account.verified && owner!=null) {
                    if (AutomaticNotifications.accountTag(owner)==tag && automaticModel!=null) { nav.navigate("gmail") { launchSingleTop=true }; automaticModel.open(id) }
                    consumedNotification()
                } else if (demoMode) consumedNotification()
            }
        }
    }
    val automatic = automaticModel?.state?.collectAsState()?.value
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "summary"
    val callPreview = route == callWarningPreviewRoute
    val phone = phoneRoute
    val tabs = if (phone == null) destinations else listOf(
        destinations.first(), MainDestination(phone, "Teléfono", HestiaSymbol.PHONE),
    ) + destinations.drop(1)
    val selected = when { route.startsWith("lesson") -> "education"; route == "settings" || route == "gmail" -> "summary"; else -> route }
    val detail = route == "gmail" || route == "settings" || route.startsWith("lesson") || callPreview
    val context = LocalContext.current
    fun openMain(target: String) { nav.navigate(target) { popUpTo("summary") { inclusive = target == "summary" }; launchSingleTop = true } }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { BrandHeader(if (detail) { { nav.popBackStack(); Unit } } else null, if (route == "summary") { { nav.navigate("settings") } } else null, callPreview) },
        bottomBar = {
            if (!callPreview) {
                Surface(color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Row(Modifier.fillMaxWidth().navigationBarsPadding().selectableGroup().padding(if (phone == null) 12.dp else 8.dp), horizontalArrangement = Arrangement.spacedBy(if (phone == null) 12.dp else 4.dp)) {
                        tabs.forEach { destination ->
                            val active = selected == destination.route
                            Surface(Modifier.weight(1f), shape = MaterialTheme.shapes.medium, color = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface) {
                                Column(Modifier.fillMaxWidth().heightIn(min = 76.dp).testTag("tab_${destination.route}").selectable(active, role = Role.Tab, onClick = { openMain(destination.route) }).padding(horizontal = 4.dp, vertical = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    HestiaIcon(destination.icon)
                                    Text(destination.label, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal, style = MaterialTheme.typography.labelMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                }
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        val noMotion = state.settings.display.reduceMotion
        NavHost(nav, startDestination = "summary", modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            enterTransition = { if (noMotion) EnterTransition.None else fadeIn(tween(120)) }, exitTransition = { if (noMotion) ExitTransition.None else fadeOut(tween(80)) }) {
            composable("summary") {
                SummaryScreen(
                    WeekRange.current(), state.summary, gmail, automatic, callSetup.state.active,
                    showCalls = phone == null,
                ) { nav.navigate("gmail") }
            }
            addPhoneDestination(callSetup) { callWarningPreviewRoute?.let(nav::navigate) }
            addCallWarningPreview { nav.popBackStack() }
            composable("education") { EducationScreen(state.settings.completedLessons) { nav.navigate("lesson/$it") } }
            composable("family") {
                FamilyScreen(state.settings.contacts, state.saving, viewModel::addContact, viewModel::removeContact) {
                    openExternal(context, dialIntent(it.phone), viewModel::showError)
                }
            }
            composable("settings") { SettingsScreen(state.settings.display, state.saving, viewModel::setDisplay, account, { accountViewModel.signIn(context) }, logout, accountViewModel::retry, gmail, { gmailViewModel.begin(false, authorizeGmail) }, { gmailViewModel.begin(true, authorizeGmail) }, { gmailViewModel.disconnect(revokeManual) }, { nav.navigate("gmail") }, callSetup, showCallSettings = phone == null) }
            composable("gmail") { GmailScreen(gmail, { gmailViewModel.begin(false, authorizeGmail) }, { gmailViewModel.begin(true, authorizeGmail) }, { gmailViewModel.disconnect(revokeManual) }, automaticAvailable = automaticModel != null, manualAvailable = !demoMode) { automaticModel?.let { AutomaticPanel(it, authorizeAutomatic, revokeGmail, account.account?.email) } } }
            composable("lesson/{id}", arguments = listOf(navArgument("id") { type = NavType.IntType })) { backStack ->
                val lesson = lessons.firstOrNull { it.id == backStack.arguments?.getInt("id") }
                if (lesson == null) ErrorState("No encontramos esta lección.") { openMain("education") }
                else LessonScreen(lesson, state.saving, {
                    viewModel.completeLesson(lesson.id) {
                        if (lesson.id < lessons.size) nav.navigate("lesson/${lesson.id + 1}") { popUpTo("education") }
                        else nav.popBackStack("education", false)
                    }
                }, { source -> if(demoMode)viewModel.showError("DEMO LOCAL: los enlaces externos no se abren durante la demostración.") else openExternal(context, Intent(Intent.ACTION_VIEW, source.url.toUri()), viewModel::showError) })
            }
        }
    }
}
