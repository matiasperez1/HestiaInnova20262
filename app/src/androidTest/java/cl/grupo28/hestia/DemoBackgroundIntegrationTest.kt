package cl.grupo28.hestia

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import cl.grupo28.hestia.data.automatic.LocalDemoGateway
import cl.grupo28.hestia.domain.auth.AuthState
import cl.grupo28.hestia.features.automatic.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

/** Only on an isolated QA emulator, with the synthetic local backend running. */
class DemoBackgroundIntegrationTest {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 private fun waitText(text:String)=compose.waitUntil(30000){compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}
 private fun tap(text:String){waitText(text);val n=compose.onNodeWithText(text);if(runCatching{n.assertIsDisplayed()}.isFailure)n.performScrollTo();n.performClick()}
 @Test fun highNotificationSurvivesBackgroundAndOpensCorrectDetail() {
  Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("localDemoIntegration")=="true")
  val instrumentation=InstrumentationRegistry.getInstrumentation();val context=compose.activity
  if(android.os.Build.VERSION.SDK_INT>=33)instrumentation.uiAutomation.grantRuntimePermission(context.packageName,Manifest.permission.POST_NOTIFICATIONS)
  if(android.os.Build.VERSION.SDK_INT>=37)instrumentation.uiAutomation.grantRuntimePermission(context.packageName,"android.permission.ACCESS_LOCAL_NETWORK")
  val controls=AutomaticEnvironmentFactory.create(context,MutableStateFlow(AuthState(false))).demo!!
  compose.runOnUiThread { controls.select(true,"bob") }
  try {
   compose.waitUntil(30000){compose.onAllNodesWithText("Bienvenido a Hestia").fetchSemanticsNodes().isNotEmpty()||compose.onAllNodesWithTag("tab_summary").fetchSemanticsNodes().isNotEmpty()}
   if(compose.onAllNodesWithText("Bienvenido a Hestia").fetchSemanticsNodes().isNotEmpty()) listOf("Comenzar","Continuar sin cuenta","Continuar sin Gmail","Continuar sin activar","Continuar sin agenda","Continuar sin avisos","Entrar a Hestia").forEach(::tap)
   tap("Revisar correos");waitText("Backend: conectado");tap("Activar avisos en segundo plano");waitText("Activos")
   val manager=context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
   compose.waitUntil(15000) { manager.activeNotifications.any { it.id==LocalDemoBackgroundService.STATUS_ID } }
   val previous=manager.activeNotifications.mapNotNull{it.tag}.toSet()
   context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
   instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_SLEEP").close()
   val power=context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
   compose.waitUntil(10000){!power.isInteractive}
   runBlocking { controls.emit(LocalDemoGateway.owner("bob"),"high-risk",false) }
   compose.waitUntil(30000){manager.activeNotifications.any{it.tag?.startsWith("demo:bob:")==true && it.tag !in previous}}
   val notification=manager.activeNotifications.first{it.tag?.startsWith("demo:bob:")==true && it.tag !in previous}
   val tag=notification.tag
   instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_WAKEUP").close()
   instrumentation.uiAutomation.executeShellCommand("wm dismiss-keyguard").close()
   notification.notification.contentIntent.send()
   compose.waitUntil(30000){compose.onAllNodes(hasText("DEMO LOCAL · correo sintético") and hasAnyAncestor(isDialog())).fetchSemanticsNodes().isNotEmpty()}
   runBlocking {
    controls.repeat(LocalDemoGateway.owner("bob"))
    controls.emit(LocalDemoGateway.owner("bob"),"urgent-payment",false)
    controls.emit(LocalDemoGateway.owner("bob"),"legitimate",false)
   }
   Thread.sleep(9000)
   assertEquals(1,manager.activeNotifications.count{it.tag==tag})
   assertEquals(1,manager.activeNotifications.count{it.tag?.startsWith("demo:bob:")==true && it.tag !in previous})
  } finally { compose.runOnUiThread { DemoMonitor.stop(context);controls.select(false,"alice") } }
 }
}
