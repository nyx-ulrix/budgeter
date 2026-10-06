package com.nyxulrix.budgeter.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nyxulrix.budgeter.App
import com.nyxulrix.budgeter.R
import com.nyxulrix.budgeter.core.money
import com.nyxulrix.budgeter.core.parseMoney
import com.nyxulrix.budgeter.core.plain
import com.nyxulrix.budgeter.data.AppState
import com.nyxulrix.budgeter.data.TRIP_COST_KINDS
import com.nyxulrix.budgeter.data.Trip
import com.nyxulrix.budgeter.data.TripCost
import com.nyxulrix.budgeter.data.addTrip
import com.nyxulrix.budgeter.data.countries
import com.nyxulrix.budgeter.data.countryName
import com.nyxulrix.budgeter.data.currency
import com.nyxulrix.budgeter.data.currencyOf
import com.nyxulrix.budgeter.data.deleteTrip
import com.nyxulrix.budgeter.data.deleteTripCost
import com.nyxulrix.budgeter.data.liveTxns
import com.nyxulrix.budgeter.data.monthsToSave
import com.nyxulrix.budgeter.data.payTripCost
import com.nyxulrix.budgeter.data.periodOf
import com.nyxulrix.budgeter.data.saveTrip
import com.nyxulrix.budgeter.data.saveTripCost
import com.nyxulrix.budgeter.data.setTripMonthly
import com.nyxulrix.budgeter.data.topUpTrip
import com.nyxulrix.budgeter.data.tripFund
import com.nyxulrix.budgeter.data.tripFundAtStart
import com.nyxulrix.budgeter.data.tripSpent
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Trips list for the Budget tab: what each trip is saving toward, or what's left while you're on it. */
@Composable
fun TripsWindow(st: AppState) {
    val nav = LocalNav.current
    val cur = st.currency
    var adding by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    FoldWindow("Trips") {
        if (st.trips.isEmpty()) Small("Plan a trip's costs and set some money aside each month. Spending on the trip then stays out of your daily budget.")
        st.trips.sortedBy { it.start }.forEach { t ->
            val started = !today.isBefore(LocalDate.parse(t.start))
            val fund = st.tripFund(t.id)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { nav.go(Screen.TripView(t.id)) },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Art(R.drawable.icon_airplane, 22.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Body(t.name)
                    Small("${countryName(t.country)} · ${t.start}" + if (t.monthly > 0 && !started) " · ${money(t.monthly, cur)}/month" else "")
                    if (!started && t.target > 0) PixelProgress(fund.toFloat() / t.target, color = Px.blue, blocks = 12)
                }
                val left = fund - st.tripSpent(t.id)
                Text(
                    if (started) money(left, cur) + " left" else money(fund, cur) + if (t.target > 0) " / " + money(t.target, cur) else " saved",
                    style = Type.small, color = if (started && left < 0) Px.red else Px.brown,
                )
            }
        }
        PixelButton("+ Trip", { adding = true }, art = R.drawable.icon_suitcase)
    }
    if (adding) TripDialog(st, null) { adding = false }
}

/** Create or edit a trip's basics. Costs and saving are set on the trip screen. */
@Composable
fun TripDialog(st: AppState, existing: Trip?, onDismiss: () -> Unit) {
    val nav = LocalNav.current
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var country by remember { mutableStateOf(existing?.country ?: st.setup?.country ?: "US") }
    var start by remember { mutableStateOf(existing?.start?.let(LocalDate::parse) ?: LocalDate.now().plusMonths(3)) }
    var end by remember { mutableStateOf(existing?.end?.let(LocalDate::parse) ?: LocalDate.now().plusMonths(3).plusDays(6)) }
    var groupId by remember { mutableStateOf(existing?.groupId) }
    var picking by remember { mutableStateOf(false) }
    PixelDialog(if (existing == null) "New trip" else "Edit trip", onDismiss) {
        PixelField(name, { name = it }, "Trip name", placeholder = "Tokyo 2027")
        Label("Destination")
        PickerBox("${countryName(country)} · ${currencyOf(country)}") { picking = true }
        DateField("From", start, { start = it })
        DateField("To", end, { end = it })
        if (st.groups.isNotEmpty()) {
            Label("Shared with")
            Choice(listOf<String?>(null) + st.groups.map { it.id }, groupId, { id -> st.groups.firstOrNull { it.id == id }?.name ?: "Just me" }, { groupId = it })
        }
        PixelButton(if (existing == null) "Create and plan costs" else "Save", {
            val t = (existing ?: Trip(name = "", country = country, currency = "", start = "", end = ""))
                .copy(name = name.trim(), country = country, currency = currencyOf(country), start = start.toString(), end = end.toString(), groupId = groupId)
            if (existing == null) { App.store.addTrip(t); nav.go(Screen.TripView(t.id)) } else App.store.saveTrip(t)
            onDismiss()
        }, Modifier.fillMaxWidth(), enabled = name.isNotBlank() && !end.isBefore(start))
    }
    if (picking) SearchPicker("Destination", countries.map { it.code to "${it.name} (${it.code})" }, { picking = false }) { country = it; picking = false }
}

