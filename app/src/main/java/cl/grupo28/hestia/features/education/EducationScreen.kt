package cl.grupo28.hestia.features.education

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cl.grupo28.hestia.core.components.*

@Composable
fun EducationScreen(completed: Set<Int>, onLesson: (Int) -> Unit) {
    Page("Aprender", "Una lección a la vez. 2 a 3 minutos.") {
        Text("${completed.size} de 5 completadas", style = MaterialTheme.typography.labelLarge)
        lessons.forEach { lesson ->
            Surface(onClick = { onLesson(lesson.id) }, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
                Row(Modifier.fillMaxWidth().heightIn(min = 88.dp).padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(lesson.id.toString().padStart(2, '0'), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.secondary)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(lesson.title, style = MaterialTheme.typography.titleMedium)
                        Text(if (lesson.id in completed) "Completada" else "Por aprender", style = MaterialTheme.typography.bodyMedium)
                    }
                    HestiaIcon(if (lesson.id in completed) HestiaSymbol.CHECK else HestiaSymbol.ARROW)
                }
            }
        }
        Text("Material de Hestia basado en fuentes oficiales.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun LessonScreen(lesson: LessonUiModel, saving: Boolean, onComplete: () -> Unit, onSource: (OfficialSource) -> Unit) {
    var practicing by rememberSaveable(lesson.id) { mutableStateOf(false) }
    var answer by rememberSaveable(lesson.id) { mutableStateOf<Boolean?>(null) }
    var showLaw by rememberSaveable(lesson.id) { mutableStateOf(false) }
    Page(lesson.title, "Lección ${lesson.id} de 5 · 2 a 3 minutos") {
        if (!practicing) {
            HestiaCard {
                IconTile(lesson.symbol)
                Text(lesson.situation, style = MaterialTheme.typography.titleMedium)
                Text(lesson.explanation, style = MaterialTheme.typography.bodyLarge)
            }
            HestiaCard(color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(lesson.recommendation, style = MaterialTheme.typography.bodyLarge)
            }
            lesson.legalNote?.let { note ->
                TextButton({ showLaw = !showLaw }, Modifier.heightIn(min = 48.dp)) { Text(if (showLaw) "Ocultar detalle de la ley" else "Qué dice la Ley 20.009") }
                if (showLaw) Text(note, style = MaterialTheme.typography.bodyLarge)
            }
            PrimaryButton("Practicar", { practicing = true })
        } else {
            HestiaCard {
                Text(lesson.question, style = MaterialTheme.typography.titleLarge)
                SecondaryButton(lesson.correctAnswer, { answer = true })
                SecondaryButton(lesson.otherAnswer, { answer = false })
                answer?.let { correct ->
                    Text(if (correct) "Bien pensado." else "Hagamos una pausa.", style = MaterialTheme.typography.titleMedium)
                    Text(lesson.feedback, style = MaterialTheme.typography.bodyLarge)
                }
            }
            if (answer != null) PrimaryButton(if (saving) "Guardando…" else if (lesson.id < 5) "Siguiente lección" else "Terminar", onComplete, enabled = !saving)
        }
        Column {
            Text("Fuentes oficiales", style = MaterialTheme.typography.labelMedium)
            lesson.sources.forEach { source ->
                TextButton({ onSource(source) }, Modifier.heightIn(min = 48.dp)) { Text(source.title) }
            }
        }
    }
}
