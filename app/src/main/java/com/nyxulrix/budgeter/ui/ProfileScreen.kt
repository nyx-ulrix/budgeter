package com.nyxulrix.budgeter.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.identity.Identity
import com.nyxulrix.budgeter.App
import com.nyxulrix.budgeter.R
import com.nyxulrix.budgeter.ai.Ai
import com.nyxulrix.budgeter.ai.OpenRouterLogin
import com.nyxulrix.budgeter.ai.PRESETS
import com.nyxulrix.budgeter.ai.Provider
import com.nyxulrix.budgeter.ai.preset
import com.nyxulrix.budgeter.data.AppState
import com.nyxulrix.budgeter.data.countryName
import com.nyxulrix.budgeter.data.setCategories
import com.nyxulrix.budgeter.data.setDaily
import com.nyxulrix.budgeter.data.setSync
import com.nyxulrix.budgeter.data.snapshot
import com.nyxulrix.budgeter.sync.SyncWorker
import com.nyxulrix.budgeter.sync.authRequest
import com.nyxulrix.budgeter.sync.enableSync
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun ProfileScreen(st: AppState) {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val s = st.setup!!
    val snap = st.snapshot()
    val months = st.txns.map { it.date.take(7) }.distinct().size
    Page {
        Window("Profile.exe") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Art(artId("char_profile", R.drawable.char_boy_face), 88.dp, description = "Your pixel character")
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(s.name.ifBlank { "PLAYER 1" }.uppercase(), style = Type.number)
                    Small("${countryName(s.country)} · ${s.currency}")
                    Chip("LV ${months.coerceAtLeast(1)}", Px.blue)
                    Chip("${paceSymbol(snap.pace)} ${snap.pace.label}", paceColor(snap.pace))
                }
            }
            Rule()
            Label("Likes")
            Small("- Staying under budget\n- Receipts that add up")
        }
        Window("Settings") {
            PixelButton("Country, currency, month start", { nav.go(Screen.EditSetup) }, Modifier.fillMaxWidth(), kind = Kind.SECONDARY)
            PixelButton("AI receipt reading", { nav.go(Screen.Ai) }, Modifier.fillMaxWidth(), kind = Kind.SECONDARY, art = R.drawable.icon_receipt)
            PixelButton("Google Sheets sync", { nav.go(Screen.Sync) }, Modifier.fillMaxWidth(), kind = Kind.SECONDARY, glyph = Glyphs.sync)
            PixelButton("Credits", { nav.go(Screen.Credits) }, Modifier.fillMaxWidth(), kind = Kind.SECONDARY, art = R.drawable.icon_heart)
        }
        UpdatesWindow()
        CategoriesWindow(st)
        Small("Budgeter ${ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName}")
    }
}

@Composable
private fun CategoriesWindow(st: AppState) {
    var name by remember { mutableStateOf("") }
    FoldWindow("Categories") {
        st.categories.forEach { c ->
            val daily = c !in st.monthlyCategories
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Body(c, Modifier.weight(1f))
                Choice(listOf(true, false), daily, { if (it) "Daily" else "Monthly" }, { App.store.setDaily(c, it) })
                if (st.categories.size > 1) CloseButton { App.store.setCategories(st.categories - c) }
            }
        }
        Small("Daily: comes out of today's budget. Monthly (groceries, bills): spread over the month. It lowers what's left for the month, so every remaining day's budget drops a little, but today isn't marked as overspent.")
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PixelField(name, { name = it }, "New category", Modifier.weight(1f))
            PixelButton("+", { App.store.setCategories(st.categories + name.trim()); name = "" },
                enabled = name.isNotBlank() && st.categories.none { it.equals(name.trim(), true) })
        }
        Small("Removing a category keeps old expenses as they are. Its colour shows in the month bar.")
    }
}

