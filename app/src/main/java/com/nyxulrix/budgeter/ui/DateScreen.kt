package com.nyxulrix.budgeter.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.nyxulrix.budgeter.App
import com.nyxulrix.budgeter.core.Every
import com.nyxulrix.budgeter.core.Period
import com.nyxulrix.budgeter.core.Tx
import com.nyxulrix.budgeter.core.currentStep
import com.nyxulrix.budgeter.core.money
import com.nyxulrix.budgeter.core.overdrawn
import com.nyxulrix.budgeter.core.parseMoney
import com.nyxulrix.budgeter.core.plain
import com.nyxulrix.budgeter.core.totals
import com.nyxulrix.budgeter.core.withBudget
import com.nyxulrix.budgeter.data.overspendsFor
import com.nyxulrix.budgeter.data.AppState
import com.nyxulrix.budgeter.data.saveTxn
import com.nyxulrix.budgeter.data.dateContributions
import com.nyxulrix.budgeter.data.Txn
import com.nyxulrix.budgeter.data.DATE_TRANSFER
import com.nyxulrix.budgeter.data.Line
import com.nyxulrix.budgeter.data.LineKind
import com.nyxulrix.budgeter.data.currency
import com.nyxulrix.budgeter.data.editPlan
import com.nyxulrix.budgeter.data.periodOf
import com.nyxulrix.budgeter.data.planFor
import com.nyxulrix.budgeter.date.DateClient
import com.nyxulrix.budgeter.date.Phase
import com.nyxulrix.budgeter.date.TxDraft
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Budgeter's web OAuth client (project budgeter-510810); DateBudget's Firebase Auth accepts its ID tokens. */
private const val GOOGLE_CLIENT_ID = "1080294508933-ii65fgq3frcfnkg9smq55otiqlnn0f24.apps.googleusercontent.com"

private fun today() = LocalDate.now().toString()

/** The personal budget line that sets money aside for dates each month (a set-aside, carried over; tag "date"). */
fun AppState.dateLine(key: String = periodOf(LocalDate.now()).key): Line? = planFor(key).lines.firstOrNull { it.tag == "date" }

/** What the shared budget adds per month on average: weekly ×52/12, fortnightly ×26/12. */
private fun monthly(amount: Long, every: Every) = when (every) {
    Every.WEEK -> Math.round(amount * 52 / 12.0)
    Every.FORTNIGHT -> Math.round(amount * 26 / 12.0)
    Every.MONTH -> amount
}

/** Half the pool, rounded up: what's set aside in my budget until I choose another amount. */
private fun half(amount: Long) = (amount + 1) / 2

/** Sets [amount] aside for dates in my budget from this month on (replacing any earlier amount this month). Not a bank movement. */
private fun setPoolAside(amount: Long) {
    val key = App.store.value.periodOf(LocalDate.now()).key
    App.store.editPlan(key) { p ->
        p.copy(lines = p.lines.filter { it.tag != "date" } +
            if (amount > 0) listOf(Line(kind = LineKind.RESERVE, name = "Date budget", amount = amount, tag = "date", added = today())) else emptyList())
    }
}

/** Keeps the shared budget fresh while a tab showing it is open: the partner's changes show within seconds. */
@Composable
fun DateRefresh() {
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) {   // while the app is on screen
        owner.repeatOnLifecycle(Lifecycle.State.STARTED) { while (true) { DateClient.refresh(); delay(10_000) } }
    }
}

/** Each month stands alone: what was added in [p] minus what was spent in it. Nothing carries over between months. */
fun monthLeft(all: List<Tx>, p: com.nyxulrix.budgeter.core.Period) = totals(all.filter { it.date >= p.start.toString() && it.date < p.end.toString() }).left

/** The fund top-ups that covered expense [e]'s overspend ("Over budget: <note>", same day). */
fun coverTopups(all: List<Tx>, e: Tx): List<Tx> = all.filter { it.kind == "topup" && it.date == e.date && it.note == "Over budget: ${e.note}".take(100) }

/**
 * The cover top-up a date expense of [amount] on [date] needs: how far it takes that month's fund below zero. Months
 * don't carry over, so only its own month counts, as the fund shows it. When editing [old], its earlier cover top-ups
 * are left out, since saving replaces them: an edit that lowers, moves or renames it gets the right cover too.
 */
fun AppState.dateCover(all: List<Tx>, date: LocalDate, amount: Long, old: Tx? = null): Long {
    val p = periodOf(date)
    val replaced = old?.let { o -> coverTopups(all, o).map { it.id }.toSet() }.orEmpty()
    val rest = all.filter { it.id !in replaced }
    val inMonth = old != null && old.date >= p.start.toString() && old.date < p.end.toString()
    return overdrawn(monthLeft(rest, p), amount, if (inMonth) old!!.amount else 0)
}

