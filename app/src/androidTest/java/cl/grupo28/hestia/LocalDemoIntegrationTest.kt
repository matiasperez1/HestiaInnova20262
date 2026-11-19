package cl.grupo28.hestia

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import cl.grupo28.hestia.features.automatic.AutomaticEnvironmentFactory
import cl.grupo28.hestia.domain.auth.AuthState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import cl.grupo28.hestia.data.automatic.LocalDemoGateway
import org.junit.*
import org.junit.Assert.*
import java.io.File

/** Opt in only against the developer's LOCAL_DEMO backend on the QA emulator. */
class LocalDemoIntegrationTest {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 private fun waitText(text:String){compose.waitUntil(20000){compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}}
 private fun tap(text:String){waitText(text);compose.waitUntil(15000){compose.onAllNodes(hasText(text) and isEnabled()).fetchSemanticsNodes().isNotEmpty()};val node=compose.onNodeWithText(text);if(runCatching{node.assertIsDisplayed()}.isFailure)node.performScrollTo();node.performClick()}
 private fun capture(name: String) {
  if (InstrumentationRegistry.getArguments().getString("textOnly") == "true") return
  compose.mainClock.advanceTimeBy(400)
  compose.waitForIdle()
  android.os.SystemClock.sleep(400)
  val instrumentation = InstrumentationRegistry.getInstrumentation()
  val image = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
  val file = File(compose.activity.getExternalFilesDir(null), "demo-$name.png")
  file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
 }
 @Test fun localEndToEndSimulationDuplicateAlertAndSummary(){
  Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("localDemoIntegration")=="true")
  if(android.os.Build.VERSION.SDK_INT>=37)InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(compose.activity.packageName,"android.permission.ACCESS_LOCAL_NETWORK")
  val controls=AutomaticEnvironmentFactory.create(compose.activity,MutableStateFlow(AuthState(false))).demo!!
  compose.runOnUiThread{controls.select(true,"bob")}
  try{
   compose.waitUntil(20000){compose.onAllNodesWithText("Bienvenido a Hestia").fetchSemanticsNodes().isNotEmpty()||compose.onAllNodesWithTag("tab_summary").fetchSemanticsNodes().isNotEmpty()}
   if(compose.onAllNodesWithText("Bienvenido a Hestia").fetchSemanticsNodes().isNotEmpty())listOf("Comenzar","Continuar sin cuenta","Continuar sin Gmail","Continuar sin activar","Continuar sin agenda","Continuar sin avisos","Entrar a Hestia").forEach(::tap)
   waitText("Tu semana");tap("Revisar correos");waitText("Conectada al computador")
   compose.onNodeWithText("Limpiar datos demo").assertDoesNotExist()
   tap("Herramientas de demostración")
   tap("Limpiar datos demo");tap("Limpiar simulaciones");waitText("Correos simulados analizados: 0")
   runBlocking { controls.emit(LocalDemoGateway.owner("bob"),"high-risk",false) };waitText("Correos simulados analizados: 1");waitText("Avisos simulados generados: 1")
   runBlocking { controls.repeat(LocalDemoGateway.owner("bob")) };waitText("Reintentos: 0 · duplicados: 1");compose.onNodeWithText("Correos simulados analizados: 1").assertExists();compose.onNodeWithText("Backend: conectado").performScrollTo();capture("proteccion")
   compose.onNode(hasText("Ver aviso simulado",substring=true)).performScrollTo().performClick();waitText("Señales de riesgo alto");compose.onNode(hasText("DEMO LOCAL · correo sintético") and hasAnyAncestor(isDialog())).assertExists();capture("alerta")
   tap("Aprender a reconocer correos falsos");waitText("Correos electrónicos falsos")
   compose.onNodeWithText("Volver").performClick()
   compose.waitUntil(15000){compose.onAllNodes(hasText("DEMO LOCAL · correo sintético") and hasAnyAncestor(isDialog())).fetchSemanticsNodes().isNotEmpty()}
   tap("Pedir ayuda con este correo")
   compose.onNodeWithText("Cerrar").assertExists()
   tap("Cerrar")
   compose.onNode(hasText("DEMO LOCAL · correo sintético") and hasAnyAncestor(isDialog())).assertExists()
   tap("Entendido")
   compose.onNodeWithTag("tab_summary").performClick();waitText("Tu semana");compose.onNodeWithContentDescription("Analizados: 1").assertExists()
   compose.onNodeWithTag("tab_phone").assertExists();compose.onNodeWithText("Llamadas").assertExists();capture("resumen")
   tap("Revisar último aviso simulado")
   compose.waitUntil(15000){compose.onAllNodes(hasText("DEMO LOCAL · correo sintético") and hasAnyAncestor(isDialog())).fetchSemanticsNodes().isNotEmpty()}
   tap("Entendido")
   compose.onNodeWithText("Volver").performClick()
   waitText("Tu semana")
   compose.onNodeWithTag("tab_education").performClick();compose.onNodeWithTag("tab_education").assertIsSelected();compose.onNodeWithTag("tab_family").performClick();compose.onNodeWithTag("tab_family").assertIsSelected()
  }finally{compose.runOnUiThread{controls.select(false,"alice")}}
 }
}
