package com.nyxulrix.budgeter.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.nyxulrix.budgeter.R
import com.nyxulrix.budgeter.core.Period
import com.nyxulrix.budgeter.core.money
import com.nyxulrix.budgeter.data.AppState
import com.nyxulrix.budgeter.data.Txn
import com.nyxulrix.budgeter.data.currency
import com.nyxulrix.budgeter.data.liveTxns
import com.nyxulrix.budgeter.data.periodOf
import com.nyxulrix.budgeter.data.startDay
import java.time.LocalDate

@Composable
fun TxnsScreen(st: AppState) {
    var key by rememberSaveable { mutableStateOf(st.periodOf(LocalDate.now()).key) }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var scope by rememberSaveable { mutableStateOf("All") }
    val p = Period.ofKey(key, st.startDay)
    val list = st.liveTxns
        .filter { LocalDate.parse(it.date) in p }
        .filter { category == null || it.category == category || it.items.any { i -> i.category == category } }
        .filter { query.isBlank() || (it.merchant + " " + it.note + " " + it.items.joinToString { i -> i.name }).contains(query, true) }
        .filter { when (scope) { "Budget" -> it.tripId == null; "Trips" -> it.tripId != null; "Split" -> it.people > 1 || it.shares.size > 1; else -> true } }
        .sortedByDescending { it.date + it.updatedAt.toString().padStart(15, '0') }

    Page {
        Window("Transactions") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PixelButton("<", { key = p.prev().key }, kind = Kind.SECONDARY)
                Text(key, style = Type.number, modifier = Modifier.weight(1f))
                PixelButton(">", { key = p.next().key }, kind = Kind.SECONDARY)
            }
            Small("${p.start} to ${p.end.minusDays(1)}")
        }
        val active = query.isNotBlank() || scope != "All" || category != null
        FoldWindow(if (active) "Search & filters (on)" else "Search & filters", open = false) {
            PixelField(query, { query = it }, "", placeholder = "Search merchant, item, note")
            Choice(listOf("All", "Budget", "Trips", "Split"), scope, { it }, { scope = it })
            Choice(listOf<String?>(null) + st.categories, category, { it ?: "Any category" }, { category = it })
            if (active) PixelButton("Clear", { query = ""; scope = "All"; category = null }, kind = Kind.SECONDARY)
        }
        Window("${list.size} items") {
            if (list.isEmpty()) Small("Nothing here.")
            var lastDate = ""
            list.forEach { t ->
                if (t.date != lastDate) { lastDate = t.date; Label(LocalDate.parse(t.date).let { "${it.dayOfWeek.name.take(3)} $it" }) }
                TxnRow(st, t)
            }
            if (list.isNotEmpty()) {
                Rule()
                KeyValue("My share total", money(list.sumOf { it.myShare }, st.currency))
            }
        }
    }
}

/** One transaction: what, category, my share. Tap to edit. */
@Composable
fun TxnRow(st: AppState, t: Txn) {
    val nav = LocalNav.current
    val cur = st.currency
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClickLabel = "Edit") { nav.go(Screen.Expense(t.id)) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (t.tripId != null) Art(R.drawable.icon_airplane, 18.dp, description = "Trip")
        else if (t.items.isNotEmpty()) Art(R.drawable.icon_receipt, 18.dp, description = "Itemised")
        Column(Modifier.weight(1f)) {
            Body(t.merchant.ifBlank { t.category })
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(t.category, Px.cream)
                if (t.people > 1 || t.shares.size > 1) Chip("Split ×${maxOf(t.people, t.shares.size)}", Px.blue)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(money(t.myShare, cur), style = Type.body)
            if (t.myShare != t.total) Small("of ${money(t.total, cur)}")
            t.foreign?.let { Small(money(it.amount, it.currency)) }
        }
    }
}
