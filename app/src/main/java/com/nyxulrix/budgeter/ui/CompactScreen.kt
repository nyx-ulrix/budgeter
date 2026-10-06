package com.nyxulrix.budgeter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nyxulrix.budgeter.App
import com.nyxulrix.budgeter.R
import com.nyxulrix.budgeter.core.money
import com.nyxulrix.budgeter.core.parseMoney
import com.nyxulrix.budgeter.data.AppState
import com.nyxulrix.budgeter.data.Txn
import com.nyxulrix.budgeter.data.activeTrip
import com.nyxulrix.budgeter.data.currency
import com.nyxulrix.budgeter.data.liveTxns
import com.nyxulrix.budgeter.data.saveTxn
import com.nyxulrix.budgeter.data.snapshot
import java.time.LocalDate

/**
 * True on a small screen such as a flip phone's cover display (Oppo Find N Flip, Galaxy Z Flip Flex Window).
 * Uses the smallest side, so a normal phone (360dp+) turned landscape never triggers it.
 */
@Composable
fun isCompact(): Boolean = LocalConfiguration.current.smallestScreenWidthDp < 320

/**
 * Concise view for a cover screen: today's number, month bar, quick add and scan. Nothing else.
 * [adding] opens straight into quick add (from the widget or the + button).
 */
@Composable
fun CompactScreen(st: AppState, adding: Boolean = false, onDone: () -> Unit = {}) {
    val scanner = LocalScanner.current
    val cur = st.currency
    val snap = st.snapshot()
    var add by remember(adding) { mutableStateOf(adding) }
    Column(
        Modifier.fillMaxSize().background(Px.cream).verticalScroll(rememberScrollState()).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // One strip: mascot, today's number, pace.
        Row(
            Modifier.fillMaxWidth().pixelShadow(3.dp).frame(Px.orange).padding(6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Art(artId("mascot_${moodOf(snap.pace)}", R.drawable.mascot_idle), 32.dp)
            Column(Modifier.weight(1f)) {
                Text("TODAY", style = Type.label, color = Px.creamLight)
                Text(money(snap.day.remaining, cur), style = Type.number.copy(fontSize = 14.sp), color = if (snap.day.remaining < 0) Px.white else Px.creamLight)
            }
        }
        if (add) {
            QuickAdd(st) { add = false; onDone() }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${(snap.fraction * 100).toInt()}% of month", style = Type.small, modifier = Modifier.weight(1f))
                Chip("${paceSymbol(snap.pace)} ${snap.pace.label}", paceColor(snap.pace))
            }
            PixelProgress(snap.fraction, color = paceColor(snap.pace), blocks = 10)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PixelButton("Add", { add = true }, Modifier.weight(1f), glyph = Glyphs.plus)
                PixelButton("Scan", { scanner.camera() }, Modifier.weight(1f), kind = Kind.SECONDARY, glyph = Glyphs.camera)
            }
            st.liveTxns.maxByOrNull { it.date + it.updatedAt.toString().padStart(15, '0') }?.let {
                Small("Last: ${it.merchant.ifBlank { it.category }} ${money(it.myShare, cur)}")
            }
            Small("Open the phone for everything else.")
        }
    }
}

/** Amount + category + save. Home currency only; full details live in the main app. */
@Composable
private fun QuickAdd(st: AppState, onDone: () -> Unit) {
    val cur = st.currency
    val trip = st.activeTrip()
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(st.categories.first()) }
    var toTrip by remember { mutableStateOf(trip != null) }
    val v = parseMoney(amount, cur)
    PixelField(amount, { amount = it }, "Amount ($cur)", keyboard = KeyboardType.Decimal, placeholder = "0.00")
    Choice(st.categories.take(4) + "Other", category, { it }, { category = it })
    if (trip != null) Choice(listOf(true, false), toTrip, { if (it) "Trip" else "Budget" }, { toTrip = it })
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PixelButton("Save", {
            App.store.saveTxn(Txn(date = LocalDate.now().toString(), total = v!!, category = category, tripId = if (toTrip) trip?.id else null))
            onDone()
        }, Modifier.weight(1f), enabled = v != null && v > 0)
        PixelButton("X", onDone, kind = Kind.SECONDARY)
    }
}