/** A titled part of the Date budget fold. */
@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) =
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { Rule(); Label(title); content() }

/**
 * Budget tab: everything to set up the shared date budget (same data as the DateBudget app), for the month the tab
 * shows. Its expenses and top-ups are in Txns with everything else; date expenses are added like any expense, with
 * Count toward → Date budget.
 */
@Composable
fun DateBudgetFold(st: AppState, period: Period) {
    val ds by DateClient.state.collectAsState()
    val cur = st.currency
    DateRefresh()
    // A set-aside for dates in my budget: half the pool to start with (it used to be my share, as a fixed cost).
    // Once there is one, its amount is mine to set (Pool → Set aside), so the pool changing doesn't touch it.
    LaunchedEffect(ds.budget) {
        val b = ds.budget ?: return@LaunchedEffect
        val step = currentStep(b.plan, today()) ?: return@LaunchedEffect
        val line = App.store.value.dateLine()
        if (step.amount > 0 && (line == null || line.kind != LineKind.RESERVE)) setPoolAside(half(monthly(step.amount, step.every)))
    }
    FoldWindow(if (ds.phase == Phase.Ready) "Date budget · ${money(monthLeft(ds.all, period), cur)} left" else "Date budget", key = "Date budget") {
        ds.error?.let { Small("! $it", color = Px.red) }
        when (ds.phase) {
            Phase.SignedOut -> SignIn()
            Phase.Loading -> SteppedBar()
            Phase.NoBudget -> Connect()
            Phase.Ready -> {
                Fund(cur, period)
                PlanWindow(cur)
                Section("One-off top up") {
                    TxForm(cur, null, { DateClient.addTx(it) })
                    Small("Date spending is added like any expense: pick Date budget under Count toward.")
                }
            }
        }
    }
}

/** Who paid, as a colour: the budget's first member is blue, the second pink (the same on both partners' phones). */
fun payerColor(members: List<String>?, by: String) = when {
    by.isNotEmpty() && members?.getOrNull(0) == by -> Px.blue
    by.isNotEmpty() && members?.getOrNull(1) == by -> Px.pink
    else -> Px.orange
}

/** A date budget entry in the Txns list, marked with a heart in the payer's colour. Expenses open the expense editor; top-ups edit here. */
@Composable
fun DateTxnRow(st: AppState, tx: Tx) {
    val scope = rememberCoroutineScope()
    val nav = LocalNav.current
    val ds by DateClient.state.collectAsState()
    val cur = st.currency
    var editing by remember { mutableStateOf(false) }
    val expense = tx.kind == "expense"
    val who = ds.budget?.names?.get(tx.by) ?: if (tx.by.isNotEmpty()) "Partner" else "Shared"
    val charged = if (expense) st.overspendsFor(tx.id, tx.date, tx.note, ds.txs.filter { it.kind == "expense" }).sumOf { it.total } else 0
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClickLabel = "Edit") { if (expense) nav.go(Screen.Expense(dateId = tx.id)) else editing = true }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Glyph(Glyphs.heart, payerColor(ds.budget?.members, tx.by), 18.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Body(tx.note.ifEmpty { if (expense) "Date" else "Top up" })
            Small("${if (expense) "Date budget" else "Top up"} · $who" + if (charged > 0) " · ${money(charged, cur)} over, from your budget" else "")
        }
        Text((if (expense) "" else "+") + money(tx.amount, cur), style = Type.body, color = if (expense) Px.brown else Px.green)
    }
    if (editing) PixelDialog("Top up", { editing = false }) {
        TxForm(cur, tx, { DateClient.updateTx(tx.id, it) }) { editing = false }
        PixelButton("Delete", { editing = false; scope.launch { runCatching { DateClient.deleteTx(tx.id) } } }, Modifier.fillMaxWidth(), kind = Kind.DANGER)
    }
}

@Composable
private fun SignIn() {
    Section("Sign in") {
        Body("One shared fund for your dates, with your partner. It's the same budget as the DateBudget app, so either of you can use either app.")
        if (!DateClient.configured) Small("Date budget isn't set up in this build.")
        else SignInButton()
    }
}

/**
 * The one login: signs in to the date budget with the Google account Budgeter uses. [quiet] only picks an account
 * that has already signed in here (no UI, no errors); otherwise the account picker shows. Needs an Activity [ctx].
 * Returns an error message to show, or null.
 */