@Composable
fun TripScreen(st: AppState, id: String) {
    val nav = LocalNav.current
    val t = st.trips.firstOrNull { it.id == id } ?: run { LaunchedEffect(id) { nav.back() }; return }
    val cur = st.currency
    val today = LocalDate.now()
    val start = LocalDate.parse(t.start)
    val end = LocalDate.parse(t.end)
    val started = !today.isBefore(start)
    val fund = st.tripFund(id)
    val spent = st.tripSpent(id)
    val left = fund - spent
    var editing by remember { mutableStateOf(false) }

    Page {
        PageHeader(t.name)
        Small("${countryName(t.country)} · pays in ${t.currency} · ${t.start} to ${t.end}")

        CostsWindow(st, t)
        if (!started) SavingWindow(st, t)

        Window(if (started) "Trip fund" else "Fund so far") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Art(artId("mascot_travel", R.drawable.mascot_idle), 48.dp)
                Column(Modifier.weight(1f)) {
                    Label("Left in fund")
                    Text(money(left, cur), style = Type.hero, color = if (left < 0) Px.red else Px.brown)
                }
            }
            PixelProgress(if (fund > 0) spent.toFloat() / fund else if (spent > 0) 1f else 0f, color = if (left < 0) Px.red else Px.orange)
            KeyValue("Set aside", money(fund, cur))
            KeyValue("Spent", money(spent, cur))
            val daysLeft = (ChronoUnit.DAYS.between(maxOf(today, start), end) + 1).coerceAtLeast(0)
            if (started && daysLeft > 0) KeyValue("Per day left ($daysLeft days)", money(left.coerceAtLeast(0) / daysLeft, cur))
            if (left < 0) Chip("✖ Over fund", Px.red)
            TopUp(t)
        }

        Window("Expenses") {
            val list = st.liveTxns.filter { it.tripId == id }.sortedByDescending { it.date }
            if (list.isEmpty()) Small("No trip spending yet.")
            list.forEach { TxnRow(st, it) }
            PixelButton("Trip expense", { nav.go(Screen.Expense(tripId = id)) }, glyph = Glyphs.plus)
        }
        t.groupId?.let { gid -> PixelButton("Group balances", { nav.go(Screen.GroupView(gid)) }, Modifier.fillMaxWidth(), kind = Kind.SECONDARY) }
        PixelButton("Edit trip", { editing = true }, Modifier.fillMaxWidth(), kind = Kind.SECONDARY)
        PixelButton("Delete trip and its expenses", { App.store.deleteTrip(id); nav.back() }, Modifier.fillMaxWidth(), kind = Kind.DANGER)
    }
    if (editing) TripDialog(st, t) { editing = false }
}

/** Planned costs: the trip's target. Each can be marked paid, which records it as a trip expense. */
@Composable
private fun CostsWindow(st: AppState, t: Trip) {
    val cur = st.currency
    val spentByKind = st.liveTxns.filter { it.tripId == t.id }.groupBy { it.category }.mapValues { (_, l) -> l.sumOf { it.myShare } }
    Window("Planned costs") {
        if (t.costs.isEmpty()) Small("List what the trip will cost: flights, stay, food, activities. Together they set the savings target.")
        t.costs.forEach { c ->
            val paid = c.paidTxnId != null && st.liveTxns.any { it.id == c.paidTxnId }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Column(Modifier.weight(1f)) {
                    Body(c.name)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip(c.kind, Px.cream)
                        if (paid) Chip("✓ Paid", Px.green)
                    }
                }
                Text(money(c.amount, cur), style = Type.body)
                if (!paid) PixelButton("Pay", { App.store.payTripCost(t, c) }, kind = Kind.SECONDARY)
                CloseButton { App.store.deleteTripCost(t.id, c.id) }
            }
        }
        if (t.costs.isNotEmpty()) {
            Rule()
            KeyValue("Planned total", money(t.target, cur))
            val kinds = t.costs.map { it.kind }.distinct().filter { (spentByKind[it] ?: 0) > 0 }
            if (kinds.isNotEmpty()) {
                Label("Planned vs spent")
                kinds.forEach { k ->
                    val planned = t.costs.filter { it.kind == k }.sumOf { it.amount }
                    val s = spentByKind[k] ?: 0
                    KeyValue(k, "${money(s, cur)} / ${money(planned, cur)}", if (s > planned) Px.red else Px.brown)
                }
            }
        }
        AddCost(t)
        Small("\"Pay\" records the cost as a trip expense today, for things like flights booked early.")
    }
}

