package cl.grupo28.hestia.features.onboarding

import androidx.compose.runtime.Composable

internal const val agendaOnboardingDescription = "Puedes incluir llamadas de números guardados desde Ajustes si activas la identificación de llamadas."
internal const val agendaOnboardingTitle = "Llamadas de tus contactos"

@Composable
internal fun AgendaOnboardingContent(continueButton: @Composable (String) -> Unit) = continueButton("Continuar sin agenda")