suspend fun dateGoogleSignIn(ctx: android.content.Context, quiet: Boolean): String? {
    if (!DateClient.configured || DateClient.state.value.phase != Phase.SignedOut) return null
    return try {
        val option = GetGoogleIdOption.Builder().setServerClientId(GOOGLE_CLIENT_ID)
            .setFilterByAuthorizedAccounts(quiet).setAutoSelectEnabled(quiet).build()
        val res = CredentialManager.create(ctx).getCredential(ctx, GetCredentialRequest.Builder().addCredentialOption(option).build())
        DateClient.signIn(GoogleIdTokenCredential.createFrom(res.credential.data).idToken)
        null
    } catch (_: GetCredentialCancellationException) {
        null   // closed the account picker: nothing to say
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        if (quiet) null else e.message ?: "Google sign-in failed."
    }
}

@Composable
private fun SignInButton() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var err by remember { mutableStateOf<String?>(null) }
    Small("This uses your Budgeter Google account: connecting Google sync signs you in here too. Or sign in just for this:")
    PixelButton("Sign in with Google", { err = null; scope.launch { err = dateGoogleSignIn(ctx, quiet = false) } }, Modifier.fillMaxWidth())
    err?.let { Small("! $it", color = Px.red) }
}

@Composable
private fun Connect() {
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    fun run(f: suspend () -> Unit) { err = null; scope.launch { runCatching { f() }.onFailure { if (it !is kotlinx.coroutines.CancellationException) err = it.message } } }
    Section("Start a budget") {
        Small("You'll get a code to give your partner.")
        PixelButton("Start a budget", { run { DateClient.create() } })
    }
    Section("Join your partner") {
        PixelField(code, { code = it.uppercase().take(8) }, "Code", placeholder = "ABCD2345")
        PixelButton("Join", { run { DateClient.join(code) } }, enabled = code.trim().length == 8)
        err?.let { Small("! $it", color = Px.red) }
    }
}

/** The fund for the month picked in the Budget tab: that month's top-ups and spending, and what carries over. */
/**
 * Who has put what into the pool this month: the date expenses each of us paid, with my partner's transfers to me
 * on their side. A transfer adds to "In the bank" (it's a negative expense), not to my budget.
 */
@Composable
private fun Contributions(cur: String, period: Period, partnerName: String) {
    val st by App.store.state.collectAsState()
    val ds by DateClient.state.collectAsState()
    val c = st.dateContributions(period, ds.txs, ds.uid ?: return)
    var text by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    val v = if (text.isBlank()) null else parseMoney(text, cur)
    Label("Put in this month")
    KeyValue("You", money(c.mine, cur))
    KeyValue(partnerName, money(c.theirs, cur))
    Small("You paid ${money(c.iPaid, cur)} of date spending, $partnerName ${money(c.theyPaid, cur)}" +
        if (c.transferred != 0L) ", and $partnerName transferred you ${money(c.transferred, cur)}." else ".")
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PixelField(text, { text = it }, "$partnerName transferred me", Modifier.weight(1f), keyboard = KeyboardType.Decimal, placeholder = "0.00",
            error = if (text.isNotBlank() && (v == null || v <= 0)) "Not an amount" else null)
        PixelButton("Add", {
            App.store.saveTxn(Txn(date = date.toString(), total = -v!!, category = "Other", merchant = "From $partnerName", note = DATE_TRANSFER))
            text = ""
        }, enabled = v != null && v > 0)
    }
    DateField("Transferred on", date, { date = it })
}

@Composable
private fun Fund(cur: String, period: Period) {
    val ctx = LocalContext.current
    val ds by DateClient.state.collectAsState()
    val b = ds.budget ?: return
    val partner = b.members.firstOrNull { it != ds.uid }
    var copied by remember { mutableStateOf(false) }
    val month = totals(ds.all.filter { it.date >= period.start.toString() && it.date < period.end.toString() })
    val left = month.left
    Section("Shared fund") {
        Label(if (period.end.isAfter(LocalDate.now())) "Left to spend this month" else "Left in ${period.key}")
        Text(money(left, cur), style = Type.hero, color = if (left < 0) Px.red else Px.brown)
        KeyValue("Added in ${period.key}", money(month.topped, cur))
        KeyValue("Spent in ${period.key}", money(month.spent, cur))
        if (partner != null) Contributions(cur, period, b.names[partner] ?: "Your partner")
        Rule()
        if (partner != null) Small("Shared with ${b.names[partner] ?: "your partner"}.")
        else {
            Small("Waiting for your partner. Give them this code to join (in Budgeter or DateBudget):")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(b.code, style = Type.number)
                PixelButton(if (copied) "Copied" else "Copy", {
                    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Date budget code", b.code))
                    copied = true
                }, kind = Kind.SECONDARY)
            }
        }
        if (cur != "SGD") Small("DateBudget counts in SGD; amounts here are shown in $cur without converting.", color = Px.red)
    }
}

