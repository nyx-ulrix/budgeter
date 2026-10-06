package com.nyxulrix.budgeter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nyxulrix.budgeter.R
import com.nyxulrix.budgeter.core.Pace
import com.nyxulrix.budgeter.core.money
import com.nyxulrix.budgeter.data.AppState
import com.nyxulrix.budgeter.data.Planned
import com.nyxulrix.budgeter.data.PlannedStatus
import com.nyxulrix.budgeter.data.activeTrip
import com.nyxulrix.budgeter.data.byCategory
import com.nyxulrix.budgeter.data.currency
import com.nyxulrix.budgeter.data.dailyImpact
import com.nyxulrix.budgeter.data.liveTxns
import com.nyxulrix.budgeter.data.ME
import com.nyxulrix.budgeter.data.planFor
import com.nyxulrix.budgeter.data.reserved
import com.nyxulrix.budgeter.data.snapshot
import com.nyxulrix.budgeter.data.tripFund
import com.nyxulrix.budgeter.data.tripSpent
import java.time.LocalDate
import java.time.temporal.ChronoUnit

fun paceColor(p: Pace): Color = when (p) {
    Pace.ON_TRACK -> Px.green
    Pace.SLIGHTLY_OVER -> Px.orange
    Pace.OVER -> Px.red
}

/** Brown normally, orange once today's budget is used and you're into saved-up money, red past that too. */
fun dayColor(d: com.nyxulrix.budgeter.core.Day) = when { d.remaining < 0 -> Px.red; d.dailyLeft < 0 -> Px.orange; else -> Px.brown }

fun paceSymbol(p: Pace) = when (p) { Pace.ON_TRACK -> "●"; Pace.SLIGHTLY_OVER -> "▲"; Pace.OVER -> "✖" }

@Composable
fun HomeScreen(st: AppState) {
    val nav = LocalNav.current
    val scanner = LocalScanner.current
    val cur = st.currency
    val snap = st.snapshot()
    val now = LocalDate.now()
    var reserving by remember { mutableStateOf<Planned?>(null) }

    Page {
        // Above the fold: today, this month, quick actions.
        Window("Today.exe") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Art(artId("mascot_${moodOf(snap.pace)}", R.drawable.mascot_idle), 56.dp, description = null)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Label("Left to spend today")
                    Text(money(snap.day.dailyLeft, cur), style = Type.hero, color = dayColor(snap.day))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Small("Today's budget ${money(snap.day.budget, cur)}")
                Small("Spent ${money(snap.day.spent, cur)}")
            }
            PixelProgress(
                if (snap.day.budget > 0) snap.day.spent.toFloat() / snap.day.budget else if (snap.day.spent > 0) 1f else 0f,
                color = when { snap.day.remaining < 0 -> Px.red; snap.day.dailyLeft < 0 -> Px.yellow; else -> Px.orange },
            )
            SavedUp(snap.day, cur)
            Sparkline(st, now)
        }

        Window("This month", header = if (snap.over) Px.red else Px.orange) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${money(snap.spent, cur)} / ${money(snap.spendable, cur)}", style = Type.body, modifier = Modifier.weight(1f))
                when {
                    snap.over -> Chip("✖ Over budget", Px.red)
                    snap.dipping -> Chip("▲ Dipping into savings", Px.yellow)
                    else -> Chip("${paceSymbol(snap.pace)} ${snap.pace.label}", paceColor(snap.pace))
                }
            }
            MonthBar(st, snap)
            val daysLeft = ChronoUnit.DAYS.between(now, snap.period.end)
            Small(when {
                snap.over -> "${money(snap.spent - snap.spendable - snap.target, cur)} over"
                snap.dipping -> "${money(snap.dipped, cur)} of your ${money(snap.target, cur)} savings target spent"
                else -> "${money(snap.left, cur)} left" + if (snap.target > 0) " · ${money(snap.target, cur)} kept for savings" else ""
            } + " · $daysLeft days to go · resets ${snap.period.end}")
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PixelButton("Expense", { nav.go(Screen.Expense()) }, Modifier.weight(1f), glyph = Glyphs.plus)
            PixelButton("Scan", { scanner.camera() }, Modifier.weight(1f), glyph = Glyphs.camera)
        }

        // Secondary, folded.
        st.activeTrip(now)?.let { trip ->
            val left = st.tripFund(trip.id) - st.tripSpent(trip.id)
            FoldWindow("Trip: ${trip.name}") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Art(R.drawable.icon_airplane, 24.dp)
                    Body("${money(left, cur)} left in trip fund", Modifier.weight(1f), if (left < 0) Px.red else Px.brown)
                }
                Small("Trip spending doesn't touch your daily budget.")
                PixelButton("Open trip", { nav.go(Screen.TripView(trip.id)) }, kind = Kind.SECONDARY)
            }
        }

        val top = st.planned.filter { it.status == PlannedStatus.OPEN }.sortedWith(compareBy({ it.priority }, { it.targetMonth })).take(2)
        FoldWindow("Planned purchases") {
            if (top.isEmpty()) Small("Nothing planned yet. Add something you're saving for.")
            top.forEach { p -> PlannedRow(st, p, onReserve = { reserving = p }) }
            PixelButton("+ Planned item", { nav.go(Screen.PlannedEdit()) }, kind = Kind.SECONDARY, art = R.drawable.icon_star)
        }

        FoldWindow("Recent") {
            val recent = st.liveTxns.sortedByDescending { it.date + it.updatedAt.toString().padStart(15, '0') }.take(3)
            if (recent.isEmpty()) Small("No spending yet.")
            recent.forEach { TxnRow(st, it) }
        }

        Small("Savings totals are kept off this screen. See Budget → Savings.")
    }

    reserving?.let { p -> ReserveDialog(st, p) { reserving = null } }
}

