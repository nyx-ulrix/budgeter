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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.nyxulrix.budgeter.core.settle
import com.nyxulrix.budgeter.data.AppState
import com.nyxulrix.budgeter.data.Group
import com.nyxulrix.budgeter.data.ME
import com.nyxulrix.budgeter.data.Trip
import com.nyxulrix.budgeter.data.addPerson
import com.nyxulrix.budgeter.data.addTrip
import com.nyxulrix.budgeter.data.countries
import com.nyxulrix.budgeter.data.countryName
import com.nyxulrix.budgeter.data.currency
import com.nyxulrix.budgeter.data.currencyOf
import com.nyxulrix.budgeter.data.deleteGroup
import com.nyxulrix.budgeter.data.deleteTrip
import com.nyxulrix.budgeter.data.groupNet
import com.nyxulrix.budgeter.data.liveTxns
import com.nyxulrix.budgeter.data.personName
import com.nyxulrix.budgeter.data.saveGroup
import com.nyxulrix.budgeter.data.saveTrip
import com.nyxulrix.budgeter.data.settleUp
import com.nyxulrix.budgeter.data.topUpTrip
import com.nyxulrix.budgeter.data.tripFund
import com.nyxulrix.budgeter.data.tripSpent
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
fun SharedScreen(st: AppState) {
    val nav = LocalNav.current
    var seg by rememberSaveable { mutableStateOf("Groups") }
    var newGroup by remember { mutableStateOf(false) }
    var newTrip by remember { mutableStateOf(false) }
    val cur = st.currency
    Page {
        Choice(listOf("Groups", "Trips"), seg, { it }, { seg = it })
        if (seg == "Groups") {
            Window("Groups") {
                if (st.groups.isEmpty()) Small("Make a group for roommates, friends or family to split bills and track who owes whom.")
                st.groups.forEach { g ->
                    val mine = st.groupNet(g.id)[ME] ?: 0
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { nav.go(Screen.GroupView(g.id)) },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Glyph(Glyphs.people, Px.blue)
                        Column(Modifier.weight(1f)) {
                            Body(g.name)
                            Small(g.members.joinToString { st.personName(it) })
                        }
                        Text(when { mine > 0 -> "+" + money(mine, cur); mine < 0 -> money(mine, cur); else -> "settled" }, style = Type.body,
                            color = if (mine < 0) Px.red else Px.brown)
                    }
                }
                PixelButton("+ Group", { newGroup = true }, glyph = Glyphs.people)
            }
        } else {
            Window("Trips") {
                if (st.trips.isEmpty()) Small("A trip gets its own fund, set aside from this month. Trip spending then stays out of your daily budget.")
                st.trips.sortedByDescending { it.start }.forEach { t ->
                    val left = st.tripFund(t.id) - st.tripSpent(t.id)
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { nav.go(Screen.TripView(t.id)) },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Art(R.drawable.icon_airplane, 22.dp)
                        Column(Modifier.weight(1f)) {
                            Body(t.name)
                            Small("${countryName(t.country)} · ${t.start} to ${t.end}")
                        }
                        Text(money(left, cur) + " left", style = Type.body, color = if (left < 0) Px.red else Px.brown)
                    }
                }
                PixelButton("+ Trip", { newTrip = true }, art = R.drawable.icon_suitcase)
            }
        }
    }
    if (newGroup) GroupDialog(st, null) { newGroup = false }
    if (newTrip) TripDialog(st, null) { newTrip = false }
}

/** Create or edit a group: name and members (existing people or new names). */
@Composable
fun GroupDialog(st: AppState, existing: Group?, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var members by remember { mutableStateOf(existing?.members?.toSet() ?: setOf(ME)) }
    var newName by remember { mutableStateOf("") }
    PixelDialog(if (existing == null) "New group" else "Edit group", onDismiss) {
        PixelField(name, { name = it }, "Group name", placeholder = "Flatmates")
        Label("Members")
        Toggles(st.people.map { it.id to it.name }, members) { id ->
            if (id != ME) members = if (id in members) members - id else members + id
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PixelField(newName, { newName = it }, "Add a person", Modifier.weight(1f))
            PixelButton("+", { members = members + App.store.addPerson(newName); newName = "" }, enabled = newName.isNotBlank())
        }
        PixelButton("Save", {
            App.store.saveGroup((existing ?: Group(name = "")).copy(name = name.trim(), members = listOf(ME) + (members - ME).toList()))
            onDismiss()
        }, Modifier.fillMaxWidth(), enabled = name.isNotBlank() && members.size > 1)
    }
}

