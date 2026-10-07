package dev.dovie.shabbosmode

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import androidx.core.view.WindowCompat
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    val page = mutableStateOf("home")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        page.value = validPage(savedInstanceState?.getString("page") ?: intent?.getStringExtra("page"))
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        AppScheduler.createChannels(this)
        SyncWorker.ensureScheduled(this)
        SyncWorker.refreshNow(this)
        lifecycleScope.launch { AppGraph.get(this@MainActivity).scheduler.reschedule() }
        setContent { AppRoot(AppGraph.get(this), page) }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        page.value = validPage(intent.getStringExtra("page"))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("page", page.value)
        super.onSaveInstanceState(outState)
    }

    private fun validPage(value: String?) = value?.takeIf {
        it in setOf("home", "prepare", "alarms", "shuls", "settings", "clock", "setup")
    } ?: "home"

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { AppGraph.get(this@MainActivity).scheduler.reschedule() }
    }
}
