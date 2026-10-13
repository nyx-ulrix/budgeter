package com.nyxulrix.budgeter.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.nyxulrix.budgeter.core.Tx
import com.nyxulrix.budgeter.core.currentStep
import com.nyxulrix.budgeter.core.money
import com.nyxulrix.budgeter.core.parseMoney
import com.nyxulrix.budgeter.core.plain
import com.nyxulrix.budgeter.core.withBudget
import com.nyxulrix.budgeter.data.AppState
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

/** The personal budget line that sets aside my share of the date budget each month (a fixed cost, carried over). */
fun AppState.dateLine(key: String = periodOf(LocalDate.now()).key): Line? = planFor(key).lines.firstOrNull { it.tag == "date" }

/** What the shared budget adds per month on average: weekly ×52/12, fortnightly ×26/12. */
private fun monthly(amount: Long, every: Every) = when (every) {
    Every.WEEK -> Math.round(amount * 52 / 12.0)
    Every.FORTNIGHT -> Math.round(amount * 26 / 12.0)
    Every.MONTH -> amount
}

/** Sets my monthly share as a "Date budget" fixed cost from this month on (replacing any earlier amount this month). */
private fun setMyShare(amount: Long) {
    val key = App.store.value.periodOf(LocalDate.now()).key
    App.store.editPlan(key) { p ->
        p.copy(lines = p.lines.filter { it.tag != "date" } +
            if (amount > 0) listOf(Line(kind = LineKind.FIXED, name = "Date budget (my share)", amount = amount, tag = "date", added = today())) else emptyList())
    }
}

@Composable
fun DateScreen(st: AppState) {
    val ds by DateClient.state.collectAsState()
    val cur = st.currency
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) {   // while the app is on screen; the partner's changes show within seconds
        owner.repeatOnLifecycle(Lifecycle.State.STARTED) { while (true) { DateClient.refresh(); delay(10_000) } }
    }
    Page {
        PageHeader("Date budget")
        ds.error?.let { Small("! $it", color = Px.red) }
        when (ds.phase) {
            Phase.SignedOut -> SignIn()
            Phase.Loading -> SteppedBar()
            Phase.NoBudget -> Connect()
            Phase.Ready -> {
                Fund(cur)
                PlanWindow(cur)
                ShareWindow(st, cur)
                Window("Add") { TxForm(cur, null, { DateClient.addTx(it) }) }
                History(cur)
            }
        }
        if (ds.phase != Phase.SignedOut) PixelButton("Sign out of the date budget", { DateClient.signOut() }, Modifier.fillMaxWidth(), kind = Kind.SECONDARY)
    }
}

@Composable
private fun SignIn() {
    Window("Date budget") {
        Body("One shared fund for your dates, with your partner. It's the same budget as the DateBudget app, so either of you can use either app.")
        if (!DateClient.configured) Small("Date budget isn't set up in this build.")
        else SignInButton()
    }
}

@Composable
private fun SignInButton() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var err by remember { mutableStateOf<String?>(null) }
    Small("Sign in with the Google account you use for DateBudget.")
    PixelButton("Sign in with Google", {
        err = null
        scope.launch {
            try {
                val option = GetGoogleIdOption.Builder().setServerClientId(GOOGLE_CLIENT_ID)
                    .setFilterByAuthorizedAccounts(false).setAutoSelectEnabled(false).build()
                val res = CredentialManager.create(ctx).getCredential(ctx, GetCredentialRequest.Builder().addCredentialOption(option).build())
                DateClient.signIn(GoogleIdTokenCredential.createFrom(res.credential.data).idToken)
            } catch (_: GetCredentialCancellationException) {
                // Closed the account picker: nothing to say.
            } catch (e: Exception) {
                err = e.message ?: "Google sign-in failed."
            }
        }
    }, Modifier.fillMaxWidth())
    err?.let { Small("! $it", color = Px.red) }
}

