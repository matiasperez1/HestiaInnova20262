package cl.grupo28.hestia

import cl.grupo28.hestia.domain.EducationIds
import cl.grupo28.hestia.features.education.EducationContent
import cl.grupo28.hestia.features.education.ExerciseKind
import java.net.URI
import org.junit.Assert.*
import org.junit.Test

class EducationContentTest {
    @Test fun sixModulesEachHaveTwoLessonsWithStableIdsAndSources() {
        val modules = EducationContent.modules
        val lessons = modules.flatMap { it.lessons }
        assertEquals(6, modules.size)
        assertEquals(6, modules.map { it.id }.toSet().size)
        assertTrue(modules.all { it.lessons.size == 2 })
        assertEquals(12, lessons.map { it.id }.toSet().size)
        assertTrue(EducationContent.lessonsById.keys.containsAll(EducationIds.legacyLessons.values))
        assertTrue(lessons.all { it.situation.isNotBlank() && it.signals.size in 2..3 && it.actions.isNotEmpty() && it.sources.isNotEmpty() })
    }

    @Test fun allExercisesHaveExactlyOneSafeAnswerAndAllowRetry() {
        val exercises = EducationContent.lessonsById.values.flatMap { listOf(it.exercise, it.followUpExercise) }
        assertEquals(24, exercises.size)
        assertEquals(exercises.size, exercises.map { it.question }.toSet().size)
        assertEquals(ExerciseKind.entries.toSet(), exercises.map { it.kind }.toSet())
        for (exercise in exercises) {
            assertTrue(exercise.options.size in 2..3)
            assertEquals(exercise.options.size, exercise.options.map { it.id }.toSet().size)
            assertEquals(1, exercise.options.count { exercise.evaluate(it.id) == true })
            assertTrue(exercise.options.any { exercise.evaluate(it.id) == false })
            assertNotNull(exercise.explanation.takeIf(String::isNotBlank))
            assertNull(exercise.evaluate(null))
            assertNull(exercise.evaluate("opción retirada"))
        }
    }

    @Test fun onlySourcesContainLinksAndUseOfficialHttpsHosts() {
        val hosts = setOf("www.sernac.cl", "www.cmfchile.cl", "www.chileatiende.gob.cl", "csirt.gob.cl", "www.bcn.cl")
        for (lesson in EducationContent.lessonsById.values) {
            val exercisesText = listOf(lesson.exercise, lesson.followUpExercise).flatMap { exercise ->
                listOf(exercise.question, exercise.explanation) + exercise.options.map { it.label }
            }
            val scenarioText = listOf(lesson.situation, lesson.takeaway) + lesson.signals + lesson.actions + exercisesText
            assertTrue(scenarioText.none { it.contains(Regex("https?://|www\\.", RegexOption.IGNORE_CASE)) })
            lesson.sources.forEach { source ->
                val url = URI(source.url)
                assertEquals("https", url.scheme)
                assertTrue(url.host in hosts)
                assertNull(url.userInfo)
            }
        }
    }
}