/**
 * Money saved up this month: what earlier days left unspent, added up day by day, minus anything already
 * dipped into today. It can be spent any day this month; at month end it becomes savings.
 */
@Composable
private fun SavedUp(day: com.nyxulrix.budgeter.core.Day, cur: String) {
    Row(
        Modifier.fillMaxWidth().frame(Px.cream, width = 2.dp).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Art(R.drawable.icon_coin, 22.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Label("Saved up this month")
            Small(when {
                day.bonus <= 0 -> "Spend less than today's budget and the rest builds up here."
                day.dailyLeft < 0 -> "Using it today: ${money(day.bonus - day.bonusLeft, cur)} of ${money(day.bonus, cur)}."
                else -> "Unspent from earlier days. Use it any day this month."
            })
        }
        Text(money(day.bonusLeft, cur), style = Type.number, color = if (day.bonusLeft > 0) Px.green else Px.muted)
    }
}

/** Spent per day for the last 3 days as small bars. */
@Composable
private fun Sparkline(st: AppState, today: LocalDate) {
    val days = (2 downTo 0).map { today.minusDays(it.toLong()) }
    val spent = days.map { d -> st.liveTxns.filter { it.tripId == null && it.date == d.toString() }.sumOf { st.dailyImpact(it) } }
    val max = spent.max().coerceAtLeast(1)
    Row(
        Modifier.semantics { contentDescription = "Last three days: " + spent.joinToString { money(it, st.currency) } },
        verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Label("Last 3 days")
        days.forEachIndexed { i, d ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.width(18.dp).height((4 + 24 * spent[i] / max).toInt().dp).background(if (i == 2) Px.orange else Px.blue))
                Text(d.dayOfWeek.name.take(2), style = Type.label)
            }
        }
    }
}

@Composable
fun PlannedRow(st: AppState, p: Planned, onReserve: () -> Unit) {
    val nav = LocalNav.current
    val cur = st.currency
    val res = st.reserved(p.id)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Body(p.name, Modifier.weight(1f))
            Text(money(p.price, cur), style = Type.body)
        }
        PixelProgress(if (p.price > 0) res.toFloat() / p.price else 1f, color = Px.blue, blocks = 12)
        Small("Reserved ${money(res, cur)} · ${money((p.price - res).coerceAtLeast(0), cur)} to go · for ${p.targetMonth}")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PixelButton("Reserve", onReserve, kind = Kind.SECONDARY)
            PixelButton("Buy now", { nav.go(Screen.PlannedEdit(p.id)) })
        }
    }
}
