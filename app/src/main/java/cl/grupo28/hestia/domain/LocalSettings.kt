package cl.grupo28.hestia.domain

import cl.grupo28.hestia.core.accessibility.DisplayPreferences

data class EmergencyContact(
    val id: String, val name: String, val phone: String,
    val relationship: String? = null, val primary: Boolean = false,
)

data class LocalSettings(
    val onboardingStep: Int = 0,
    val contacts: List<EmergencyContact> = emptyList(),
    val education: EducationProgress = EducationProgress(),
    val display: DisplayPreferences = DisplayPreferences(),
) {
    val onboardingComplete: Boolean get() = onboardingStep == 7
}

object ContactRules {
    fun name(value: String): String = value.trim().replace(Regex("\\s+"), " ").also {
        require(it.isNotEmpty() && it.length <= 60) { "Escribe un nombre de hasta 60 letras." }
    }
    fun phone(value: String): String = value.trim().replace(Regex("[ ()-]"), "").also {
        require(it.matches(Regex("\\+?[0-9]{7,15}"))) { "Escribe un teléfono válido, con código de país si corresponde." }
    }
    fun relationship(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }?.also {
        require(it.length <= 40) { "Escribe una relación de hasta 40 caracteres." }
    }
}
