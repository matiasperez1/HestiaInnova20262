package cl.grupo28.hestia.features.calls

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cl.grupo28.hestia.core.components.HestiaCard
import cl.grupo28.hestia.core.components.Page
import cl.grupo28.hestia.core.components.SecondaryButton
import cl.grupo28.hestia.core.components.SectionHeader
import cl.grupo28.hestia.domain.WeekRange
import cl.grupo28.hestia.features.calls.dialer.DialerPad
import cl.grupo28.hestia.features.calls.dialer.PhoneGlyph
import cl.grupo28.hestia.features.calls.dialer.PhoneIcon
import cl.grupo28.hestia.features.calls.dialer.cleanDialNumber
import cl.grupo28.hestia.features.contacts.AgendaDirectory
import cl.grupo28.hestia.features.contacts.AgendaMatches
import cl.grupo28.hestia.features.contacts.AgendaUse
import cl.grupo28.hestia.features.contacts.rememberAgendaAccess
import cl.grupo28.hestia.features.onboarding.AgendaRehearsal
import cl.grupo28.hestia.features.summary.WeeklyCallCards
import cl.grupo28.hestia.features.summary.rememberCallOverview

@Composable
internal fun PhoneScreen(calls: CallSetup, onPreview: () -> Unit, onLearnCalls: () -> Unit) {
    var section by rememberSaveable { mutableStateOf(PhoneSection.RECENT) }
    var number by rememberSaveable { mutableStateOf("") }
    var trusted by rememberSaveable { mutableStateOf(false) }
    val agenda = if (section == PhoneSection.CONTACTS || section == PhoneSection.KEYPAD) rememberAgendaAccess() else null
    val selectNumber: (String) -> Unit = {
        number = cleanDialNumber(it)
        section = PhoneSection.KEYPAD
    }
    BackHandler(section != PhoneSection.RECENT) { section = PhoneSection.RECENT }
    Column(Modifier.fillMaxSize()) {
        PhoneSections(section) { section = it }
        key(section) {
            when (section) {
                PhoneSection.RECENT -> Page("Recientes", "Llamadas gestionadas por Hestia.") {
                    PhoneRecentList(selectNumber, manage = true)
                }
                PhoneSection.CONTACTS -> if (agenda != null) {
                    AgendaDirectory(agenda, AgendaUse.Call, onDialerSetup = { section = PhoneSection.PROTECTION },
                        extra = { SecondaryButton("Contactos de confianza", { trusted = true }) })
                }
                PhoneSection.KEYPAD -> Page("Teclado", "Marca un número o elígelo en Contactos.") {
                    DialerPad(number, { number = it }, onSetup = { section = PhoneSection.PROTECTION }, matches = {
                        agenda?.let { AgendaMatches(it, number) { picked -> number = cleanDialNumber(picked) } }
                    })
                }
                PhoneSection.PROTECTION -> PhoneProtection(calls, onPreview, onLearnCalls)
            }
        }
    }
    if (trusted) AlertDialog(
        onDismissRequest = { trusted = false },
        title = { Text("Contactos de confianza") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            PhoneContacts({ picked -> trusted = false; selectNumber(picked) }, manage = true)
        } },
        confirmButton = { TextButton({ trusted = false }, Modifier.heightIn(min = 48.dp)) { Text("Cerrar") } },
    )
}

private enum class PhoneSection(val label: String, val icon: PhoneGlyph) {
    RECENT("Recientes", PhoneGlyph.Recents),
    CONTACTS("Contactos", PhoneGlyph.Contacts),
    KEYPAD("Teclado", PhoneGlyph.Digits),
    PROTECTION("Protección", PhoneGlyph.Shield),
}

@Composable
private fun PhoneSections(selected: PhoneSection, onSelect: (PhoneSection) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography.labelMedium
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val minimumWidths = PhoneSection.entries.associateWith { section ->
        val labelWidth = with(density) {
            measurer.measure(section.label, style = typography.copy(fontWeight = FontWeight.Bold)).size.width.toDp()
        }
        maxOf(48.dp, labelWidth + 8.dp)
    }
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        val gap = 6.dp
        val gaps = gap * (PhoneSection.entries.size - 1)
        val minimumWidth = minimumWidths.values.fold(0.dp) { total, width -> total + width }
        val contentWidth = maxOf(maxWidth, minimumWidth + gaps)
        val available = contentWidth - gaps
        val equalWidth = available / PhoneSection.entries.size
        val extra = (available - minimumWidth) / PhoneSection.entries.size
        Row(
            Modifier.horizontalScroll(rememberScrollState()).width(contentWidth).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            for (section in PhoneSection.entries) {
                val active = section == selected
                val weight = if (minimumWidths.values.all { it <= equalWidth }) 1f
                else (minimumWidths.getValue(section) + extra).value
                Column(
                    Modifier.weight(weight).heightIn(min = 48.dp).clip(MaterialTheme.shapes.medium)
                        .background(if (active) colors.primaryContainer else colors.surface)
                        .selectable(active, role = Role.Tab, onClick = { onSelect(section) })
                        .testTag("phone_section_${section.name.lowercase()}")
                        .padding(horizontal = 3.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    PhoneIcon(section.icon, tint = colors.primary)
                    Text(section.label, style = typography, textAlign = TextAlign.Center, maxLines = 1,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun PhoneProtection(calls: CallSetup, onPreview: () -> Unit, onLearnCalls: () -> Unit) {
    val overview = rememberCallOverview(WeekRange.current())
    var tools by rememberSaveable { mutableStateOf(false) }
    var reputation by rememberSaveable { mutableStateOf(false) }
    var testAgenda by rememberSaveable { mutableStateOf(false) }
    Page("Protección", "Consulta números reportados y configura cómo atender las llamadas.") {
        DialerSetup()
        HestiaCard {
            SectionHeader("Identificación de llamadas")
            CallRoleContent(calls)
            if (calls.state.active) CallNoticeContent(calls)
            SecondaryButton("Aprender sobre llamadas sospechosas", onLearnCalls)
        }
        PersonalReportsPanel()
        SecondaryButton(if (reputation) "Ocultar reputación de demostración" else "Reputación de demostración", { reputation = !reputation })
        if (reputation) DemoCallReportsCard()
        WeeklyCallCards(overview, calls.state.active)
        SecondaryButton(if (tools) "Ocultar herramientas DEBUG" else "Herramientas DEBUG", { tools = !tools })
        if (tools) {
            SecondaryButton("Probar alerta de llamada", onPreview)
            SecondaryButton("Probar paso de agenda", { testAgenda = true })
        }
    }
    if (testAgenda) AgendaRehearsal { testAgenda = false }
}
