package cl.grupo28.hestia.features.education

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cl.grupo28.hestia.core.components.*
import cl.grupo28.hestia.domain.EducationProgress
import cl.grupo28.hestia.domain.HelpRequest
import cl.grupo28.hestia.domain.LessonCheckpoint
import cl.grupo28.hestia.domain.LessonStep
import cl.grupo28.hestia.features.family.TrustHelpDialog

@Composable
internal fun EducationScreen(progress: EducationProgress, saving: Boolean, onModule: (String) -> Unit, onLesson: (String) -> Unit) {
    val modules = EducationContent.modules
    val finished = modules.count { module -> module.lessons.all { it.id in progress.completed } }
    val recent = progress.lastLessonId?.takeIf(progress.checkpoints::containsKey)?.let(EducationContent.lessonsById::get)

    Page("Aprender", "Aprende a reconocer una posible estafa y qué puedes hacer.") {
        SectionHeader("Tu progreso", "$finished de ${modules.size} temas completados")
        if (recent != null) {
            PrimaryButton("Continuar: ${recent.title}", { onLesson(recent.id) }, enabled = !saving)
        } else if (progress.completed.isEmpty() && progress.checkpoints.isEmpty()) {
            Text("Elige el tema que más te interese.", style = MaterialTheme.typography.bodyLarge)
        }
        modules.forEach { module ->
            val completed = module.lessons.count { it.id in progress.completed }
            val started = completed > 0 || module.lessons.any { it.id in progress.checkpoints }
            val action = when {
                completed == module.lessons.size -> "Repasar"
                started -> "Continuar"
                else -> "Comenzar"
            }
            HestiaCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconTile(module.icon)
                    SectionHeader(module.title)
                }
                Text(module.description, style = MaterialTheme.typography.bodyLarge)
                Text("$completed de ${module.lessons.size} lecciones completadas", style = MaterialTheme.typography.bodyMedium)
                SecondaryButton(action, { onModule(module.id) }, Modifier.semantics { contentDescription = "$action: ${module.title}" })
            }
        }
    }
}

@Composable
internal fun EducationModuleScreen(module: EducationModule, progress: EducationProgress, saving: Boolean, onLesson: (String) -> Unit) {
    Page(module.title, module.description) {
        module.lessons.forEach { lesson ->
            val inProgress = lesson.id in progress.checkpoints
            val completed = lesson.id in progress.completed
            val action = when {
                inProgress -> "Continuar lección"
                completed -> "Repetir lección"
                else -> "Abrir lección"
            }
            HestiaCard {
                SectionHeader(lesson.title, when {
                    completed && inProgress -> "Completada · repaso en curso"
                    completed -> "Completada"
                    inProgress -> "En curso"
                    else -> "Por comenzar"
                })
                SecondaryButton(action, { onLesson(lesson.id) }, Modifier.semantics { contentDescription = "$action: ${lesson.title}" }, enabled = !saving)
            }
        }
    }
}