/** The recurring pool for both of you, added every week, 2 weeks or month, and what I set aside for it in my budget each month. */
@Composable
private fun PlanWindow(cur: String) {
    val scope = rememberCoroutineScope()
    val ds by DateClient.state.collectAsState()
    val st by App.store.state.collectAsState()
    val b = ds.budget ?: return
    val step = currentStep(b.plan, today())
    val line = st.dateLine()?.takeIf { it.kind == LineKind.RESERVE }
    var amount by remember { mutableStateOf(step?.let { plain(it.amount, cur) } ?: "") }
    var every by remember { mutableStateOf(step?.every ?: Every.MONTH) }
    var day by remember { mutableStateOf((step?.day ?: 1).toString()) }
    var aside by remember { mutableStateOf(line?.let { plain(it.amount, cur) } ?: "") }
    var err by remember { mutableStateOf<String?>(null) }
    val v = if (amount.isBlank()) 0L else parseMoney(amount, cur)
    val a = if (aside.isBlank()) null else parseMoney(aside, cur)   // blank: half the pool
    val d = day.toIntOrNull()
    val ok = v != null && v >= 0 && (aside.isBlank() || (a != null && a >= 0)) && (every != Every.MONTH || (d != null && d in 1..31))
    Section("Pool") {
        Small(if (step != null && step.amount > 0)
            "${money(step.amount, cur)} is added every ${step.every.label}" + (if (step.every == Every.MONTH) " on day ${step.day ?: 1}" else " (on Monday)") +
                "; you set ${money(line?.amount ?: 0, cur)} aside in your budget each month."
        else "No recurring pool yet.")
        PixelField(amount, { amount = it }, "Pool ($cur)", keyboard = KeyboardType.Decimal, placeholder = "0.00", error = if (v == null) "Not a number" else null)
        Choice(Every.entries, every, { "Every ${it.label}" }, { every = it })
        if (every == Every.MONTH) PixelField(day, { s -> day = s.filter(Char::isDigit).take(2) }, "On day (1-31)", keyboard = KeyboardType.Number)
        PixelField(aside, { aside = it }, "Set aside in my budget each month ($cur)", keyboard = KeyboardType.Decimal,
            placeholder = plain(half(monthly(v ?: 0, every)), cur), error = if (aside.isNotBlank() && a == null) "Not a number" else null)
        PixelButton("Save", {
            err = null
            val e = every
            val keep = a ?: half(monthly(v!!, e))   // blank: half the pool
            scope.launch {
                runCatching {
                    DateClient.setPlan(withBudget(b.plan, today(), v!!, e, if (e == Every.MONTH) d!! else 1))
                    setPoolAside(keep)
                }.onFailure { if (it !is kotlinx.coroutines.CancellationException) err = it.message }
            }
        }, enabled = ok)
        Small("Starts with the current period; earlier ones keep what they had. Set it to 0 to stop. For one-off extras, add a top-up.")
        err?.let { Small("! $it", color = Px.red) }
    }
}

/** The amount of a top-up. New ones are dated today; edits keep their date and note. */
@Composable
private fun TxForm(cur: String, tx: Tx?, onSave: suspend (TxDraft) -> Unit, onDone: () -> Unit = {}) {
    val scope = rememberCoroutineScope()
    val kind = tx?.kind ?: "topup"   // fixed: new entries here are top-ups; edits keep theirs
    var amount by remember { mutableStateOf(tx?.let { plain(it.amount, cur) } ?: "") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val v = parseMoney(amount, cur)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PixelField(amount, { amount = it }, "Amount ($cur)", keyboard = KeyboardType.Decimal, placeholder = "0.00")
        PixelButton(if (tx != null) "Save" else "Add", {
            busy = true; err = null
            scope.launch {
                runCatching { onSave(TxDraft(kind, v!!, tx?.note ?: "", tx?.date ?: LocalDate.now().toString())) }
                    .onSuccess { if (tx == null) amount = ""; onDone() }
                    .onFailure { if (it !is kotlinx.coroutines.CancellationException) err = it.message }
                busy = false
            }
        }, Modifier.fillMaxWidth(), enabled = !busy && v != null && v > 0)
        err?.let { Small("! $it", color = Px.red) }
    }
}
