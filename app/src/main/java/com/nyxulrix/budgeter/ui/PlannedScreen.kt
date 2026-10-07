package com.nyxulrix.budgeter.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.nyxulrix.budgeter.App
import com.nyxulrix.budgeter.core.money
import com.nyxulrix.budgeter.core.parseMoney
import com.nyxulrix.budgeter.core.plain
import com.nyxulrix.budgeter.data.AppState
import com.nyxulrix.budgeter.data.Planned
import com.nyxulrix.budgeter.data.PlannedStatus
import com.nyxulrix.budgeter.data.buyNow
import com.nyxulrix.budgeter.data.currency
import com.nyxulrix.budgeter.data.deletePlanned
import com.nyxulrix.budgeter.data.periodOf
import com.nyxulrix.budgeter.data.reserved
import com.nyxulrix.budgeter.data.savePlanned
import com.nyxulrix.budgeter.data.snapshot
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

@Composable
fun PlannedScreen(st: AppState, id: String?) {
    val nav = LocalNav.current
    val cur = st.currency
    val existing = id?.let { pid -> st.planned.firstOrNull { it.id == pid } }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var price by remember { mutableStateOf(existing?.price?.let { plain(it, cur) } ?: "") }
    var priority by remember { mutableStateOf(existing?.priority ?: 2) }
    val months = (0..12).map { YearMonth.now().plusMonths(it.toLong()).toString() }
    var target by remember { mutableStateOf(existing?.targetMonth ?: st.periodOf(LocalDate.now()).key) }
    var category by remember { mutableStateOf("Shopping") }
    var reserving by remember { mutableStateOf(false) }
    val priceV = parseMoney(price, cur)
    val ok = name.isNotBlank() && priceV != null && priceV > 0

    Page {
        PageHeader(if (existing == null) "Plan to buy" else existing.name)
        Window("Item") {
            PixelField(name, { name = it }, "What")
            PixelField(price, { price = it }, "Price ($cur)", keyboard = KeyboardType.Decimal)
            Label("Priority")
            Choice(listOf(1, 2, 3), priority, { mapOf(1 to "High", 2 to "Medium", 3 to "Low")[it]!! }, { priority = it })
            Label("Target month")
            Choice((months + target).distinct().sorted(), target, { it }, { target = it })
            if (existing != null && priceV != null && priceV < st.reserved(existing.id))
                Small("You've set aside ${money(st.reserved(existing.id), cur)}. Saving frees the ${money(st.reserved(existing.id) - priceV, cur)} above the new price.", color = Px.brown)
            PixelButton(if (existing == null) "Save" else "Save changes", {
                App.store.savePlanned((existing ?: Planned(name = "", price = 0, targetMonth = target))
                    .copy(name = name.trim(), price = priceV!!, priority = priority, targetMonth = target))
                nav.back()
            }, Modifier.fillMaxWidth(), enabled = ok)
        }

        if (existing != null && existing.status == PlannedStatus.OPEN) {
            val res = st.reserved(existing.id)
            val left = (existing.price - res).coerceAtLeast(0)
            val daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), st.snapshot().period.end).coerceAtLeast(1)
            Window("Progress") {
                PixelProgress(res.toFloat() / existing.price, color = Px.blue)
                KeyValue("Reserved", money(res, cur))
                KeyValue("Still to reserve", money(left, cur))
                Small("Reserving the rest now would lower your daily budget by about ${money(left / daysLeft, cur)}.")
                PixelButton("Reserve / change amount", { reserving = true }, Modifier.fillMaxWidth(), kind = Kind.SECONDARY)
            }
            Window("Buy now") {
                Body("Records ${money(existing.price, cur)} as spent today. The ${money(res, cur)} already reserved isn't counted twice.")
                Label("Category")
                Choice(st.categories, category, { it }, { category = it })
                PixelButton("Buy now", { App.store.buyNow(existing, category); nav.back() }, Modifier.fillMaxWidth())
            }
        }
        if (existing != null) PixelButton(if (existing.status == PlannedStatus.OPEN) "Delete (frees reserved money)" else "Delete", {
            App.store.deletePlanned(existing.id); nav.back()
        }, Modifier.fillMaxWidth(), kind = Kind.DANGER)
    }
    if (reserving && existing != null) ReserveDialog(st, existing) { reserving = false }
}
