package com.nyxulrix.budgeter.ui

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nyxulrix.budgeter.App
import com.nyxulrix.budgeter.R
import com.nyxulrix.budgeter.core.ParsedReceipt
import com.nyxulrix.budgeter.data.AppState
import com.nyxulrix.budgeter.data.snapshot
import com.nyxulrix.budgeter.ocr.Scanner
import com.nyxulrix.budgeter.ocr.rememberScanner

enum class Tab(val label: String, val glyph: List<String>) {
    HOME("Home", Glyphs.home),
    TXNS("Txns", Glyphs.list),
    BUDGET("Budget", Glyphs.coin),
    SHARED("Shared", Glyphs.people),
    PROFILE("Profile", Glyphs.person),
}

/** Screens pushed over the tabs. */
sealed interface Screen {
    data class Expense(val id: String? = null, val receipt: ParsedReceipt? = null, val ocrText: String? = null, val groupId: String? = null, val tripId: String? = null) : Screen
    data class PlannedEdit(val id: String? = null) : Screen
    data class GroupView(val id: String) : Screen
    data class TripView(val id: String) : Screen
    data object Ai : Screen
    data object Sync : Screen
    data object EditSetup : Screen
    data object Credits : Screen
}

/** Tab + a stack of screens. No navigation library: five tabs and a handful of screens. */
class Nav {
    var tab by mutableStateOf(Tab.HOME)
    val stack = mutableStateListOf<Screen>()
    var pendingScan by mutableStateOf(false)
    fun go(s: Screen) { stack.add(s) }
    fun back() { stack.removeLastOrNull() }
    fun reset(t: Tab) { stack.clear(); tab = t }
}

val LocalNav = compositionLocalOf<Nav> { error("no nav") }
val LocalScanner = compositionLocalOf<Scanner> { error("no scanner") }

@Composable
fun Root(nav: Nav) {
    val st by App.store.state.collectAsState()
    Box(Modifier.fillMaxSize().background(Px.cream).windowInsetsPadding(WindowInsets.safeDrawing)) {
        if (st.setup == null) {
            SetupScreen(null)
            return@Box
        }
        val scanner = rememberScanner(st) { receipt, text -> nav.go(Screen.Expense(receipt = receipt, ocrText = text)) }
        LaunchedEffect(nav.pendingScan) { if (nav.pendingScan) { nav.pendingScan = false; scanner.camera() } }
        CompositionLocalProvider(LocalNav provides nav, LocalScanner provides scanner) {
            Column(Modifier.fillMaxSize()) {
                Toolbar(st)
                Box(Modifier.weight(1f)) {
                    when (val top = nav.stack.lastOrNull()) {
                        null -> when (nav.tab) {
                            Tab.HOME -> HomeScreen(st)
                            Tab.TXNS -> TxnsScreen(st)
                            Tab.BUDGET -> BudgetScreen(st)
                            Tab.SHARED -> SharedScreen(st)
                            Tab.PROFILE -> ProfileScreen(st)
                        }
                        is Screen.Expense -> ExpenseScreen(st, top)
                        is Screen.PlannedEdit -> PlannedScreen(st, top.id)
                        is Screen.GroupView -> GroupScreen(st, top.id)
                        is Screen.TripView -> TripScreen(st, top.id)
                        Screen.Ai -> AiScreen()
                        Screen.Sync -> SyncScreen(st)
                        Screen.EditSetup -> SetupScreen(st.setup)
                        Screen.Credits -> CreditsScreen()
                    }
                }
                if (nav.stack.isEmpty()) BottomBar(nav)
            }
            if (scanner.busy) ScanningOverlay(scanner.status)
        }
        BackHandler(enabled = nav.stack.isNotEmpty()) { nav.back() }
    }
}

/** Desktop-style utility bar: icon, name, period, status. */
@Composable
private fun Toolbar(st: AppState) {
    val nav = LocalNav.current
    val snap = st.snapshot()
    Column {
        Row(
            Modifier.fillMaxWidth().background(Px.orange).heightIn(min = 48.dp).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.frame(Px.creamLight, width = 2.dp).padding(2.dp)) { Art(artId("mascot_${moodOf(snap.pace)}", R.drawable.mascot_idle), 28.dp) }
            Text("BUDGETER", style = Type.number, color = Px.creamLight, modifier = Modifier.weight(1f))
            Chip(snap.period.key, Px.creamLight)
            if (st.sync.enabled) Box(
                Modifier.clickable(role = Role.Button, onClickLabel = "Sync settings") { nav.go(Screen.Sync) }
            ) { Chip(if (st.sync.paused != null) "Sync paused" else "Synced", if (st.sync.paused != null) Px.red else Px.green) }
        }
        Box(Modifier.fillMaxWidth().height(3.dp).background(Px.brown))
    }
}

@Composable
private fun BottomBar(nav: Nav) {
    Column {
        Box(Modifier.fillMaxWidth().height(3.dp).background(Px.brown))
        Row(Modifier.fillMaxWidth().background(Px.cream)) {
            Tab.entries.forEach { t ->
                val on = nav.tab == t
                Column(
                    Modifier.weight(1f).heightIn(min = 56.dp)
                        .background(if (on) Px.orange else Px.cream)
                        .clickable(role = Role.Tab) { nav.tab = t }
                        .semantics { selected = on }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                ) {
                    Glyph(t.glyph, if (on) Px.creamLight else Px.brown, 18.dp)
                    Text(t.label.uppercase(), style = Type.label, color = if (on) Px.creamLight else Px.brown)
                }
            }
        }
    }
}

/** Scrolling page body with the brief's larger gaps between windows than inside them. */
@Composable
fun Page(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 14.dp, end = 18.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        content = content,
    )
}

/** Page title row with a back button, for pushed screens. */
@Composable
fun PageHeader(title: String) {
    val nav = LocalNav.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PixelButton("< Back", { nav.back() }, kind = Kind.SECONDARY)
        Text(title.uppercase(), style = Type.hero, modifier = Modifier.weight(1f))
    }
}

fun moodOf(p: com.nyxulrix.budgeter.core.Pace) = when (p) {
    com.nyxulrix.budgeter.core.Pace.ON_TRACK -> "happy"
    com.nyxulrix.budgeter.core.Pace.SLIGHTLY_OVER -> "worried"
    com.nyxulrix.budgeter.core.Pace.OVER -> "over"
}

/** Looks up a drawable by file name so dropping in new art (docs/GRAPHICS.md) needs no code change. */
@SuppressLint("DiscouragedApi")
@Composable
@DrawableRes
fun artId(name: String, @DrawableRes fallback: Int): Int {
    val ctx = LocalContext.current
    return ctx.resources.getIdentifier(name, "drawable", ctx.packageName).takeIf { it != 0 } ?: fallback
}