@Composable
internal fun LessonScreen(
    module: EducationModule,
    lesson: EducationLesson,
    checkpoint: LessonCheckpoint,
    saving: Boolean,
    showDemoReport: Boolean,
    onProgress: (LessonCheckpoint) -> Unit,
    onComplete: (() -> Unit) -> Unit,
    onLeave: () -> Unit,
    onSource: (OfficialSource) -> Unit,
) {
    var finished by rememberSaveable(lesson.id) { mutableStateOf(false) }
    var help by remember { mutableStateOf(false) }
    val step = checkpoint.step

    if (finished) {
        Page("Terminaste esta lección", lesson.title) {
            HestiaCard(color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(lesson.takeaway, style = MaterialTheme.typography.bodyLarge)
            }
            PrimaryButton("Volver a la pantalla anterior", onLeave)
        }
        return
    }

    key(step) {
        Page(lesson.title, step.title, prominentSubtitle = true) {
            val progressLabel = when (step) {
                LessonStep.PRACTICE -> "Pregunta 1 de 2"
                LessonStep.PRACTICE_FOLLOW_UP -> "Pregunta 2 de 2"
                else -> "Paso ${step.ordinal + 1} de ${LessonStep.entries.size}"
            }
            Text(progressLabel, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyMedium)
            when (step) {
                LessonStep.SITUATION -> HestiaCard {
                    Text(lesson.situation, style = MaterialTheme.typography.bodyLarge)
                    Text("Puedes leer a tu ritmo. Al final hay dos preguntas para repasar.", style = MaterialTheme.typography.bodyLarge)
                }
                LessonStep.SIGNALS -> HestiaCard {
                    lesson.signals.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
                }
                LessonStep.ACTION -> {
                    HestiaCard(color = MaterialTheme.colorScheme.secondaryContainer) {
                        lesson.actions.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
                    }
                    if (module.id == EducationContent.RESPONSE_MODULE) {
                        SecondaryButton("Pedir ayuda a una persona de confianza", { help = true })
                    }
                    if (showDemoReport && lesson.id == EducationContent.REPORT_LESSON) {
                        Text("En DEMO LOCAL puedes reportar una llamada ficticia desde Teléfono, en Recientes → Ver opciones → Reportar número.",
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
                LessonStep.PRACTICE -> LessonPractice(lesson.exercise, checkpoint.answer, saving) { answer ->
                    onProgress(checkpoint.copy(answer = answer))
                }
                LessonStep.PRACTICE_FOLLOW_UP -> LessonPractice(lesson.followUpExercise, checkpoint.followUpAnswer, saving) { answer ->
                    onProgress(checkpoint.copy(followUpAnswer = answer))
                }
                LessonStep.REMEMBER -> HestiaCard(color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(lesson.takeaway, style = MaterialTheme.typography.bodyLarge)
                }
            }
            val canContinue = when (step) {
                LessonStep.PRACTICE -> lesson.exercise.evaluate(checkpoint.answer) != null
                LessonStep.PRACTICE_FOLLOW_UP -> lesson.followUpExercise.evaluate(checkpoint.followUpAnswer) != null
                else -> true
            }
            val action = when (step) {
                LessonStep.SITUATION -> "Comenzar lección"
                LessonStep.SIGNALS -> "Ver qué hacer"
                LessonStep.ACTION -> "Ir a las preguntas"
                LessonStep.PRACTICE -> "Siguiente pregunta"
                LessonStep.PRACTICE_FOLLOW_UP -> "Ver el consejo final"
                LessonStep.REMEMBER -> "Terminar lección"
            }
            PrimaryButton(action, {
                if (step == LessonStep.REMEMBER) onComplete { finished = true }
                else onProgress(checkpoint.copy(step = LessonStep.entries[step.ordinal + 1]))
            }, enabled = !saving && canContinue)
            if (step != LessonStep.SITUATION) {
                SecondaryButton("Volver al paso anterior", { onProgress(checkpoint.copy(step = LessonStep.entries[step.ordinal - 1])) }, enabled = !saving)
            }
            if (step == LessonStep.REMEMBER) LessonSources(lesson, onSource)
        }
    }
    if (help) TrustHelpDialog(HelpRequest(), { help = false })
}

@Composable
private fun LessonPractice(exercise: EducationExercise, answer: String?, saving: Boolean, onAnswer: (String?) -> Unit) {
    val feedback = exercise.evaluate(answer)
    Text(exercise.question, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        exercise.options.forEach { option ->
            val selected = answer == option.id
            Surface(
                modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
                    .selectable(selected, enabled = !saving && feedback == null, role = Role.RadioButton, onClick = { onAnswer(option.id) }),
                color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
            ) {
                Row(Modifier.heightIn(min = 56.dp).padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    RadioButton(selected, onClick = null)
                    Text(option.label, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
    if (feedback != null) {
        HestiaCard(Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            color = if (feedback) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.tertiaryContainer) {
            SectionHeader(if (feedback) "Es una opción segura" else "Hay una opción más segura")
            Text(exercise.explanation, style = MaterialTheme.typography.bodyLarge)
        }
        SecondaryButton("Intentar de nuevo", { onAnswer(null) }, enabled = !saving)
    }
}

@Composable
private fun LessonSources(lesson: EducationLesson, onSource: (OfficialSource) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    TextButton({ expanded = !expanded }, Modifier.heightIn(min = 48.dp)) {
        Text(if (expanded) "Ocultar fuentes" else "Fuente de esta recomendación")
    }
    if (expanded) {
        lesson.legalNote?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
        Text("Revisión del contenido: ${EducationContent.REVIEWED_ON}", style = MaterialTheme.typography.bodyMedium)
        lesson.sources.forEach { source ->
            TextButton({ onSource(source) }, Modifier.heightIn(min = 48.dp)) { Text(source.title) }
        }
    }
}
