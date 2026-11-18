package cl.grupo28.hestia

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import cl.grupo28.hestia.core.theme.HestiaTheme
import cl.grupo28.hestia.data.auth.KeystoreSessionStore
import cl.grupo28.hestia.data.automatic.AutomaticApi
import cl.grupo28.hestia.domain.WeekRange
import cl.grupo28.hestia.domain.auth.AuthState
import cl.grupo28.hestia.domain.automatic.*
import cl.grupo28.hestia.features.automatic.*
import cl.grupo28.hestia.features.gmail.GmailUiState
import cl.grupo28.hestia.features.summary.SummaryScreen
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import java.io.File

class AutomaticUiTest {
 @get:Rule val compose=createComposeRule()
 @Test fun unavailableBackendNeverAppearsActiveOrRequestsPermission(){
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val model=AutomaticViewModel(AutomaticApi(KeystoreSessionStore(context),base=""),MutableStateFlow(AuthState(false)))
  compose.setContent{HestiaTheme{Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())){AutomaticPanel(model,{_,_->error("Must not request authorization")},{_,_->},null)}}}
  compose.onNodeWithText("Activar revisión automática").assertIsNotEnabled()
  compose.onNodeWithText("Sin conexión automática verificada").assertIsDisplayed()
  compose.onNodeWithText("Gmail activo").assertDoesNotExist()
  compose.onRoot().captureToImage().asAndroidBitmap().let{bitmap->File(context.getExternalFilesDir(null),"automatic-panel-qa.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}}
 }
 @Test fun automaticSummaryUsesOneSourceAndKeepsCallsUnknown(){
  val week=WeekRange.current();val start=week.start.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toString()
  val automatic=AutomaticUiState(summary=AutomaticSummary(3,1,start,week.end.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toString()))
  compose.setContent{HestiaTheme{SummaryScreen(week,null,GmailUiState(),automatic,{})}}
  compose.onNodeWithContentDescription("Analizados: 3").assertExists()
  compose.onNodeWithContentDescription("Sospechosos: 1").assertExists()
  compose.onNodeWithContentDescription("Registradas por Hestia: Sin datos").assertExists()
  compose.onNodeWithText("Revisión automática de correos").assertDoesNotExist()
 }
}
