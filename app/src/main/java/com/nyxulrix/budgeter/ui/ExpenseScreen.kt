package com.nyxulrix.budgeter.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nyxulrix.budgeter.App
import com.nyxulrix.budgeter.ai.Ai
import com.nyxulrix.budgeter.core.ParsedReceipt
import com.nyxulrix.budgeter.core.ReceiptItem
import com.nyxulrix.budgeter.core.SplitMethod
import com.nyxulrix.budgeter.core.allocate
import com.nyxulrix.budgeter.core.convert
import com.nyxulrix.budgeter.core.itemShares
import com.nyxulrix.budgeter.core.money
import com.nyxulrix.budgeter.core.parseMoney
import com.nyxulrix.budgeter.core.plain
import com.nyxulrix.budgeter.core.split
import com.nyxulrix.budgeter.data.AppState
import com.nyxulrix.budgeter.data.Foreign
import com.nyxulrix.budgeter.data.ME
import com.nyxulrix.budgeter.data.Txn
import com.nyxulrix.budgeter.data.TxnItem
import com.nyxulrix.budgeter.data.activeTrip
import com.nyxulrix.budgeter.data.currency
import com.nyxulrix.budgeter.data.currencyCodes
import com.nyxulrix.budgeter.data.deleteTxn
import com.nyxulrix.budgeter.data.newId
import com.nyxulrix.budgeter.data.personId
import com.nyxulrix.budgeter.data.personLabel
import com.nyxulrix.budgeter.data.Rates
import com.nyxulrix.budgeter.data.saveTxn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate

class ItemDraft(name: String, price: String, qty: Int = 1, owners: Set<String> = emptySet(), category: String? = null) {
    var name by mutableStateOf(name)
    var price by mutableStateOf(price)
    var qty by mutableStateOf(qty)
    var owners by mutableStateOf(owners)
    var category by mutableStateOf(category)
}

/** Everything the editor holds, as text where the user types, so half-typed numbers survive. */
class Draft(home: String) {
    var itemised by mutableStateOf(false)
    var amount by mutableStateOf("")
    var tax by mutableStateOf("")
    var service by mutableStateOf("")
    var discount by mutableStateOf("")
    var printedTotal by mutableStateOf("")
    var taxIncluded by mutableStateOf(false)
    var merchant by mutableStateOf("")
    var date by mutableStateOf(LocalDate.now())
    var category by mutableStateOf("Other")
    var note by mutableStateOf("")
    var tripId by mutableStateOf<String?>(null)
    var paidCur by mutableStateOf(home)
    var rate by mutableStateOf("1")
    var people by mutableStateOf(1)                 // P1 (me) .. P[people]
    var payer by mutableStateOf(ME)
    var method by mutableStateOf(SplitMethod.EQUAL)
    val inputs = mutableStateMapOf<String, String>()
    val items = mutableStateListOf<ItemDraft>()

    fun load(r: ParsedReceipt, fallbackCur: String) {
        paidCur = r.currency ?: fallbackCur
        fun p(v: Long) = if (v == 0L) "" else plain(v, paidCur)
        merchant = r.merchant.ifBlank { merchant }
        r.date?.let { runCatching { date = LocalDate.parse(it) } }
        items.clear(); items += r.items.map { ItemDraft(it.name, plain(it.price, paidCur), it.qty) }
        itemised = r.items.isNotEmpty()
        discount = p(r.discount); service = p(r.serviceCharge); tax = p(r.tax); taxIncluded = r.taxIncluded
        printedTotal = r.total?.let { plain(it, paidCur) } ?: ""
        if (!itemised) amount = r.total?.let { plain(it, paidCur) } ?: ""
    }
}

