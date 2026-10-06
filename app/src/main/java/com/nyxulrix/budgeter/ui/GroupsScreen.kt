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
fun GroupsScreen(st: AppState) {
    val nav = LocalNav.current
    var newGroup by remember { mutableStateOf(false) }
    val cur = st.currency
    Page {
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
    }
    if (newGroup) GroupDialog(st, null) { newGroup = false }
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
        val used = st.txns.any { it.groupId == id } || st.trips.any { it.groupId == id }
        PixelButton("Delete group", { App.store.deleteGroup(id); nav.back() }, Modifier.fillMaxWidth(), kind = Kind.DANGER, enabled = !used)
        if (used) Small("This group has expenses or a trip. Delete those first to remove the group.")
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