@Composable
fun AiScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var version by remember { mutableIntStateOf(0) }     // bump to re-read saved providers
    val loginStatus by OpenRouterLogin.status.collectAsState()
    val providers = remember(version, loginStatus) { Ai.providers(ctx) }
    val active = remember(version, loginStatus) { Ai.activeId(ctx) }
    var adding by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Provider?>(null) }

    Page {
        PageHeader("AI")
        Window("How it works") {
            Body("Receipt photos are sent to the AI you pick, to read them into items, tax and service charge. If it can't read photos, only the text your phone reads is sent.")
            Small("With no AI picked, the built-in reader is used. It's free and offline, but less accurate.")
        }
        Window("Your AI logins") {
            if (providers.isEmpty()) Small("None yet. Add one below.")
            providers.forEach { p ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Body(p.label)
                            Small("${preset(p.preset).name} · ${p.model}" + if (!Ai.hasKey(ctx, p)) " · key missing" else "")
                        }
                        if (p.id == active) Chip("● In use", Px.green)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (p.id != active) PixelButton("Use", { Ai.setActive(ctx, p.id); version++ })
                        PixelButton("Edit", { editing = p }, kind = Kind.SECONDARY)
                        PixelButton("Test", {
                            scope.launch {
                                val msg = runCatching { Ai.chat(ctx, p, "Reply with the single word OK.", "Say OK.") }
                                    .fold({ "${p.label} answered: ${it.take(40)}" }, { it.message ?: "Failed" })
                                Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
                            }
                        }, kind = Kind.SECONDARY)
                    }
                    Rule()
                }
            }
            if (active != null) PixelButton("Use built-in reader instead", { Ai.setActive(ctx, null); version++ }, kind = Kind.SECONDARY)
        }
        Window("Add a login") {
            Choice(PRESETS.map { it.id }, adding, { preset(it).name }, { adding = it })
            adding?.let { id ->
                val pr = preset(id)
                Small(pr.note)
                if (id == "openrouter") {
                    PixelButton("Sign in with OpenRouter", { OpenRouterLogin.start(ctx) }, Modifier.fillMaxWidth())
                    loginStatus?.let { Body(it) }
                    var code by remember { mutableStateOf("") }
                    FoldWindow("Sign-in didn't come back?") {
                        Small("Sign in this way instead, then paste the code OpenRouter shows.")
                        PixelButton("Sign in (show code)", { OpenRouterLogin.startHeadless(ctx) }, kind = Kind.SECONDARY)
                        PixelField(code, { code = it }, "Code")
                        PixelButton("Finish", { OpenRouterLogin.pasteCode(ctx, code) }, enabled = code.isNotBlank())
                    }
                    Small("Or paste an OpenRouter API key:")
                }
                ProviderForm(pr.id, null) { version++; adding = null }
            }
        }
    }
    editing?.let { p ->
        PixelDialog("Edit ${p.label}", { editing = null }) { ProviderForm(p.preset, p) { version++; editing = null } }
    }
}

/** Key, label, model (with a fetched list) and base URL for one login. */
@Composable
private fun ProviderForm(presetId: String, existing: Provider?, onSaved: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val pr = preset(presetId)
    var label by remember(presetId) { mutableStateOf(existing?.label ?: pr.name) }
    var base by remember(presetId) { mutableStateOf(existing?.base ?: pr.base) }
    var model by remember(presetId) { mutableStateOf(existing?.model ?: pr.model) }
    var key by remember(presetId) { mutableStateOf("") }
    var models by remember(presetId) { mutableStateOf(listOf(model).filter { it.isNotBlank() }) }
    var loading by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    // Fetch the provider's models once there's a key to ask with (typed, or already saved). Restarts as you type,
    // which doubles as a debounce.
    LaunchedEffect(presetId, base, key) {
        if (key.isBlank() && existing == null) return@LaunchedEffect
        if (!Ai.safeBase(base.trim())) return@LaunchedEffect
        delay(700)
        loading = true
        val probe = (existing ?: Provider(preset = presetId, label = "", base = "", model = "")).copy(base = base.trim())
        val list = runCatching { Ai.models(ctx, probe, key) }.getOrDefault(emptyList())
        loading = false
        if (list.isNotEmpty()) {
            models = list
            if (model !in list) model = list.firstOrNull { it == pr.model } ?: list.first()
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PixelField(label, { label = it }, "Name it", placeholder = "Claude (work)")
        PixelField(key, { key = it }, if (existing != null) "New key (blank keeps the old one)" else "API key", keyboard = KeyboardType.Password)
        if (pr.keyUrl.isNotEmpty()) PixelButton("Get a key", {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(pr.keyUrl)))
        }, kind = Kind.SECONDARY)
        if (presetId == "custom") PixelField(base, { base = it }, "Base URL", placeholder = "http://192.168.1.5:11434/v1", keyboard = KeyboardType.Uri)
        Label("Model")
        PickerBox(model.ifBlank { "Pick a model" }) { picking = true }
        Small(when {
            loading -> "Loading models…"
            key.isBlank() && existing == null -> "Enter your key to load the models you can use."
            else -> "${models.size} model${if (models.size == 1) "" else "s"} available."
        })
        if (base.isNotBlank() && !Ai.safeBase(base.trim())) Small("Use https, or http only for this phone or your home network.", color = Px.red)
        PixelButton("Save", {
            runCatching {
                Ai.save(ctx, (existing ?: Provider(preset = presetId, label = "", base = "", model = "")).copy(label = label.trim(), base = base.trim(), model = model.trim()), key.ifBlank { null })
            }.onSuccess { onSaved() }.onFailure { Toast.makeText(ctx, it.message, Toast.LENGTH_LONG).show() }
        }, Modifier.fillMaxWidth(), enabled = label.isNotBlank() && model.isNotBlank() && Ai.safeBase(base.trim()) && (existing != null || key.isNotBlank()))
        if (existing != null) PixelButton("Remove", { Ai.remove(ctx, existing.id); onSaved() }, Modifier.fillMaxWidth(), kind = Kind.DANGER)
    }
    if (picking) SearchPicker("Model", models.map { it to it }, { picking = false }, allowCustom = true) { model = it; picking = false }
}

