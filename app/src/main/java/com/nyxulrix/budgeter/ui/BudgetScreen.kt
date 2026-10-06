package com.nyxulrix.budgeter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nyxulrix.budgeter.App
import com.nyxulrix.budgeter.R
import com.nyxulrix.budgeter.core.Period
import com.nyxulrix.budgeter.core.money
import com.nyxulrix.budgeter.core.parseMoney
import com.nyxulrix.budgeter.core.plain
import com.nyxulrix.budgeter.data.AppState
import com.nyxulrix.budgeter.data.Line
import com.nyxulrix.budgeter.data.LineKind
import com.nyxulrix.budgeter.data.Plan
import com.nyxulrix.budgeter.data.Planned
import com.nyxulrix.budgeter.data.PlannedStatus
import com.nyxulrix.budgeter.data.addLine
import com.nyxulrix.budgeter.data.byCategory
import com.nyxulrix.budgeter.data.currency
import com.nyxulrix.budgeter.data.editPlan
import com.nyxulrix.budgeter.data.periodOf
import com.nyxulrix.budgeter.data.planFor
import com.nyxulrix.budgeter.data.removeLine
import com.nyxulrix.budgeter.data.reserve
import com.nyxulrix.budgeter.data.reserved
import com.nyxulrix.budgeter.data.snapshot
import com.nyxulrix.budgeter.data.spendable
import com.nyxulrix.budgeter.data.available
import com.nyxulrix.budgeter.data.monthSaved
import com.nyxulrix.budgeter.data.savedToDate
import com.nyxulrix.budgeter.data.tripMonthlyTotal
import com.nyxulrix.budgeter.data.spent
import com.nyxulrix.budgeter.data.startDay
import com.nyxulrix.budgeter.data.total
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
fun BudgetScreen(st: AppState) {
    val nav = LocalNav.current
    val cur = st.currency
    var key by rememberSaveable { mutableStateOf(st.periodOf(LocalDate.now()).key) }
    val p = Period.ofKey(key, st.startDay)
    val plan = st.planFor(key)
    var reserving by remember { mutableStateOf<Planned?>(null) }

    Page {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PixelButton("<", { key = p.prev().key }, kind = Kind.SECONDARY)
            Column1(Modifier.weight(1f)) {
                Text(key, style = Type.hero)
                Small("${p.start} to ${p.end.minusDays(1)}")
            }
            PixelButton(">", { key = p.next().key }, kind = Kind.SECONDARY)
        }

        BudgetWindow(st, key, plan, cur)

        IncomeWindow(key, plan, cur)
        LinesWindow("Extra money", LineKind.EXTRA, key, plan, cur, "Added mid-month: gifts, side jobs, refunds.")
        LinesWindow("Fixed costs", LineKind.FIXED, key, plan, cur, "Taken off first: rent, phone, subscriptions. Copied to next month.")

        // The only place savings totals are shown.
        FoldWindow("Savings") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Art(R.drawable.icon_money_bag, 28.dp)
                Column1(Modifier.weight(1f)) {
                    KeyValue("Saved to date", money(st.savedToDate(key), cur))
                    if (plan.savingsTarget > 0) KeyValue("Target this month", money(plan.savingsTarget, cur))
                    if (!p.end.isAfter(LocalDate.now())) KeyValue("Saved this month", money(st.monthSaved(key), cur))
                    else {
                        val wholeBudget = st.monthSaved(key) + st.spent(p) - maxOf(st.spent(p), st.spendable(key))
                        KeyValue("If you stop spending now", money(st.monthSaved(key), cur))
                        KeyValue("If you spend your budget", money(wholeBudget, cur), if (wholeBudget < 0) Px.red else Px.brown)
                    }
                }
            }
            Small("Whatever you don't spend becomes savings when the month ends, including money you never budgeted. A month that goes over takes it back out.")
        }

        val setAside = plan.lines.filter { it.kind == LineKind.RESERVE || it.kind == LineKind.TRIP_FUND }
        if (setAside.isNotEmpty()) FoldWindow("Set aside this month") {
            setAside.forEach { l ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Body("${l.kind.label}: ${l.name}", Modifier.weight(1f))
                    Text(money(l.amount, cur), style = Type.body)
                    CloseButton { App.store.removeLine(key, l.id) }
                }
            }
            Small("Removing one gives the money back to this month.")
        }


        TripsWindow(st)

        FoldWindow("Planned purchases") {
            val open = st.planned.filter { it.status == PlannedStatus.OPEN }.sortedWith(compareBy({ it.priority }, { it.targetMonth }))
            if (open.isEmpty()) Small("Add things you want to buy later. Reserve money bit by bit, or buy now.")
            open.forEach { pl ->
                PlannedRow(st, pl, onReserve = { reserving = pl })
                Rule()
            }
            PixelButton("+ Planned item", { nav.go(Screen.PlannedEdit()) }, art = R.drawable.icon_star)
            val bought = st.planned.filter { it.status == PlannedStatus.BOUGHT }
            if (bought.isNotEmpty()) {
                Label("Bought")
                bought.forEach { KeyValue("✓ ${it.name}", money(it.price, cur)) }
            }
        }
    }
    reserving?.let { pl -> ReserveDialog(st, pl) { reserving = null } }
}

@Composable
private fun Column1(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) =
    androidx.compose.foundation.layout.Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp), content = content)