@Composable
private fun Connect() {
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    fun run(f: suspend () -> Unit) { err = null; scope.launch { runCatching { f() }.onFailure { err = it.message } } }
    Window("Start a budget") {
        Small("You'll get a code to give your partner.")
        PixelButton("Start a budget", { run { DateClient.create() } })
    }
    Window("Join your partner") {
        PixelField(code, { code = it.uppercase().take(8) }, "Code", placeholder = "ABCD2345")
        PixelButton("Join", { run { DateClient.join(code) } }, enabled = code.trim().length == 8)
        err?.let { Small("! $it", color = Px.red) }
    }
}

@Composable
private fun Fund(cur: String) {
    val ctx = LocalContext.current
    val ds by DateClient.state.collectAsState()
    val b = ds.budget ?: return
    val partner = b.members.firstOrNull { it != ds.uid }
    var copied by remember { mutableStateOf(false) }
    Window("Date fund.exe") {
        Label("Left to spend")
        Text(money(ds.left, cur), style = Type.hero, color = if (ds.left < 0) Px.red else Px.brown)
        KeyValue("Added", money(ds.topped, cur))
        KeyValue("Spent", money(ds.spent, cur))
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

@Composable
private fun PlanWindow(cur: String) {
    val scope = rememberCoroutineScope()
    val ds by DateClient.state.collectAsState()
    val plan = ds.budget?.plan.orEmpty()
    val step = currentStep(plan, today())
    var amount by remember { mutableStateOf(step?.let { plain(it.amount, cur) } ?: "") }
    var every by remember { mutableStateOf(step?.every ?: Every.MONTH) }
    var day by remember { mutableStateOf((step?.day ?: 1).toString()) }
    var err by remember { mutableStateOf<String?>(null) }
    val v = if (amount.isBlank()) 0L else parseMoney(amount, cur)
    val d = day.toIntOrNull()
    val ok = v != null && v >= 0 && (every != Every.MONTH || (d != null && d in 1..31))
    Window("Budget") {
        Small(if (step != null && step.amount > 0)
            "${money(step.amount, cur)} is added every ${step.every.label}" + (if (step.every == Every.MONTH) " on day ${step.day ?: 1}." else " (on Monday).")
        else "No recurring budget yet.")
        PixelField(amount, { amount = it }, "Add ($cur)", keyboard = KeyboardType.Decimal, placeholder = "0.00", error = if (v == null) "Not a number" else null)
        Choice(Every.entries, every, { "Every ${it.label}" }, { every = it })
        if (every == Every.MONTH) PixelField(day, { s -> day = s.filter(Char::isDigit).take(2) }, "On day (1-31)", keyboard = KeyboardType.Number)
        PixelButton("Save", {
            err = null
            scope.launch {
                runCatching { DateClient.setPlan(withBudget(plan, today(), v!!, every, if (every == Every.MONTH) d!! else 1)) }.onFailure { err = it.message }
            }
        }, enabled = ok)
        Small("Starts with the current period; earlier ones keep what they had. Set it to 0 to stop. For one-off extras, add a top-up.")
        err?.let { Small("! $it", color = Px.red) }
    }
}

@Composable
private fun ShareWindow(st: AppState, cur: String) {
    val ds by DateClient.state.collectAsState()
    val step = currentStep(ds.budget?.plan.orEmpty(), today())
    val line = st.dateLine()
    val suggested = step?.let { Math.round(monthly(it.amount, it.every) / 2.0) } ?: 0L
    var text by remember { mutableStateOf(line?.let { plain(it.amount, cur) } ?: if (suggested > 0) plain(suggested, cur) else "") }
    val v = if (text.isBlank()) 0L else parseMoney(text, cur)
    Window("My share") {
        Body(if (line != null) "${money(line.amount, cur)} a month is set aside from your budget for dates." else "Set aside your share each month from your own budget.")
        PixelField(text, { text = it }, "Each month ($cur)", keyboard = KeyboardType.Decimal, placeholder = "0.00", error = if (v == null) "Not a number" else null)
        if (suggested > 0) Small("Half of the shared budget is about ${money(suggested, cur)} a month.")
        PixelButton("Set", { setMyShare(v!!) }, kind = Kind.SECONDARY, enabled = v != null && v != (line?.amount ?: 0L))
        Small("It's a fixed cost in your budget from this month on, so your daily budget already allows for it. Date spending then comes out of the shared fund only: tag an expense \"Date budget\" under Count toward.")
    }
}

/** Expense / top-up form, for adding and for the edit dialog. */
@Composable
private fun TxForm(cur: String, tx: Tx?, onSave: suspend (TxDraft) -> Unit, onDone: () -> Unit = {}) {
    val scope = rememberCoroutineScope()
    var kind by remember { mutableStateOf(tx?.kind ?: "expense") }
    var amount by remember { mutableStateOf(tx?.let { plain(it.amount, cur) } ?: "") }
    var note by remember { mutableStateOf(tx?.note ?: "") }
    var date by remember { mutableStateOf(tx?.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val v = parseMoney(amount, cur)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Choice(listOf("expense", "topup"), kind, { if (it == "expense") "Expense" else "Top up" }, { kind = it })
        PixelField(amount, { amount = it }, "Amount ($cur)", keyboard = KeyboardType.Decimal, placeholder = "0.00")
        PixelField(note, { note = it.take(100) }, "What for", placeholder = if (kind == "expense") "Dinner, movie…" else "Top up")
        DateField("Date", date, { date = it })
        PixelButton(if (tx != null) "Save" else "Add", {
            busy = true; err = null
            scope.launch {
                runCatching { onSave(TxDraft(kind, v!!, note.trim(), date.toString())) }
                    .onSuccess { if (tx == null) { amount = ""; note = "" }; onDone() }
                    .onFailure { err = it.message }
                busy = false
            }
        }, Modifier.fillMaxWidth(), enabled = !busy && v != null && v > 0)
        err?.let { Small("! $it", color = Px.red) }
    }
}

@Composable
private fun History(cur: String) {
    val scope = rememberCoroutineScope()
    val ds by DateClient.state.collectAsState()
    var editing by remember { mutableStateOf<Tx?>(null) }
    var deleting by remember { mutableStateOf<Tx?>(null) }
    Window("History") {
        if (ds.all.isEmpty()) Small("Nothing yet. Add your first date.")
        ds.all.forEach { t ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Body(t.note.ifEmpty { if (t.kind == "expense") "Expense" else "Top up" })
                        Small(t.date + if (t.auto) "" else " · ${ds.budget?.names?.get(t.by) ?: "Partner"}")
                    }
                    Text((if (t.kind == "expense") "−" else "+") + money(t.amount, cur), style = Type.body, color = if (t.kind == "expense") Px.brown else Px.green)
                }
                if (!t.auto) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Small("Edit", Modifier.heightIn(min = 32.dp).clickable(role = Role.Button) { editing = t }.padding(4.dp), color = Px.blue)
                    Small("Delete", Modifier.heightIn(min = 32.dp).clickable(role = Role.Button) { deleting = t }.padding(4.dp), color = Px.red)
                }
                Rule()
            }
        }
    }
    editing?.let { t ->
        PixelDialog("Edit", { editing = null }) { TxForm(cur, t, { DateClient.updateTx(t.id, it) }) { editing = null } }
    }
    deleting?.let { t ->
        PixelDialog("Delete", { deleting = null }) {
            Body("Delete ${money(t.amount, cur)} ${t.note.ifEmpty { t.kind }}?")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PixelButton("Delete", { deleting = null; scope.launch { runCatching { DateClient.deleteTx(t.id) } } }, kind = Kind.DANGER)
                PixelButton("Cancel", { deleting = null }, kind = Kind.SECONDARY)
            }
        }
    }
}
