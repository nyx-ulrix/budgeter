package com.nyxulrix.budgeter

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.nyxulrix.budgeter.ai.OpenRouterLogin
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
        setContent { BudgeterTheme { Root(nav) } }
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
        }
    }

    companion object { const val EXTRA_OPEN = "open" }
}
