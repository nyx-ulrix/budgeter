package com.nyxulrix.budgeter

import androidx.lifecycle.lifecycleScope
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.nyxulrix.budgeter.ai.OpenRouterLogin
import com.nyxulrix.budgeter.date.DateClient
import com.nyxulrix.budgeter.date.Phase
import com.nyxulrix.budgeter.update.Updater
import kotlinx.coroutines.launch
import com.nyxulrix.budgeter.ui.BudgeterTheme
import com.nyxulrix.budgeter.ui.Nav
import com.nyxulrix.budgeter.ui.Root
import com.nyxulrix.budgeter.ui.Screen
import com.nyxulrix.budgeter.ui.Tab

class MainActivity : ComponentActivity() {
    private val nav = Nav()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handle(intent)
        App.scope.launch { Updater.check(applicationContext) }
        // The shared date budget, once per process (a rotated activity keeps what is loaded): the saved session, else a
        // quiet sign-in with the Google account already used here (the one login; no UI when there isn't one).
        if (DateClient.state.value.phase == Phase.SignedOut) lifecycleScope.launch {
            DateClient.resume()
            com.nyxulrix.budgeter.ui.dateGoogleSignIn(this@MainActivity, quiet = true)
        }
        setContent { BudgeterTheme { Root(nav) } }
    }

    /** Opening the app pulls changes made in the web app (sync on). */
    override fun onResume() {
        super.onResume()
        com.nyxulrix.budgeter.sync.SyncWorker.queue(this, 0)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** Widget taps and the OpenRouter sign-in callback land here. */
    private fun handle(intent: Intent?) {
        intent?.data?.takeIf { it.scheme == "budgeter" && it.host == "openrouter" }?.let {
            OpenRouterLogin.finish(this, it)
            nav.reset(Tab.PROFILE); nav.go(Screen.Ai)
            return
        }
        when (intent?.getStringExtra(EXTRA_OPEN)) {
            "add" -> { nav.reset(Tab.HOME); nav.go(Screen.Expense()) }
            "scan" -> { nav.reset(Tab.HOME); nav.pendingScan = true }
            "planned" -> nav.reset(Tab.BUDGET)
            "home" -> nav.reset(Tab.HOME)
            "macros" -> nav.reset(Tab.MACROS)
        }
    }

    companion object { const val EXTRA_OPEN = "open" }
}