@Composable
private fun AddCost(t: Trip) {
    val cur = App.store.value.currency
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("Flights") }
    val v = parseMoney(amount, cur)
    Choice(TRIP_COST_KINDS, kind, { it }, { kind = it })
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PixelField(name, { name = it }, "Cost", Modifier.weight(1.4f), placeholder = kind)
        PixelField(amount, { amount = it }, "Amount ($cur)", Modifier.weight(1f), keyboard = KeyboardType.Decimal)
        PixelButton("+", {
            App.store.saveTripCost(t.id, TripCost(name = name.trim().ifBlank { kind }, amount = v!!, kind = kind))
            name = ""; amount = ""
        }, enabled = v != null && v > 0)
    }
}

/** Monthly set-aside until the trip, with a suggested amount that covers the planned costs in time. */
@Composable
private fun SavingWindow(st: AppState, t: Trip) {
    val cur = st.currency
    val fund = st.tripFund(t.id)
    val months = st.monthsToSave(t)
    val stillNeeded = (t.target - fund).coerceAtLeast(0)
    val suggested = if (months > 0) (stillNeeded + months - 1) / months else stillNeeded
    var text by remember(t.monthly) { mutableStateOf(if (t.monthly > 0) plain(t.monthly, cur) else "") }
    val v = if (text.isBlank()) 0L else parseMoney(text, cur)
    val lastMonth = st.periodOf(LocalDate.parse(t.start)).key
    Window("Saving for it") {
        if (t.target > 0) {
            PixelProgress(fund.toFloat() / t.target, color = Px.blue)
            KeyValue("Saved so far", "${money(fund, cur)} / ${money(t.target, cur)}")
            KeyValue("Still needed", money(stillNeeded, cur))
        } else KeyValue("Saved so far", money(fund, cur))
        KeyValue("Months to save", "$months (until $lastMonth)")
        if (t.target > 0 && months > 0) Body("Set aside ${money(suggested, cur)} a month to cover it.")
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PixelField(text, { text = it }, "Set aside per month ($cur)", Modifier.weight(1f), keyboard = KeyboardType.Decimal,
                placeholder = "0.00", error = if (v == null) "Not a number" else null)
            PixelButton("Set", { App.store.setTripMonthly(t, v!!) }, enabled = v != null && v >= 0 && v != t.monthly)
        }
        if (t.target > 0 && months > 0 && suggested != t.monthly) PixelButton("Use ${money(suggested, cur)}", {
            App.store.setTripMonthly(t, suggested)
        }, kind = Kind.SECONDARY)
        if (t.monthly > 0) {
            val atStart = st.tripFundAtStart(t)
            Small("Comes off each month's spendable money until $lastMonth, and stops once the costs are covered.")
            if (t.target > 0 && atStart < t.target) Small("At this rate you'll have ${money(atStart, cur)} by the trip, ${money(t.target - atStart, cur)} short.", color = Px.red)
            else if (t.target > 0) Small("On track to have it all by the trip.", color = Px.green)
        }
    }
}

/** One-off top-up from this month. */
@Composable
private fun TopUp(t: Trip) {
    val cur = App.store.value.currency
    var amount by remember { mutableStateOf("") }
    val v = parseMoney(amount, cur)
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PixelField(amount, { amount = it }, "Add a one-off amount now ($cur)", Modifier.weight(1f), keyboard = KeyboardType.Decimal)
        PixelButton("Add", { App.store.topUpTrip(t, v!!); amount = "" }, enabled = v != null && v > 0)
    }
}
