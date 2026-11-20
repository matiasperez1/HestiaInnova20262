package cl.grupo28.hestia

import cl.grupo28.hestia.data.PersonalReportChanged
import cl.grupo28.hestia.data.PersonalReportState
import cl.grupo28.hestia.data.PersonalReportStore
import cl.grupo28.hestia.data.PersonalReports
import cl.grupo28.hestia.domain.CallSignals
import cl.grupo28.hestia.domain.CallVerdict
import cl.grupo28.hestia.domain.PersonalReportReason
import cl.grupo28.hestia.domain.normalizePersonalNumber
import cl.grupo28.hestia.services.afterAllowingCall
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class PersonalReportsTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun normalizesNationalAndInternationalNumbersThroughTheSelectedRegion() {
        assertEquals(chile, normalize("9 1234-5678", "CL"))
        assertEquals(chile, normalize("(+56) 9 1234-5678", "CL"))
        assertEquals(chile, normalize("+56 (9) 1234 5678", "US"))
        assertEquals(uk, normalize("00 44 (20) 7946-0958", "CL"))
        assertEquals(uk, normalize("020 7946 0958", "GB"))
        assertNull(normalize("020 7946 0958", "CL"))
        assertNull(normalize("Número privado", "CL"))
        assertNull(normalize("+", "CL"))
        assertNull(normalize("+56 9 1234 5678;123", "CL"))
        assertNull(normalize("112", "CL"))
    }

    @Test fun savesEditsAndWithdrawsAcrossRepositoryRestarts() = runTest {
        val file = folder.newFile()
        val store = PersonalReportStore({ file.readBytes().takeIf { it.isNotEmpty() } }, file::writeBytes)
        val dispatcher = StandardTestDispatcher(testScheduler)
        var time = Instant.EPOCH
        fun reopen() = PersonalReports(store, { normalize(it, "CL") }, { time }, dispatcher)
        val first = reopen().save("9 1234-5678", PersonalReportReason.IMPERSONATION, " Apunte privado ")
        val second = reopen()
        assertEquals(first, second.active(chile))
        assertEquals("Apunte privado", first.description)
        time = time.plusSeconds(1)
        val edit = second.save(chile, PersonalReportReason.REQUEST_FOR_DATA, null, first.id)
        assertEquals(first.id, edit.id)
        assertEquals(first.originalNumber, edit.originalNumber)
        assertEquals(first.createdAt, edit.createdAt)
        assertEquals(time, edit.modifiedAt)
        time = time.plusSeconds(1)
        second.withdraw(edit)
        val third = reopen()
        assertNull(third.active(chile))
        val withdrawn = (third.state.value as PersonalReportState.Ready).reports.getValue(chile)
        assertFalse(withdrawn.active)
        assertEquals(time, withdrawn.modifiedAt)
        assertEquals(first.id, third.save(chile, PersonalReportReason.OTHER, null).id)
        assertTrue(reopen().active(chile)!!.active)
    }

    @Test fun aliasesAndConcurrentSavesCannotCreateDuplicateActiveReports() = runTest {
        var content: ByteArray? = null
        val store = PersonalReportStore({ content }, { content = it })
        val repository = PersonalReports(store, { normalize(it, "CL") }, io = StandardTestDispatcher(testScheduler))
        val results = listOf(chile, "9 1234-5678").map { input ->
            async { runCatching { repository.save(input, PersonalReportReason.OTHER, null) } }
        }.map { it.await() }
        assertEquals(1, results.count { it.isSuccess })
        assertEquals(1, results.count { it.exceptionOrNull() is PersonalReportChanged })
        assertEquals(1, store.load().size)
    }

    @Test fun failingReadOrWriteDoesNotTurnIntoAnEmptySuccessfulStore() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var wrote = false
        val unreadable = PersonalReports(PersonalReportStore({ "invalid".toByteArray() }, { wrote = true }),
            { normalize(it, "CL") }, io = dispatcher)
        assertTrue(runCatching { unreadable.save(chile, PersonalReportReason.OTHER, null) }.exceptionOrNull() is IOException)
        assertEquals(PersonalReportState.Unavailable, unreadable.state.value)
        assertFalse(wrote)
        val failedWrite = PersonalReports(PersonalReportStore({ null }, { throw IOException() }), { normalize(it, "CL") }, io = dispatcher)
        assertTrue(runCatching { failedWrite.save(chile, PersonalReportReason.OTHER, null) }.exceptionOrNull() is IOException)
        assertNull(failedWrite.active(chile))
    }

    @Test fun returnsTheTelecomDecisionBeforeLocalIoAndKeepsTheTwoSignalsIndependent() = runTest {
        val pendingIo = CompletableDeferred<Unit>()
        var allowed = false
        var warning: CallSignals? = null
        this.afterAllowingCall(allow = { allowed = true }) {
            pendingIo.await()
            warning = CallSignals(PersonalReportReason.IMPERSONATION, CallVerdict.REPORTED)
        }
        assertTrue(allowed)
        runCurrent()
        assertNull(warning)
        pendingIo.complete(Unit)
        runCurrent()
        assertEquals(PersonalReportReason.IMPERSONATION, warning!!.personal)
        assertEquals(CallVerdict.REPORTED, warning!!.community)
        assertTrue(warning!!.shouldWarn)
        assertTrue(CallSignals(null, CallVerdict.DEBUG_REPORTED).shouldWarn)
        assertTrue(CallSignals(PersonalReportReason.OTHER, CallVerdict.NO_MATCH).shouldWarn)
        assertFalse(CallSignals(null, CallVerdict.NO_MATCH).shouldWarn)
        assertFalse(CallSignals(null, CallVerdict.DEBUG_PENDING).shouldWarn)
    }

    private fun normalize(value: String, region: String) = normalizePersonalNumber(value, region) { candidate, country ->
        when {
            candidate == chile || candidate == uk -> candidate
            candidate == "912345678" && country == "CL" -> chile
            candidate == "02079460958" && country == "GB" -> uk
            else -> null
        }
    }

    private companion object {
        const val chile = "+56912345678"
        const val uk = "+442079460958"
    }
}