@Composable
fun GroupScreen(st: AppState, id: String) {
    val nav = LocalNav.current
    val g = st.groups.firstOrNull { it.id == id } ?: run { LaunchedEffect(id) { nav.back() }; return }
    val cur = st.currency
    val net = st.groupNet(id)
    var editing by remember { mutableStateOf(false) }
    var paying by remember { mutableStateOf<com.nyxulrix.budgeter.core.Transfer?>(null) }
    Page {
        PageHeader(g.name)
        Window("Balances") {
            net.forEach { (who, v) ->
                KeyValue(st.personName(who), when { v > 0 -> "is owed ${money(v, cur)}"; v < 0 -> "owes ${money(-v, cur)}"; else -> "settled" },
                    if (v < 0) Px.red else Px.brown)
            }
        }
        Window("Settle up") {
            val transfers = settle(net)
            if (transfers.isEmpty()) Small("Everyone is square.")
            transfers.forEach { t ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Body("${st.personName(t.from)} → ${st.personName(t.to)}: ${money(t.amount, cur)}", Modifier.weight(1f))
                    PixelButton("Paid", { paying = t }, kind = Kind.SECONDARY)
                }
            }
            if (transfers.isNotEmpty()) Small("Tap Paid to log a payment. You can log part of it.")
        }
        Window("Expenses") {
            val list = st.liveTxns.filter { it.groupId == id }.sortedByDescending { it.date }
            if (list.isEmpty()) Small("No shared expenses yet.")
            list.forEach { TxnRow(st, it) }
            PixelButton("Shared expense", { nav.go(Screen.Expense(groupId = id)) }, glyph = Glyphs.plus)
        }
        PixelButton("Edit group", { editing = true }, Modifier.fillMaxWidth(), kind = Kind.SECONDARY)
        PixelButton("Delete group", { App.store.deleteGroup(id); nav.back() }, Modifier.fillMaxWidth(), kind = Kind.DANGER)
    }
    if (editing) GroupDialog(st, g) { editing = false }
    paying?.let { t ->
        var amount by remember(t) { mutableStateOf(com.nyxulrix.budgeter.core.plain(t.amount, cur)) }
        val v = parseMoney(amount, cur)
        PixelDialog("Log payment", { paying = null }) {
            Body("${st.personName(t.from)} pays ${st.personName(t.to)}")
            PixelField(amount, { amount = it }, "Amount ($cur)", keyboard = KeyboardType.Decimal)
            PixelButton("Log it", { App.store.settleUp(id, t.from, t.to, v!!); paying = null }, Modifier.fillMaxWidth(),
                enabled = v != null && v > 0 && v <= t.amount)
        }
    }
}

/** Create or edit a trip. A new trip's fund is set aside from this month. */
@Composable
fun TripDialog(st: AppState, existing: Trip?, onDismiss: () -> Unit) {
    val cur = st.currency
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var country by remember { mutableStateOf(existing?.country ?: st.setup?.country ?: "US") }
    var start by remember { mutableStateOf(existing?.start?.let(LocalDate::parse) ?: LocalDate.now()) }
    var end by remember { mutableStateOf(existing?.end?.let(LocalDate::parse) ?: LocalDate.now().plusDays(4)) }
    var fund by remember { mutableStateOf("") }
    var groupId by remember { mutableStateOf(existing?.groupId) }
    var picking by remember { mutableStateOf(false) }
    val fundV = if (fund.isBlank()) 0L else parseMoney(fund, cur)
    PixelDialog(if (existing == null) "New trip" else "Edit trip", onDismiss) {
        PixelField(name, { name = it }, "Trip name", placeholder = "Tokyo 2026")
        Label("Destination")
        PickerBox("${countryName(country)} · ${currencyOf(country)}") { picking = true }
        DateField("From", start, { start = it })
        DateField("To", end, { end = it })
        if (existing == null) {
            PixelField(fund, { fund = it }, "Trip fund ($cur)", keyboard = KeyboardType.Decimal, error = if (fundV == null) "Not a number" else null)
            Small("Set aside from this month now. Top it up any time.")
        }
        if (st.groups.isNotEmpty()) {
            Label("Shared with")
            Choice(listOf<String?>(null) + st.groups.map { it.id }, groupId, { id -> st.groups.firstOrNull { it.id == id }?.name ?: "Just me" }, { groupId = it })
        }
        PixelButton("Save", {
            val t = (existing ?: Trip(name = "", country = country, currency = "", start = "", end = ""))
                .copy(name = name.trim(), country = country, currency = currencyOf(country), start = start.toString(), end = end.toString(), groupId = groupId)
            if (existing == null) App.store.addTrip(t, fundV!!) else App.store.saveTrip(t)
            onDismiss()
        }, Modifier.fillMaxWidth(), enabled = name.isNotBlank() && !end.isBefore(start) && fundV != null)
    }
    if (picking) SearchPicker("Destination", countries.map { it.code to "${it.name} (${it.code})" }, { picking = false }) { country = it; picking = false }
}

@Composable
fun TripScreen(st: AppState, id: String) {
    val nav = LocalNav.current
    val t = st.trips.firstOrNull { it.id == id } ?: run { LaunchedEffect(id) { nav.back() }; return }
    val cur = st.currency
    val fund = st.tripFund(id)
    val spent = st.tripSpent(id)
    val left = fund - spent
    val today = LocalDate.now()
    val end = LocalDate.parse(t.end)
    val daysLeft = (ChronoUnit.DAYS.between(maxOf(today, LocalDate.parse(t.start)), end) + 1).coerceAtLeast(0)
    var topUp by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }
    val topV = parseMoney(topUp, cur)
    Page {
        PageHeader(t.name)
        Window("Trip fund") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Art(artId("mascot_travel", R.drawable.mascot_idle), 48.dp)
                Column(Modifier.weight(1f)) {
                    Label("Left in fund")
                    Text(money(left, cur), style = Type.hero, color = if (left < 0) Px.red else Px.brown)
                }
            }
            PixelProgress(if (fund > 0) spent.toFloat() / fund else 1f, color = if (left < 0) Px.red else Px.orange)
            KeyValue("Fund", money(fund, cur))
            KeyValue("Spent", money(spent, cur))
            if (daysLeft > 0) KeyValue("Per day left ($daysLeft days)", money(left.coerceAtLeast(0) / daysLeft, cur))
            Small("${countryName(t.country)} · pays in ${t.currency} · ${t.start} to ${t.end}")
            if (left < 0) Chip("✖ Over fund", Px.red)
        }
        Window("Top up") {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PixelField(topUp, { topUp = it }, "Add to fund ($cur)", Modifier.weight(1f), keyboard = KeyboardType.Decimal)
                PixelButton("Add", { App.store.topUpTrip(t, topV!!); topUp = "" }, enabled = topV != null && topV > 0)
            }
            Small("Comes out of this month's spendable money.")
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
