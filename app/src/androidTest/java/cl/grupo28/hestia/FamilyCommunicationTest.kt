package cl.grupo28.hestia

import android.content.ActivityNotFoundException
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cl.grupo28.hestia.features.family.FamilyAndroidActions
import cl.grupo28.hestia.features.family.HelpChannel
import cl.grupo28.hestia.features.family.helpIntent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FamilyCommunicationTest {
    @Test fun composesSmsAndSharesPlainTextWithoutAnImplicitRecipient() {
        val syntheticPhone = "+12025550196"
        val sms = helpIntent(HelpChannel.SMS, syntheticPhone, "Solicitud de prueba")
        assertEquals(Intent.ACTION_SENDTO, sms.action)
        assertEquals("smsto", sms.data?.scheme)
        assertEquals(syntheticPhone, sms.data?.schemeSpecificPart)
        assertEquals("Solicitud de prueba", sms.getStringExtra("sms_body"))
        val share = helpIntent(HelpChannel.SHARE, syntheticPhone, "Advertencia de prueba")
        assertEquals(Intent.ACTION_CHOOSER, share.action)
        @Suppress("DEPRECATION")
        val send = share.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("Advertencia de prueba", send.getStringExtra(Intent.EXTRA_TEXT))
        assertNull(send.data)
        assertNull(send.`package`)
        assertNull(send.component)
        assertEquals(Intent.ACTION_DIAL, helpIntent(HelpChannel.CALL, syntheticPhone, "").action)
    }

    @Test fun providesAnAlternativeWhenAnSmsAppCannotBeOpened() {
        val context = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
            override fun startActivity(intent: Intent) { throw ActivityNotFoundException() }
        }
        assertEquals("No se pudo abrir Mensajes. Puedes elegir Compartir advertencia o llamar.",
            FamilyAndroidActions(context).open(HelpChannel.SMS, "2025550196", "Prueba"))
    }
}
