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
import androidx.compose.runtime.mutableStateMapOf
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
import com.nyxulrix.budgeter.ai.Ai
import com.nyxulrix.budgeter.ai.Food
import com.nyxulrix.budgeter.ai.FoodHit
import com.nyxulrix.budgeter.core.sumMacros
import com.nyxulrix.budgeter.data.FoodPreset
import com.nyxulrix.budgeter.data.deletePreset
import com.nyxulrix.budgeter.data.newId
import com.nyxulrix.budgeter.data.savePreset
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import com.nyxulrix.budgeter.core.MACROS
import com.nyxulrix.budgeter.core.fmtMacro
import com.nyxulrix.budgeter.core.macro
import com.nyxulrix.budgeter.core.macroReport
import com.nyxulrix.budgeter.core.money
import com.nyxulrix.budgeter.data.AppState
import com.nyxulrix.budgeter.data.Meal
import com.nyxulrix.budgeter.data.Txn
import com.nyxulrix.budgeter.data.currency
import com.nyxulrix.budgeter.data.deleteMeal
import com.nyxulrix.budgeter.data.editMacros
import com.nyxulrix.budgeter.data.macroTarget
import com.nyxulrix.budgeter.data.macrosOn
import com.nyxulrix.budgeter.data.mealsOn
import com.nyxulrix.budgeter.data.saveMeal
import com.nyxulrix.budgeter.data.unloggedFood
import java.time.LocalDate

private val DOW = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")

/** "650 kcal · 30 g protein" for the macros I track. */
private fun AppState.summary(m: Map<String, Double>) =
    macros.tracked.joinToString(" · ") { k -> "${fmtMacro(m[k] ?: 0.0)} ${macro(k).unit}" + if (k == "calories") "" else " ${macro(k).label.lowercase()}" }

/** What a Food expense was, as a meal name: its items, else the shop. */
private fun Txn.mealName() = items.joinToString(", ") { it.name }.ifBlank { merchant.ifBlank { "Meal" } }

/** Progress towards today's target for each tracked macro. Shared by the Macros tab and the Home card. */
@Composable
fun MacroBars(st: AppState, day: String) {
    val got = st.macrosOn(day)
    st.macros.tracked.forEach { k ->
        val m = macro(k)
        val v = got[k] ?: 0.0
        val t = st.macroTarget(k)
        val color = when { m.more -> if (v >= t) Px.green else Px.orange; v > t -> Px.red; else -> Px.orange }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label(m.label, Modifier.weight(1f))
                Text("${fmtMacro(v)} / ${fmtMacro(t)} ${m.unit}", style = Type.body, color = if (!m.more && v > t) Px.red else Px.brown)
            }
            PixelProgress(if (t > 0) (v / t).toFloat() else if (v > 0) 1f else 0f, color = color)
        }
    }
    if (st.macros.tracked.isEmpty()) Small("Pick what to track under Goals on the Macros tab.")
}

@Composable
fun MacrosScreen(st: AppState) {
    var day by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var editing by remember { mutableStateOf<Meal?>(null) }
    val date = LocalDate.parse(day)
    val cur = st.currency
    Page {
        Window("Macros") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PixelButton("<", { day = date.minusDays(1).toString() }, kind = Kind.SECONDARY)
                Text(if (date == LocalDate.now()) "TODAY" else "${DOW[date.dayOfWeek.value - 1]} $day", style = Type.number, modifier = Modifier.weight(1f))
                PixelButton(">", { day = date.plusDays(1).toString() }, kind = Kind.SECONDARY, enabled = date < LocalDate.now())
            }
            MacroBars(st, day)
        }

        Window("Meals") {
            val meals = st.mealsOn(day)
            val waiting = st.unloggedFood(day)
            meals.forEach { m -> Row2(m.name.ifBlank { "Meal" }, if (m.macros.isEmpty()) "Not counted" else st.summary(m.macros)) { editing = m } }
            waiting.forEach { t ->
                Row2(t.mealName(), "${money(t.myShare, cur)} on Food · tap to add what you ate", Px.orange) {
                    editing = Meal(date = t.date, name = t.mealName(), txnId = t.id)
                }
            }
            if (meals.isEmpty() && waiting.isEmpty()) Small("Nothing yet. Food expenses (meals, not groceries) show up here to fill in.")
            PixelButton("Add food", { editing = Meal(date = day, name = "") }, Modifier.fillMaxWidth(), glyph = Glyphs.plus)
            Small("For food you didn't pay for: free food, a treat, home cooking.")
        }

        ReportWindow(st)
        PresetsWindow(st)
        GoalsWindow(st)
    }
    editing?.let { MealDialog(st, it) { editing = null } }
}

@Composable
private fun Row2(title: String, sub: String, subColor: androidx.compose.ui.graphics.Color = Px.muted, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClickLabel = "Edit", onClick = onClick).padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Body(title)
        Small(sub, color = subColor)
    }
}

