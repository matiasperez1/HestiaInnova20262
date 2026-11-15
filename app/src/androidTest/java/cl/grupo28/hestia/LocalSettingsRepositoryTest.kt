package cl.grupo28.hestia

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import cl.grupo28.hestia.core.accessibility.*
import cl.grupo28.hestia.core.navigation.dialIntent
import cl.grupo28.hestia.data.DataStoreSettingsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class LocalSettingsRepositoryTest {
    @Test fun durableSetupContactsAppearanceAndProgress() = runBlocking {
        val file = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "repo-${UUID.randomUUID()}.preferences_pb")
        var job = SupervisorJob()
        fun repository() = DataStoreSettingsRepository(PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file }))
        var repo = repository()
        try {
            assertEquals(0, repo.settings.first().onboardingStep)
            repo.setOnboardingStep(3)
            job.cancelAndJoin()
            job = SupervisorJob(); repo = repository()
            assertEquals(3, repo.settings.first().onboardingStep)
            repo.addContact("Ana (prueba)", "+56900000001")
            repo.addContact("Luis (prueba)", "+56900000002")
            try { repo.addContact("Repetido", "+56 9 0000 0001"); fail("Must reject duplicate") } catch (_: IllegalArgumentException) { }
            repo.completeLesson(2)
            repo.setDisplay(DisplayPreferences(TextSize.VERY_LARGE, highContrast = true, reduceMotion = true))
            repo.setOnboardingStep(7)
            job.cancelAndJoin()
            job = SupervisorJob(); repo = repository()
            val saved = repo.settings.first()
            assertTrue(saved.onboardingComplete)
            assertEquals(2, saved.contacts.size)
            assertEquals(setOf(2), saved.completedLessons)
            assertEquals(TextSize.VERY_LARGE, saved.display.textSize)
            assertTrue(saved.display.highContrast)
            repo.removeContact(saved.contacts.first().id)
            assertEquals("Luis (prueba)", repo.settings.first().contacts.single().name)
            assertEquals(android.content.Intent.ACTION_DIAL, dialIntent(saved.contacts.last().phone).action)
            assertEquals("tel", dialIntent(saved.contacts.last().phone).data?.scheme)
            assertEquals("+56900000002", dialIntent(saved.contacts.last().phone).data?.schemeSpecificPart)
        } finally { job.cancelAndJoin(); file.delete() }
    }
}
