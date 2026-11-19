package cl.grupo28.hestia

import android.os.Bundle
import android.content.Intent
import androidx.compose.runtime.*
import cl.grupo28.hestia.features.automatic.*
import cl.grupo28.hestia.features.account.AccountViewModel
import cl.grupo28.hestia.features.gmail.GmailViewModel
import cl.grupo28.hestia.features.gmail.GmailAuthorization
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import cl.grupo28.hestia.core.navigation.HestiaApp
import cl.grupo28.hestia.core.navigation.OPEN_PHONE_RECENTS_ACTION
import cl.grupo28.hestia.core.navigation.phoneRecentsRoute
import androidx.lifecycle.ViewModelProvider
import cl.grupo28.hestia.core.HestiaViewModel
import cl.grupo28.hestia.data.DataStoreSettingsRepository

class MainActivity : ComponentActivity() {
    private lateinit var accountViewModel: AccountViewModel
    private lateinit var gmailAuthorization: GmailAuthorization
    private lateinit var automaticModel: AutomaticViewModel
    private lateinit var automaticAuthorization: AutomaticAuthorization
    private var notificationEvent by mutableStateOf<Pair<String,String>?>(null)
    private var recentsRequested by mutableStateOf(false)
    private fun readRecentsRequest(intent: Intent?) {
        if (phoneRecentsRoute != null && intent?.action == OPEN_PHONE_RECENTS_ACTION) {
            recentsRequested = true
            intent.action = null
        }
    }
    private fun readNotification(intent: Intent?) {
        val id=intent?.getStringExtra("hestia_event_id"); val tag=intent?.getStringExtra("hestia_account_tag")
        if(id!=null && tag!=null && id.matches(Regex("[a-fA-F0-9-]{36}")) && (tag.matches(Regex("[a-f0-9]{64}")) || (intent.getStringExtra("hestia_type")=="DEMO_LOCAL" && tag.matches(Regex("demo:(alice|bob)"))))) notificationEvent=id to tag
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getStringExtra("hestia_account_tag")?.startsWith("demo:") != true) setIntent(intent)
        readNotification(intent)
        readRecentsRequest(intent)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val viewModel = ViewModelProvider(this, HestiaViewModel.Factory(DataStoreSettingsRepository(applicationContext)))[HestiaViewModel::class.java]
        accountViewModel = ViewModelProvider(this, AccountViewModel.Factory(applicationContext))[AccountViewModel::class.java]
        val gmailViewModel = ViewModelProvider(this, GmailViewModel.Factory(applicationContext, accountViewModel.state))[GmailViewModel::class.java]
        gmailAuthorization = GmailAuthorization(this, gmailViewModel, savedInstanceState)
        automaticModel=ViewModelProvider(this, AutomaticViewModel.Factory(applicationContext, accountViewModel.state))[AutomaticViewModel::class.java]
        automaticAuthorization=AutomaticAuthorization(this, automaticModel, savedInstanceState)
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) { AutomaticEnvironmentFactory.mode(applicationContext).collect { demo -> if(demo)accountViewModel.stop() else accountViewModel.start() } } }
        readNotification(intent)
        readRecentsRequest(intent)
        setContent {
            HestiaApp(viewModel, accountViewModel, gmailViewModel, gmailAuthorization::authorize, gmailAuthorization::revoke,
                automaticModel, automaticAuthorization::authorize, notificationEvent, { notificationEvent = null },
                recentsRequested, { recentsRequested = false })
        }
    }
    override fun onSaveInstanceState(outState: Bundle) { gmailAuthorization.save(outState); automaticAuthorization.save(outState); super.onSaveInstanceState(outState) }
    override fun onStart() { super.onStart(); if(AutomaticEnvironmentFactory.externalAllowed(applicationContext)) accountViewModel.start(); if(::automaticModel.isInitialized) {automaticModel.refresh();automaticModel.foreground(true)} }
    override fun onStop() { accountViewModel.stop(); if(::automaticModel.isInitialized)automaticModel.foreground(false); super.onStop() }
}