@Composable
private fun ReportWindow(st: AppState) {
    var days by rememberSaveable { mutableStateOf(7) }
    val byDay = st.meals.filter { it.macros.isNotEmpty() }.groupBy({ it.date }, { it.macros })
    val targets = st.macros.tracked.associateWith { st.macroTarget(it) }
    val (logged, stats) = macroReport(byDay, st.macros.tracked, targets, LocalDate.now(), days)
    Window("Report") {
        Choice(listOf(7, 30), days, { "$it days" }, { days = it })
        if (logged.isEmpty()) Small("Nothing logged in the last $days days.")
        else {
            stats.forEach { s ->
                val m = macro(s.key)
                KeyValue("${m.label} a day", "${fmtMacro(s.average)} / ${fmtMacro(targets.getValue(s.key))} ${m.unit}")
                Small("On target ${s.onTargetDays} of ${logged.size} logged days" + if (m.more) " (at least the target)" else " (at most the target)")
            }
            Rule()
            logged.forEach { (d, m) ->
                val ld = LocalDate.parse(d)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Label("${DOW[ld.dayOfWeek.value - 1]} ${d.drop(5)}", Modifier.weight(1f))
                    Small(st.summary(m))
                }
            }
        }
    }
}

@Composable
private fun GoalsWindow(st: AppState) {
    FoldWindow("Goals") {
        Label("Track")
        Toggles(MACROS.map { it.key to it.label }, st.macros.tracked.toSet()) { k ->
            App.store.editMacros { g -> g.copy(tracked = if (k in g.tracked) g.tracked - k else MACROS.map { it.key }.filter { it in g.tracked || it == k }) }
        }
        st.macros.tracked.forEach { k -> TargetField(st, k) }
        Small("Daily targets. Calories, carbs, fat, sugar and sodium are limits; protein and fibre are minimums.")
    }
}

@Composable
private fun TargetField(st: AppState, k: String) {
    val m = macro(k)
    var text by remember(k) { mutableStateOf(fmtMacro(st.macroTarget(k)).replace(",", "")) }
    val v = text.trim().toDoubleOrNull()
    PixelField(text, {
        text = it
        it.trim().toDoubleOrNull()?.takeIf { n -> n > 0 }?.let { n -> App.store.editMacros { g -> g.copy(targets = g.targets + (k to n)) } }
    }, "${m.label} (${m.unit} a day)", keyboard = KeyboardType.Decimal, error = if (v == null || v <= 0) "Enter a number above 0" else null)
}

/** "12.5" for a field: commas dropped. */
private fun plainNum(v: Double) = fmtMacro(v).replace(",", "")
private fun toText(m: Map<String, Double>) = m.mapValues { (_, v) -> plainNum(v) }
/** A typed amount: a number of at least 0, else null. */
private fun num(s: String?) = s?.trim()?.toDoubleOrNull()?.takeIf { it >= 0 && it.isFinite() }
/** The amounts typed in, as numbers (blank and invalid left out). */
private fun numbers(x: Map<String, String>) = x.mapNotNull { (k, v) -> num(v)?.let { k to it } }.toMap()

/**
 * One meal: name, day and macros. Macros can come from a preset, the free food database (each result adds on,
 * so a recipe is built ingredient by ingredient), the AI provider, or be typed in from a label.
 */