private fun draftFrom(st: AppState, s: Screen.Expense): Draft {
    val home = st.currency
    val d = Draft(home)
    val t = s.id?.let { id -> st.txns.firstOrNull { it.id == id } }
    val trip = st.activeTrip()
    when {
        t != null -> {
            val cur = t.foreign?.currency ?: home
            val paid = t.foreign?.amount ?: t.total
            d.paidCur = cur; d.rate = (t.foreign?.rate ?: 1.0).toString()
            d.itemised = t.items.isNotEmpty()
            d.amount = plain(if (t.items.isEmpty()) paid else paid - t.serviceCharge - (if (t.taxIncluded) 0 else t.tax), cur)
            d.tax = if (t.tax != 0L) plain(t.tax, cur) else ""
            d.service = if (t.serviceCharge != 0L) plain(t.serviceCharge, cur) else ""
            d.discount = if (t.discount != 0L) plain(t.discount, cur) else ""
            d.taxIncluded = t.taxIncluded
            d.printedTotal = if (d.itemised) plain(paid, cur) else ""
            d.items += t.items.map { ItemDraft(it.name, plain(it.price, cur), it.qty, it.owners, it.category) }
            d.merchant = t.merchant; d.date = LocalDate.parse(t.date); d.category = t.category; d.note = t.note
            d.tripId = t.tripId; d.payer = t.payer; d.method = t.method
            d.people = maxOf(t.people, t.shares.size, 1)
            t.splitInput.forEach { (who, v) ->
                d.inputs[who] = when (t.method) {
                    SplitMethod.PERCENT -> BigDecimal.valueOf(v, 2).stripTrailingZeros().toPlainString()
                    SplitMethod.EXACT -> plain(v, cur)
                    else -> v.toString()
                }
            }
        }
        else -> {
            val t2 = s.tripId?.let { id -> st.trips.firstOrNull { it.id == id } } ?: trip
            d.tripId = t2?.id
            d.paidCur = t2?.currency ?: home
            s.receipt?.let { d.load(it, d.paidCur) }
        }
    }
    return d
}

