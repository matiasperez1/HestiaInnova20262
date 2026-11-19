package cl.grupo28.hestia.domain

object EducationIds {
    const val BANK_CALL = "llamada-banco"
    const val FAKE_EMAIL = "correo-falso"
    const val FAMILY_MESSAGE = "mensaje-familiar"
    const val LOAN_OFFER = "credito-inversion"
    const val UNKNOWN_PAYMENT = "operacion-desconocida"

    val legacyLessons = mapOf(1 to BANK_CALL, 2 to FAKE_EMAIL, 3 to FAMILY_MESSAGE, 4 to LOAN_OFFER, 5 to UNKNOWN_PAYMENT)
}

enum class LessonStep(val title: String) {
    SITUATION("Situación"), SIGNALS("¿En qué me fijo?"), ACTION("¿Y qué debería hacer?"),
    PRACTICE("Practiquemos"), PRACTICE_FOLLOW_UP("Practiquemos"), REMEMBER("Un consejo para recordar"),
}

data class LessonCheckpoint(
    val step: LessonStep = LessonStep.SITUATION,
    val answer: String? = null,
    val followUpAnswer: String? = null,
)

data class EducationProgress(
    val completed: Set<String> = emptySet(),
    val checkpoints: Map<String, LessonCheckpoint> = emptyMap(),
    val lastLessonId: String? = null,
)
