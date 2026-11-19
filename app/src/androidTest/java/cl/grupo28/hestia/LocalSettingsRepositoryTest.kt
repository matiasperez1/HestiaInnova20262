package cl.grupo28.hestia

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import cl.grupo28.hestia.core.accessibility.*
import cl.grupo28.hestia.core.navigation.dialIntent
import cl.grupo28.hestia.data.DataStoreSettingsRepository
import cl.grupo28.hestia.domain.EducationIds
import cl.grupo28.hestia.domain.LessonCheckpoint
import cl.grupo28.hestia.domain.LessonStep
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
        fun openStore() = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file })
        var store = openStore()
        var repo = DataStoreSettingsRepository(store)
        try {
            assertEquals(0, repo.settings.first().onboardingStep)
            repo.setOnboardingStep(3)
            job.cancelAndJoin()
            job = SupervisorJob(); store = openStore(); repo = DataStoreSettingsRepository(store)
            assertEquals(3, repo.settings.first().onboardingStep)
            repo.addContact("Ana (prueba)", "+56900000001")
            repo.addContact("Luis (prueba)", "+56900000002")
            try { repo.addContact("Repetido", "+56 9 0000 0001"); fail("Must reject duplicate") } catch (_: IllegalArgumentException) { }
            val original = repo.settings.first().contacts
            val ana = original.first()
            val luis = original.last()
            repo.moveContactUp(luis.id)
            assertEquals(luis.id, repo.settings.first().contacts.first().id)
            repo.setPrimaryContact(luis.id)
            repo.setPrimaryContact(ana.id)
            repo.editContact(ana.id, "Ana de prueba", "+56900000003", "Hija")
            try { repo.editContact(ana.id, "Ana", "900000002", null); fail("Must reject duplicate edit") }
            catch (_: IllegalArgumentException) { }
            val oldKey = stringSetPreferencesKey("completed_lessons_v2")
            val checkpointsKey = stringPreferencesKey("education_checkpoints")
            store.edit {
                it[oldKey] = setOf("2", "4")
                it[checkpointsKey] = """{"llamada-banco":{"step":"PRACTICE","answer":"dictar"}}"""
            }
            repo.openLesson(EducationIds.BANK_CALL)
            assertEquals(LessonCheckpoint(LessonStep.PRACTICE, "dictar"),
                repo.settings.first().education.checkpoints[EducationIds.BANK_CALL])
            val checkpoint = LessonCheckpoint(LessonStep.PRACTICE_FOLLOW_UP, "dictar", "clave")
            repo.saveLessonCheckpoint(EducationIds.BANK_CALL, checkpoint)
            repo.openLesson("mensaje-encomienda")
            repo.saveLessonCheckpoint("mensaje-encomienda", LessonCheckpoint(LessonStep.SIGNALS))
            repo.openLesson(EducationIds.BANK_CALL)
            repo.setDisplay(DisplayPreferences(TextSize.VERY_LARGE, highContrast = true, reduceMotion = true))
            repo.setOnboardingStep(7)
            job.cancelAndJoin()
            job = SupervisorJob(); store = openStore(); repo = DataStoreSettingsRepository(store)
            val saved = repo.settings.first()
            assertTrue(saved.onboardingComplete)
            assertEquals(2, saved.contacts.size)
            assertEquals(ana.id, saved.contacts.first().id)
            assertEquals("Ana de prueba", saved.contacts.first().name)
            assertEquals("Hija", saved.contacts.first().relationship)
            assertEquals(ana.id, saved.contacts.single { it.primary }.id)
            assertEquals(setOf(EducationIds.FAKE_EMAIL, EducationIds.LOAN_OFFER), saved.education.completed)
            assertEquals(EducationIds.BANK_CALL, saved.education.lastLessonId)
            assertEquals(checkpoint, saved.education.checkpoints[EducationIds.BANK_CALL])
            repo.completeLesson(EducationIds.BANK_CALL)
            repo.completeLesson(EducationIds.BANK_CALL)
            val finished = repo.settings.first().education
            assertEquals(3, finished.completed.size)
            assertNull(finished.checkpoints[EducationIds.BANK_CALL])
            assertEquals(LessonStep.SIGNALS, finished.checkpoints.getValue("mensaje-encomienda").step)
            assertEquals(setOf("2", "4"), store.data.first()[oldKey])
            assertEquals(TextSize.VERY_LARGE, saved.display.textSize)
            assertTrue(saved.display.highContrast)
            repo.removeContact(saved.contacts.first().id)
            assertEquals("Luis (prueba)", repo.settings.first().contacts.single().name)
            assertFalse(repo.settings.first().contacts.single().primary)
            assertEquals(android.content.Intent.ACTION_DIAL, dialIntent(saved.contacts.last().phone).action)
            assertEquals("tel", dialIntent(saved.contacts.last().phone).data?.scheme)
            assertEquals("+56900000002", dialIntent(saved.contacts.last().phone).data?.schemeSpecificPart)
        } finally { job.cancelAndJoin(); file.delete() }
    }
}
