package cl.grupo28.hestia.domain

import cl.grupo28.hestia.core.accessibility.DisplayPreferences

data class EmergencyContact(val id: String, val name: String, val phone: String)

data class LocalSettings(
    val onboardingStep: Int = 0,
    val contacts: List<EmergencyContact> = emptyList(),
    val completedLessons: Set<Int> = emptySet(),
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
}
