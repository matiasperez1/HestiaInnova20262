package cl.grupo28.hestia

import android.os.Bundle
import cl.grupo28.hestia.features.account.AccountViewModel
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import cl.grupo28.hestia.core.navigation.HestiaApp
import androidx.lifecycle.ViewModelProvider
import cl.grupo28.hestia.core.HestiaViewModel
import cl.grupo28.hestia.data.DataStoreSettingsRepository

class MainActivity : ComponentActivity() {
    private lateinit var accountViewModel: AccountViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val viewModel = ViewModelProvider(this, HestiaViewModel.Factory(DataStoreSettingsRepository(applicationContext)))[HestiaViewModel::class.java]
        accountViewModel = ViewModelProvider(this, AccountViewModel.Factory(applicationContext))[AccountViewModel::class.java]
        setContent {
            HestiaApp(viewModel, accountViewModel)
        }
    }
    override fun onStart() { super.onStart(); accountViewModel.start() }
    override fun onStop() { accountViewModel.stop(); super.onStop() }
}