/** The monthly budget setting. Shows only what's spendable; the earnings behind it are in the windows below. */
@Composable
private fun BudgetWindow(st: AppState, key: String, plan: Plan, cur: String) {
    val p = Period.ofKey(key, st.startDay)
    val available = st.available(key)
    val spendable = st.spendable(key)
    val spent = st.spent(p)
    var text by remember(key, plan.budget) { mutableStateOf(plan.budget?.let { plain(it, cur) } ?: "") }
    val v = if (text.isBlank()) null else parseMoney(text, cur)
    Window("Spendable.exe", header = when { spent > spendable + plan.savingsTarget -> Px.red; spent > spendable -> Px.yellow; else -> Px.orange }) {
        Label("Spendable this month")
        Text(money(spendable, cur), style = Type.hero, color = if (spendable < 0 || spent > spendable) Px.red else Px.brown)
        Small("Spent so far ${money(spent, cur)}" + when {
            spent > spendable + plan.savingsTarget -> " · over by ${money(spent - spendable - plan.savingsTarget, cur)}"
            spent > spendable -> " · ${money(spent - spendable, cur)} into savings"
            else -> ""
        })
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PixelField(text, { text = it }, "Monthly budget (what you plan to spend)", Modifier.weight(1f), keyboard = KeyboardType.Decimal,
                placeholder = plain(available.coerceAtLeast(0), cur), error = if (text.isNotBlank() && v == null) "Not a number" else null)
            PixelButton("Set", { App.store.editPlan(key) { it.copy(budget = v) } }, enabled = v != null && v >= 0 && v != plan.budget)
        }
        if (v != null && v > available) Small("That's more than the ${money(available, cur)} left after fixed costs and set-asides.", color = Px.red)
        if (plan.budget != null) PixelButton("Use everything available", { App.store.editPlan(key) { it.copy(budget = null) } }, kind = Kind.SECONDARY)
        Rule()
        var targetText by remember(key, plan.savingsTarget) { mutableStateOf(if (plan.savingsTarget > 0) plain(plan.savingsTarget, cur) else "") }
        val tv = if (targetText.isBlank()) 0L else parseMoney(targetText, cur)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PixelField(targetText, { targetText = it }, "Savings target (kept aside first)", Modifier.weight(1f), keyboard = KeyboardType.Decimal,
                placeholder = "0.00", error = if (tv == null) "Not a number" else null)
            PixelButton("Set", { App.store.editPlan(key) { it.copy(savingsTarget = tv!!) } }, enabled = tv != null && tv >= 0 && tv != plan.savingsTarget)
        }
        Small("${money(plan.budget ?: available, cur)} budget − ${money(plan.savingsTarget, cur)} savings target = ${money(spendable, cur)} to spend.", color = Px.brown)
        Small("The target comes off what you can spend straight away. Spending past that dips into it (the bar pulses yellow); past the target too is over budget (red). Both carry over to the next months. Anything else unspent also becomes savings.")
    }
}

@Composable
private fun IncomeWindow(key: String, plan: Plan, cur: String) {
    var text by remember(key, plan.income) { mutableStateOf(plain(plan.income, cur)) }
    val v = parseMoney(text, cur)
    FoldWindow("Money in") {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PixelField(text, { text = it }, "Money in each month (pay, allowance)", Modifier.weight(1f), keyboard = KeyboardType.Decimal,
                error = if (v == null) "Not a number" else null)
            PixelButton("Set", { App.store.editPlan(key) { it.copy(income = v!!) } }, enabled = v != null && v != plan.income)
        }
        Small("Carries over to following months until you change it.")
    }
}

@Composable
private fun LinesWindow(title: String, kind: LineKind, key: String, plan: Plan, cur: String, hint: String) {
    FoldWindow(title) {
        LineEditor(kind, key, plan, cur)
        Small(hint)
    }
}

/** List of lines of one kind with remove buttons, plus an add row. */
@Composable
private fun LineEditor(kind: LineKind, key: String, plan: Plan, cur: String) {
    plan.lines.filter { it.kind == kind }.forEach { l ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Body(l.name, Modifier.weight(1f))
            Text(money(l.amount, cur), style = Type.body)
            CloseButton { App.store.removeLine(key, l.id) }
        }
    }
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    val v = parseMoney(amount, cur)
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PixelField(name, { name = it }, "Name", Modifier.weight(1.4f))
        PixelField(amount, { amount = it }, "Amount", Modifier.weight(1f), keyboard = KeyboardType.Decimal)
        PixelButton("+", {
            App.store.addLine(key, Line(kind = kind, name = name.trim(), amount = v!!))
            name = ""; amount = ""
        }, enabled = name.isNotBlank() && v != null && v > 0)
    }
}


/** Reserve part of a planned item's price from this month. Shows the hit to today's budget. */
@Composable
fun ReserveDialog(st: AppState, p: Planned, onDismiss: () -> Unit) {
    val cur = st.currency
    val left = (p.price - st.reserved(p.id)).coerceAtLeast(0)
    var text by remember { mutableStateOf(plain(left, cur)) }
    val v = parseMoney(text, cur)
    val snap = st.snapshot()
    val daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), snap.period.end).coerceAtLeast(1)
    PixelDialog("Reserve: ${p.name}", onDismiss) {
        Body("Still to reserve: ${money(left, cur)}")
        PixelField(text, { text = it }, "Reserve now", keyboard = KeyboardType.Decimal)
        if (v != null && v > 0) Small("Lowers your daily budget by about ${money(v / daysLeft, cur)} for the rest of the month.", color = Px.brown)
        PixelButton("Reserve", { App.store.reserve(p, v!!); onDismiss() }, Modifier.fillMaxWidth(), enabled = v != null && v > 0)
    }
}