@Composable
fun SyncScreen(st: AppState) {
    val ctx = LocalContext.current
    val sync = st.sync
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) runCatching {
            val r = Identity.getAuthorizationClient(ctx).getAuthorizationResultFromIntent(res.data)
            enableSync(r.toGoogleSignInAccount()?.email)
        }.onFailure { Toast.makeText(ctx, "Google sign-in failed: ${it.message}", Toast.LENGTH_LONG).show() }
    }
    fun connect() {
        Identity.getAuthorizationClient(ctx).authorize(authRequest)
            .addOnSuccessListener { r ->
                val pi = r.pendingIntent
                if (r.hasResolution() && pi != null) launcher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                else enableSync(r.toGoogleSignInAccount()?.email)
            }
            .addOnFailureListener {
                Toast.makeText(ctx, "Google sign-in isn't available in this build yet (${it.message}). See docs/google-setup.md.", Toast.LENGTH_LONG).show()
            }
    }
    Page {
        PageHeader("Sheets")
        Window("Google Sheets") {
            Body("Copies every transaction to a spreadsheet called Budgeter in your Google Drive, one tab per month.")
            Small("The app can only see files it made itself. Edits made in the sheet don't come back to the app.")
            when {
                !sync.enabled -> PixelButton("Connect Google", { connect() }, Modifier.fillMaxWidth(), glyph = Glyphs.sync)
                sync.paused != null -> {
                    Chip("✖ Sync paused", Px.red)
                    Body(sync.paused)
                    PixelButton("Sign in again", { connect() }, Modifier.fillMaxWidth())
                }
                else -> Chip("● Sync on", Px.green)
            }
            sync.account?.let { Small("Account: $it") }
            sync.lastSync?.let { Small("Last sync: ${DateFormat.getDateTimeInstance().format(Date(it))}") }
            val pending = st.txns.count { com.nyxulrix.budgeter.sync.Sheets.needsSync(it) }
            if (sync.enabled) Small("$pending change(s) waiting")
            if (sync.spreadsheetId != null) PixelButton("Open spreadsheet", {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://docs.google.com/spreadsheets/d/${sync.spreadsheetId}")))
            }, kind = Kind.SECONDARY)
            if (sync.enabled) {
                PixelButton("Sync now", { SyncWorker.queue(ctx, 0) }, kind = Kind.SECONDARY)
                PixelButton("Turn off sync", { App.store.setSync { it.copy(enabled = false, paused = null) } }, kind = Kind.DANGER)
            }
        }
    }
}

@Composable
fun CreditsScreen() {
    Page {
        PageHeader("Credits")
        Window("Art") {
            Body("Ninja Adventure by Pixel-boy & AAA (CC0)")
            Body("1-bit Pixel Icons by Nikoichu (CC0)")
            Small("Placeholder art. Your own art replaces it; see docs/GRAPHICS.md.")
        }
        Window("Fonts") {
            Body("Press Start 2P, Silkscreen, VT323")
            Small("SIL Open Font License 1.1")
        }
    }
}
