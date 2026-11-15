package cl.grupo28.hestia

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import cl.grupo28.hestia.core.navigation.HestiaApp
import androidx.lifecycle.ViewModelProvider
import cl.grupo28.hestia.core.HestiaViewModel
import cl.grupo28.hestia.data.DataStoreSettingsRepository

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val viewModel = ViewModelProvider(this, HestiaViewModel.Factory(DataStoreSettingsRepository(applicationContext)))[HestiaViewModel::class.java]
        setContent {
            HestiaApp(viewModel)
        }
    }
}