@Composable
fun ExpenseScreen(st: AppState, s: Screen.Expense) {
    val ctx = LocalContext.current
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val home = st.currency
    val d = remember(s) { draftFrom(st, s) }
    val existing = s.id?.let { id -> st.txns.firstOrNull { it.id == id } }
    var catFor by remember { mutableStateOf<ItemDraft?>(null) }
    var rereading by remember { mutableStateOf(false) }
    val cur = d.paidCur
    val splitting = d.people > 1
    val members = (1..d.people).map(::personId)

    // Fetch a rate when paying in another currency; the user can overwrite it.
    LaunchedEffect(cur) {
        if (cur == home) d.rate = "1"
        else if (existing?.foreign?.currency != cur) { d.rate = ""; Rates.rate(cur, home)?.let { r -> if (d.rate.isBlank()) d.rate = r.toString() } }
    }

    // Derived numbers.
    fun m(text: String) = if (text.isBlank()) 0L else parseMoney(text, cur)
    val errors = mutableListOf<String>()
    val taxV = m(d.tax); val svcV = m(d.service); val discV = m(d.discount)
    if (taxV == null || svcV == null || discV == null) errors += "A charge isn't a number"
    val receipt = if (d.itemised) ParsedReceipt(
        items = d.items.map { ReceiptItem(it.name, parseMoney(it.price, cur) ?: 0, it.qty) },
        discount = discV ?: 0, serviceCharge = svcV ?: 0, tax = taxV ?: 0, taxIncluded = d.taxIncluded,
        total = if (d.printedTotal.isBlank()) null else parseMoney(d.printedTotal, cur),
    ) else null
    if (receipt != null) {
        if (d.items.isEmpty()) errors += "Add at least one item"
        if (d.items.any { parseMoney(it.price, cur) == null }) errors += "An item price isn't a number"
        if (receipt.mismatch != 0L) errors += "Items and charges come to ${money(receipt.computedTotal, cur)} but the receipt total is ${money(receipt.total!!, cur)}. Fix an item or clear the total."
    }
    val amountV = m(d.amount)
    if (!d.itemised && (amountV == null || d.amount.isBlank())) errors += "Enter an amount"
    val totalPaid = receipt?.computedTotal ?: (amountV ?: 0)
    if (totalPaid <= 0) errors += "The total must be more than zero"
    val rate = if (cur == home) 1.0 else d.rate.toDoubleOrNull()?.takeIf { it > 0 }
    if (rate == null) errors += "Enter the exchange rate"
    val totalHome = convert(totalPaid, cur, home, rate ?: 1.0)
    val costsPaid = receipt?.itemCosts().orEmpty()
    val costsHome = costsPaid.map { convert(it, cur, home, rate ?: 1.0) }.toMutableList().also { list ->
        if (list.isNotEmpty()) { val i = list.indices.maxBy { list[it] }; list[i] += totalHome - list.sum() }
    }
    val shares: Map<String, Long> = if (!splitting) emptyMap() else runCatching {
        val parts = when (d.method) {
            SplitMethod.ITEMS -> itemShares(costsHome, d.items.map { it -> it.owners.map { o -> members.indexOf(o) }.filter { i -> i >= 0 }.toSet() }, members.size)
            SplitMethod.EQUAL -> split(totalHome, SplitMethod.EQUAL, members.map { 1L })
            SplitMethod.SHARES -> split(totalHome, SplitMethod.SHARES, members.map { d.inputs[it]?.toLongOrNull() ?: 1L })
            SplitMethod.PERCENT -> split(totalHome, SplitMethod.PERCENT, members.map {
                BigDecimal(d.inputs[it]?.ifBlank { "0" } ?: "0").movePointRight(2).toLong()
            })
            SplitMethod.EXACT -> {
                val paid = members.map { parseMoney(d.inputs[it] ?: "", cur) ?: 0L }
                require(paid.sum() == totalPaid) { "Amounts add up to ${money(paid.sum(), cur)}, not ${money(totalPaid, cur)}" }
                allocate(totalHome, paid)
            }
        }
        require(parts.sum() == totalHome) { "Split doesn't add up to the total" }
        members.zip(parts).toMap()
    }.getOrElse { errors += (it.message ?: "Split doesn't add up"); emptyMap() }

    fun save() {
        val t = Txn(
            id = existing?.id ?: newId(),
            date = d.date.toString(),
            total = totalHome,
            category = d.category,
            merchant = d.merchant.trim(),
            note = d.note.trim(),
            tax = if (d.itemised) taxV ?: 0 else 0, serviceCharge = if (d.itemised) svcV ?: 0 else 0, discount = if (d.itemised) discV ?: 0 else 0,
            taxIncluded = d.taxIncluded,
            payer = if (splitting && d.payer in members) d.payer else ME,
            method = d.method,
            people = d.people,
            shares = shares,
            splitInput = if (!splitting) emptyMap() else members.associateWith { who ->
                val v = d.inputs[who].orEmpty()
                when (d.method) {
                    SplitMethod.PERCENT -> runCatching { BigDecimal(v).movePointRight(2).toLong() }.getOrDefault(0)
                    SplitMethod.EXACT -> parseMoney(v, cur) ?: 0
                    SplitMethod.SHARES -> v.toLongOrNull() ?: 1
                    else -> 0
                }
            },
            items = if (d.itemised) d.items.mapIndexed { i, it ->
                TxnItem(it.name.trim(), parseMoney(it.price, cur) ?: 0, it.qty, costsHome.getOrElse(i) { 0 }, it.owners, it.category)
            } else emptyList(),
            tripId = d.tripId,
            foreign = if (cur != home) Foreign(cur, totalPaid, rate!!) else null,
            plannedId = existing?.plannedId,
            fromReserve = existing?.fromReserve ?: 0,
            syncedAt = existing?.syncedAt,
            syncedTab = existing?.syncedTab,
        )
        App.store.saveTxn(t)
        nav.back()
    }

    Page {
        PageHeader(when { existing != null -> "Edit"; s.receipt != null -> "Check receipt"; else -> "New expense" })

        Window(if (d.itemised) "Receipt items" else "Expense") {
            Choice(listOf(false, true), d.itemised, { if (it) "Itemised" else "Simple" }, {
                d.itemised = it
                if (!it && d.method == SplitMethod.ITEMS) d.method = SplitMethod.EQUAL
            })
            if (!d.itemised) {
                PixelField(d.amount, { d.amount = it }, "Amount ($cur)", keyboard = KeyboardType.Decimal, placeholder = "0.00")
            } else {
                d.items.forEachIndexed { i, item ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                            PixelField(item.name, { item.name = it }, "Item ${i + 1}", Modifier.weight(1.6f))
                            PixelField(item.price, { item.price = it }, "Price", Modifier.weight(1f), keyboard = KeyboardType.Decimal)
                            CloseButton { d.items.remove(item) }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (item.qty > 1) Small("×${item.qty}")
                            costsPaid.getOrNull(i)?.let { Small("Incl. charges ${money(it, cur)}", color = Px.brown) }
                            Text("[${item.category ?: d.category}]", style = Type.label,
                                modifier = Modifier.heightIn(min = 32.dp).clickable(role = Role.Button, onClickLabel = "Change item category") { catFor = item }.padding(4.dp))
                        }
                        if (splitting && d.method == SplitMethod.ITEMS) Toggles(
                            members.map { it to personLabel(it) }, item.owners,
                        ) { who -> item.owners = if (who in item.owners) item.owners - who else item.owners + who }
                    }
                }
                PixelButton("+ Item", { d.items += ItemDraft("", "") }, kind = Kind.SECONDARY)
                Rule()
                PixelField(d.discount, { d.discount = it }, "Bill discount", keyboard = KeyboardType.Decimal)
                PixelField(d.service, { d.service = it }, "Service charge (printed)", keyboard = KeyboardType.Decimal)
                PixelField(d.tax, { d.tax = it }, "Tax / GST (printed)", keyboard = KeyboardType.Decimal)
                Choice(listOf(false, true), d.taxIncluded, { if (it) "Prices include tax" else "Tax on top" }, { d.taxIncluded = it })
                PixelField(d.printedTotal, { d.printedTotal = it }, "Receipt total (to check)", keyboard = KeyboardType.Decimal)
            }
            Rule()
            KeyValue("Total", money(totalPaid, cur))
            if (cur != home) KeyValue("In $home", money(totalHome, home))
            if (splitting) KeyValue("My share (P1)", money(shares[ME] ?: 0, home))
        }

        Window("Details") {
            PixelField(d.merchant, { d.merchant = it }, "Name", placeholder = "What was it?")
            DateField("Date", d.date, { d.date = it })
            Label("Category")
            Choice(st.categories, d.category, { it }, { d.category = it })
            PixelField(d.note, { d.note = it }, "Note", singleLine = false)
            if (cur != home) PixelField(d.rate, { d.rate = it }, "Paid in $cur · 1 $cur = ? $home", keyboard = KeyboardType.Decimal)
        }

        if (st.trips.isNotEmpty()) Window("Count toward") {
            val trips = st.trips.sortedByDescending { it.start }
            Choice(listOf<String?>(null) + trips.map { it.id }, d.tripId, { id -> id?.let { tid -> "Trip: " + trips.first { it.id == tid }.name } ?: "Monthly budget" }, { id ->
                d.tripId = id
                trips.firstOrNull { it.id == id }?.let { t -> d.paidCur = t.currency }
            })
            if (d.tripId != null) Small("Comes out of the trip fund, not your daily budget.")
        }

        Window("Split") {
            Label("Split between")
            Choice((1..8).toList(), d.people, { if (it == 1) "Just me" else "$it people" }, { n ->
                d.people = n
                if (d.payer !in (1..n).map(::personId)) d.payer = ME
            })
            if (splitting) {
                Small("You are P1. Everyone else is P2, P3 and so on. No names needed.")
                Label("Who paid")
                Choice(members, d.payer, { personLabel(it) }, { d.payer = it })
                Label("How to split")
                Choice(SplitMethod.entries.filter { it != SplitMethod.ITEMS || d.itemised }, d.method, { it.label }, { d.method = it })
                when (d.method) {
                    SplitMethod.EQUAL, SplitMethod.ITEMS -> Unit
                    else -> members.forEach { who ->
                        PixelField(d.inputs[who] ?: "", { d.inputs[who] = it }, personLabel(who) + when (d.method) {
                            SplitMethod.PERCENT -> " (%)"; SplitMethod.SHARES -> " (shares)"; else -> " ($cur)"
                        }, keyboard = KeyboardType.Decimal, placeholder = if (d.method == SplitMethod.SHARES) "1" else "0")
                    }
                }
                if (d.method == SplitMethod.ITEMS) Small("Tap P numbers under each item. None picked = shared by everyone.")
                if (shares.isNotEmpty()) {
                    Rule()
                    members.forEach { who ->
                        KeyValue(personLabel(who) + if (who == ME) " (you)" else "", money(shares[who] ?: 0, home))
                        if (d.itemised) {
                            val mine = d.items.filter { it.owners.isEmpty() || who in it.owners }.map { it.name.ifBlank { "item" } }
                            if (mine.isNotEmpty()) Small(mine.joinToString(" · "))
                        }
                    }
                    Rule()
                    if (d.payer == ME) members.filter { it != ME && (shares[it] ?: 0) > 0 }.forEach {
                        Body("${personLabel(it)} owes you ${money(shares[it] ?: 0, home)}")
                    } else if ((shares[ME] ?: 0) > 0) Body("You owe ${personLabel(d.payer)} ${money(shares[ME] ?: 0, home)}")
                }
            }
        }

        s.ocrText?.let { text ->
            FoldWindow("Scanned text") {
                Small("Read again with:")
                val providers = remember { Ai.providers(ctx) }
                Choice(listOf<String?>(null) + providers.map { it.id }, null, { id -> providers.firstOrNull { it.id == id }?.label ?: "Built-in reader" }, onSelect = { id ->
                    if (rereading) return@Choice Unit
                    rereading = true
                    scope.launch {
                        runCatching { Ai.parse(ctx, providers.firstOrNull { it.id == id }, text, d.paidCur) }
                            .onSuccess { d.load(it, d.paidCur) }
                            .onFailure { Toast.makeText(ctx, it.message, Toast.LENGTH_LONG).show() }
                        rereading = false
                    }
                })
                if (rereading) Small("Reading…")
                Small(text)
            }
        }

        errors.forEach { Text("! $it", style = Type.small, color = Px.red) }
        PixelButton(if (existing != null) "Save changes" else "Save expense", { save() }, Modifier.fillMaxWidth(), enabled = errors.isEmpty())
        if (existing != null) PixelButton("Delete", { App.store.deleteTxn(existing.id); nav.back() }, Modifier.fillMaxWidth(), kind = Kind.DANGER)
    }

    catFor?.let { item ->
        SearchPicker("Item category", st.categories.map { it to it }, { catFor = null }) { item.category = it; catFor = null }
    }
}

/** Multi-select name chips. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun Toggles(options: List<Pair<String, String>>, selected: Set<String>, onToggle: (String) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (id, label) -> Chip((if (id in selected) "✓ " else "") + label, if (id in selected) Px.blue else Px.creamLight,
            Modifier.heightIn(min = 40.dp).clickable(role = Role.Checkbox) { onToggle(id) }) }
    }
}

@Composable
fun ScanningOverlay(status: String) {
    androidx.compose.foundation.layout.Box(
        Modifier.fillMaxSize()
            .background(Px.navy.copy(alpha = 0.6f))
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } }
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Window("Scanning.exe", Modifier.width(320.dp)) {
            Body(status)
            SteppedBar()
            Small("Photos stay on your phone. Only the text is read.")
        }
    }
}

/** Indeterminate loading bar that moves in hard steps. */
@Composable
fun SteppedBar() {
    val ctx = LocalContext.current
    val still = android.provider.Settings.Global.getFloat(ctx.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    var step by remember { mutableStateOf(if (still) 10 else 0) }
    LaunchedEffect(still) { while (!still) { kotlinx.coroutines.delay(160); step = (step + 1) % 21 } }
    PixelProgress(step / 20f, blocks = 20)
}