@Composable
fun MealDialog(st: AppState, meal: Meal, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val saved = st.meals.any { it.id == meal.id }
    var name by remember { mutableStateOf(meal.name) }
    var date by remember { mutableStateOf(LocalDate.parse(meal.date)) }
    val values = remember { mutableStateMapOf<String, String>().apply { putAll(toText(meal.macros)) } }
    var all by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var grams by remember { mutableStateOf("100") }
    var hits by remember { mutableStateOf<List<FoodHit>?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    val ai = remember { Ai.active(ctx) }
    val shown = if (all) MACROS.map { it.key } else st.macros.tracked
    val bad = values.filter { (_, v) -> v.isNotBlank() && num(v) == null }.keys
    val g = num(grams)
    fun run(what: String, f: suspend () -> Unit) {
        busy = what; err = null; note = null
        scope.launch {
            runCatching { f() }.onFailure { if (it !is kotlinx.coroutines.CancellationException) err = it.message }
            busy = null
        }
    }
    PixelDialog(if (saved) "Edit food" else "Add food", onDone) {
        if (st.presets.isNotEmpty()) {
            Label("Presets")
            Choice(st.presets.map { it.id }, null, { id -> st.presets.first { it.id == id }.name }, { id ->
                val p = st.presets.first { it.id == id }
                name = p.name; values.clear(); values.putAll(toText(p.macros))
            })
        }
        PixelField(name, { name = it.take(100) }, "What you ate", placeholder = "Chicken rice")
        DateField("Date", date, { date = it })
        shown.forEach { k ->
            val m = macro(k)
            PixelField(values[k].orEmpty(), { values[k] = it }, "${m.label} (${m.unit})", keyboard = KeyboardType.Decimal, placeholder = "0",
                error = if (k in bad) "Not a number" else null)
        }
        PixelButton(if (all) "Only what I track" else "All nutrients", { all = !all }, Modifier.fillMaxWidth(), kind = Kind.SECONDARY)
        Rule()
        Label("Look up (free food database)")
        PixelField(query, { query = it }, "", placeholder = name.trim().ifEmpty { "Whey protein, flat white, rice" })
        PixelField(grams, { grams = it }, "Grams eaten", keyboard = KeyboardType.Decimal, error = if (g == null || g <= 0) "Enter grams" else null)
        PixelButton(if (busy == "look") "Looking up…" else "Look up", {
            run("look") { hits = Food.lookup(query.trim().ifEmpty { name.trim() }) }
        }, Modifier.fillMaxWidth(), kind = Kind.SECONDARY, enabled = busy == null && (query.isNotBlank() || name.isNotBlank()))
        if (hits?.isEmpty() == true) Small("No matches. Try other words, or fill with AI.")
        hits?.forEach { h ->
            Column(
                Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(role = Role.Button, onClickLabel = "Add") {
                    val sum = sumMacros(listOf(numbers(values), Food.scale(h.per100, g ?: 100.0)))
                    values.clear(); values.putAll(toText(sum))
                    if (name.isBlank()) name = h.name.substringBefore(" · ")
                    note = "Added ${fmtMacro(g ?: 100.0)} g of ${h.name}. Tap more to add ingredients."
                }.padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Body(h.name)
                Small("per 100 g: ${st.summary(h.per100)}" + (h.serving?.let { " · serving ${fmtMacro(it)} g" } ?: ""))
            }
        }
        if (ai != null) PixelButton(if (busy == "ai") "Asking AI…" else "Fill with AI (${ai.label})", {
            run("ai") { val m = Food.aiMacros(ctx, ai, name.trim()); values.clear(); values.putAll(toText(m)); note = "Filled by AI: check the numbers." }
        }, Modifier.fillMaxWidth(), kind = Kind.SECONDARY, enabled = busy == null && name.isNotBlank())
        else Small("Set up AI in Profile to fill macros from a description.")
        Small("The lookup is free. AI uses your provider and may cost a little; put amounts in the name (e.g. \"2 eggs and toast\").")
        note?.let { Small(it, color = Px.green) }
        err?.let { Small("! $it", color = Px.red) }
        Rule()
        PixelButton("Save", {
            App.store.saveMeal(meal.copy(name = name.trim(), date = date.toString(), macros = numbers(values))); onDone()
        }, Modifier.fillMaxWidth(), enabled = bad.isEmpty() && name.isNotBlank())
        PixelButton("Save as preset", {
            val p = st.presets.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
            App.store.savePreset(FoodPreset(id = p?.id ?: newId(), name = name.trim(), macros = numbers(values)))
            note = "Saved \"${name.trim()}\" as a preset."
        }, Modifier.fillMaxWidth(), kind = Kind.SECONDARY, enabled = bad.isEmpty() && name.isNotBlank())
        if (!saved && meal.txnId != null) PixelButton("Not a meal for me", { App.store.saveMeal(meal.copy(name = name.trim().ifBlank { "Meal" }, macros = emptyMap())); onDone() },
            Modifier.fillMaxWidth(), kind = Kind.SECONDARY)
        if (saved) PixelButton("Delete", { App.store.deleteMeal(meal.id); onDone() }, Modifier.fillMaxWidth(), kind = Kind.DANGER)
    }
}

/** Presets I've saved, to remove ones I no longer use. */
@Composable
private fun PresetsWindow(st: AppState) {
    FoldWindow("Presets (${st.presets.size})") {
        if (st.presets.isEmpty()) Small("Save something you eat often (protein powder, morning coffee, your own recipe) with \"Save as preset\" when adding food.")
        st.presets.forEach { p ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) { Body(p.name); Small(st.summary(p.macros)) }
                CloseButton { App.store.deletePreset(p.id) }
            }
        }
    }
}

/** Home "Food" button: opens Add food for today. */
@Composable
fun AddFoodButton(st: AppState, modifier: Modifier = Modifier) {
    var adding by remember { mutableStateOf<Meal?>(null) }
    PixelButton("Food", { adding = Meal(date = LocalDate.now().toString(), name = "") }, modifier, glyph = Glyphs.food)
    adding?.let { MealDialog(st, it) { adding = null } }
}

/** Home card: today's macro progress. Tap to open the Macros tab. */
@Composable
fun MacrosCard(st: AppState) {
    val nav = LocalNav.current
    val today = LocalDate.now().toString()
    val waiting = st.unloggedFood(today).size
    Window("Macros today", Modifier.clickable(role = Role.Button, onClickLabel = "Open macros") { nav.tab = Tab.MACROS }) {
        MacroBars(st, today)
        if (waiting > 0) Small("$waiting meal${if (waiting > 1) "s" else ""} to fill in", color = Px.orange)
    }
}

