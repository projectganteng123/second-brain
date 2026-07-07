package com.secondbrain.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.secondbrain.app.navigation.NavGraph
import com.secondbrain.app.ui.theme.SecondBrainTheme
import com.secondbrain.app.util.PendingProcessor
import com.secondbrain.app.util.PrefsManager
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()

        val app = application as SecondBrainApp
        val prefs = PrefsManager(this)
        val openInput = intent?.getBooleanExtra(EXTRA_OPEN_INPUT, false) == true

        // Saat app dibuka, di latar & bertahap: cabut alarm basi (catatan yang sudah
        // dihapus/diarsip/diproses ulang), lalu proses catatan offline yang tertunda.
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { com.secondbrain.app.notification.AlarmJanitor.sweep(applicationContext) }
            runCatching { PendingProcessor.processAll(app.repository, prefs) }
        }

        setContent {
            SecondBrainTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    NavGraph(navController, app.repository, prefs, openInput)
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    companion object {
        const val EXTRA_OPEN_INPUT = "open_input"
    }
}
