package cl.grupo28.hestia

import android.Manifest
import android.content.ContentProviderOperation
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.Photo
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import cl.grupo28.hestia.core.theme.HestiaTheme
import cl.grupo28.hestia.data.DataStoreSettingsRepository
import cl.grupo28.hestia.data.contacts.AgendaContact
import cl.grupo28.hestia.data.contacts.AgendaNumber
import cl.grupo28.hestia.data.contacts.DeviceAgenda
import cl.grupo28.hestia.features.calls.dialer.DialAttempt
import cl.grupo28.hestia.features.contacts.*
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class AgendaModuleTest {
    @get:Rule val compose = createComposeRule()
    private val contact = AgendaContact(1, "Ángela Prueba", null, listOf(
        AgendaNumber("202 555 0147", "2025550147"), AgendaNumber("202 555 0156", "2025550156"),
    ))

    @Test fun permissionTransitionsKeepTheDirectoryPrivateAndExposeRecovery() {
        val permission = mutableStateOf(agendaPermission(false, false, false))
        var requests = 0
        var settings = 0
        compose.setContent { HestiaTheme {
            AgendaDirectory(AgendaAccess(permission.value, AgendaResult.Loaded(listOf(contact)), { requests++ }, { settings++ }, {}), AgendaUse.Call)
        } }
        compose.onNodeWithText("Permitir acceso a mis contactos").performClick()
        compose.runOnIdle { assertEquals(1, requests); permission.value = agendaPermission(false, true, true) }
        compose.onNodeWithText("Puedes permitir la agenda ahora o seguir usando el teclado manual.").assertExists()
        compose.onNodeWithText("Ángela Prueba").assertDoesNotExist()
        compose.onNodeWithText("Permitir acceso a mis contactos").performClick()
        compose.runOnIdle { assertEquals(2, requests); permission.value = agendaPermission(true, false, true) }
        compose.onNodeWithText("Ángela Prueba").performClick()
        compose.onNodeWithText("202 555 0156").assertExists()
        compose.runOnIdle { permission.value = agendaPermission(false, false, true) }
        compose.onNodeWithText("Ángela Prueba").assertDoesNotExist()
        compose.onNodeWithText("202 555 0156").assertDoesNotExist()
        compose.onNodeWithText("Abrir Ajustes de Android").performClick()
        compose.runOnIdle { assertEquals(1, settings); permission.value = AgendaPermission.Granted }
        compose.onNodeWithText("Ángela Prueba").assertExists()
    }

    @Test fun grantedEmptyAgendaShowsHelp() {
        compose.setContent { HestiaTheme { AgendaDirectory(granted(emptyList()), AgendaUse.Call) } }
        compose.onNodeWithText("Tu agenda no tiene contactos con teléfono").assertExists()
        compose.onNodeWithText("Permitir acceso a mis contactos").assertDoesNotExist()
    }

    @Test fun searchesByAccentInsensitiveNameAndAnyNumberAndReturnsTheChosenNumber() {
        var chosen: String? = null
        val other = AgendaContact(2, "Otro contacto", null, listOf(AgendaNumber("202 555 0199", "2025550199")))
        compose.setContent { HestiaTheme { AgendaDirectory(granted(listOf(contact, other)), AgendaUse.PickNumber,
            onChooseNumber = { chosen = it }) } }
        val search = compose.onNodeWithText("Buscar por nombre o número")
        search.performTextInput("angela")
        compose.onNodeWithText("Ángela Prueba").assertExists()
        compose.onNodeWithText("Otro contacto").assertDoesNotExist()
        search.performTextReplacement("0156")
        compose.onNodeWithText("Ángela Prueba").performClick()
        compose.onNodeWithText("Usar 202 555 0156").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("2025550156", chosen) }
    }

    @Test fun explicitlyCallingTheSecondNumberDispatchesExactlyThatNumber() {
        val dialed = mutableListOf<String>()
        var closed = false
        compose.setContent { HestiaTheme {
            AgendaContactDialog(contact, AgendaUse.Call, onClose = { closed = true }, onDial = {
                dialed.add(it)
                DialAttempt.Started
            })
        } }
        compose.onNodeWithText("Elige un número.").assertExists()
        compose.runOnIdle { assertTrue(dialed.isEmpty()) }
        compose.onNodeWithContentDescription("Llamar a 202 555 0156").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("2025550156"), dialed); assertTrue(closed) }
    }

    @Test fun androidProviderGroupsAndDeduplicatesNumbers() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue(InstrumentationRegistry.getArguments().getString("isolatedAgendaProvider") == "true")
        assumeTrue(android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.MODEL.contains("sdk_gphone"))
        assertEquals(PackageManager.PERMISSION_GRANTED, context.checkSelfPermission(Manifest.permission.READ_CONTACTS))
        val resolver = context.contentResolver
        val name = "Agenda instrumentation ${System.nanoTime()}"
        val photo = ByteArrayOutputStream().use { stream ->
            val bitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
            try { bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream); stream.toByteArray() }
            finally { bitmap.recycle() }
        }
        val operations = arrayListOf(
            ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null).build(),
            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
                .withValue(StructuredName.DISPLAY_NAME, name).build(),
            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, Photo.CONTENT_ITEM_TYPE)
                .withValue(Photo.PHOTO, photo).build(),
        )
        listOf("(202) 555-0147", "2025550147", "2025550156").forEach { number ->
            operations.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
                .withValue(Phone.NUMBER, number).build())
        }
        val automation = instrumentation.uiAutomation
        automation.adoptShellPermissionIdentity(Manifest.permission.WRITE_CONTACTS)
        val rawContact = try { resolver.applyBatch(ContactsContract.AUTHORITY, operations).first().uri!! }
        finally { automation.dropShellPermissionIdentity() }
        try {
            val loaded = withTimeout(8_000) { DeviceAgenda(resolver).observe().first() }
            val actual = loaded.single { it.name == name }
            assertEquals(setOf("2025550147", "2025550156"), actual.numbers.map { it.dial }.toSet())
            assertNotNull(actual.thumbnail)
        } finally {
            automation.adoptShellPermissionIdentity(Manifest.permission.WRITE_CONTACTS)
            try { resolver.delete(rawContact, null, null) }
            finally { automation.dropShellPermissionIdentity() }
        }
    }

    @Test fun importingSavesOnlyTheExplicitlyChosenNumberInFamilia() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("isolatedAgendaProvider") == "true")
        assumeTrue(android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.MODEL.contains("sdk_gphone"))
        val repository = DataStoreSettingsRepository(InstrumentationRegistry.getInstrumentation().targetContext)
        val before = runBlocking { repository.settings.first().contacts }
        val phones = (100..199).map { "2025550$it" }.filter { number -> before.none { it.phone == number } }.take(2)
        check(phones.size == 2)
        val entry = contact.copy(name = "Agenda QA ${System.nanoTime()}", numbers = phones.map { AgendaNumber(it, it) })
        try {
            compose.setContent { HestiaTheme { AgendaContactDialog(entry, AgendaUse.Import, onClose = {}) } }
            compose.onNodeWithContentDescription("Agregar ${phones.last()} a Familia").performScrollTo().performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithText("Guardado en Familia").fetchSemanticsNodes().isNotEmpty() }
            val after = runBlocking { repository.settings.first().contacts }
            val selected = after.single { it.name == entry.name }
            assertEquals(phones.last(), selected.phone)
            assertEquals(before, after.filterNot { it.id == selected.id })
        } finally {
            runBlocking { repository.settings.first().contacts.filter { it.name == entry.name }.forEach { repository.removeContact(it.id) } }
        }
    }

    private fun granted(contacts: List<AgendaContact>) = AgendaAccess(AgendaPermission.Granted, AgendaResult.Loaded(contacts), {}, {}, {})
}
