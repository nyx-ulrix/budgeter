package com.nyxulrix.budgeter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nyxulrix.budgeter.BuildConfig
import com.nyxulrix.budgeter.update.Updater
import kotlinx.coroutines.launch

/** Strip under the toolbar when a newer release is out. */
@Composable
fun UpdateBanner() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val release by Updater.available.collectAsState()
    val busy by Updater.busy.collectAsState()
    val status by Updater.status.collectAsState()
    var dismissed by rememberSaveable { mutableStateOf<String?>(null) }
    val r = release ?: return
    if (dismissed == r.version) return
    Row(
        Modifier.fillMaxWidth().background(Px.blue).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(if (busy) (status ?: "Updating…") else "v${r.version} is out", style = Type.small, color = Px.creamLight, modifier = Modifier.weight(1f))
        if (!busy) {
            PixelButton("Later", { dismissed = r.version }, kind = Kind.SECONDARY)
            PixelButton("Update", { scope.launch { Updater.install(ctx, r) } })
        }
    }
    Box(Modifier.fillMaxWidth().height(3.dp).background(Px.brown))
}

/** Profile → Updates: version, manual check, install, and (debug builds) the local test server. */
@Composable
fun UpdatesWindow() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val release by Updater.available.collectAsState()
    val status by Updater.status.collectAsState()
    val busy by Updater.busy.collectAsState()
    var server by remember { mutableStateOf(Updater.testServer(ctx)) }
    Window("Updates") {
        KeyValue("This version", BuildConfig.VERSION_NAME + if (BuildConfig.DEBUG) " (debug)" else "")
        release?.let { r ->
            Body("Version ${r.version} is available.")
            if (r.notes.isNotBlank()) Small(r.notes.take(400))
            PixelButton("Update now", { scope.launch { Updater.install(ctx, r) } }, Modifier.fillMaxWidth(), enabled = !busy)
        }
        PixelButton("Check for updates", { scope.launch { Updater.check(ctx, force = true) } }, kind = Kind.SECONDARY, enabled = !busy)
        status?.let { Small(it, color = Px.brown) }
        Small("Updates come from github.com/${BuildConfig.UPDATE_REPO} releases and only install if signed with this app's key.")
        if (BuildConfig.DEBUG) FoldWindow("Test server (debug builds)") {
            Small("Point update checks at the PC's test server instead of GitHub. Emulator: http://10.0.2.2:8787. Phone on Wi-Fi: http://<PC address>:8787. Blank = GitHub.")
            PixelField(server, { server = it }, "Server", keyboard = KeyboardType.Uri, placeholder = "http://192.168.1.10:8787")
            PixelButton("Save", { Updater.setTestServer(ctx, server); Updater.status.value = "Saved. Tap Check for updates." }, kind = Kind.SECONDARY)
        }
    }
}
