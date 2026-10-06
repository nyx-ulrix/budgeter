package com.nyxulrix.budgeter.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nyxulrix.budgeter.App
import com.nyxulrix.budgeter.R
import com.nyxulrix.budgeter.core.parseMoney
import com.nyxulrix.budgeter.data.Setup
import com.nyxulrix.budgeter.data.countries
import com.nyxulrix.budgeter.data.countryName
import com.nyxulrix.budgeter.data.currencyCodes
import com.nyxulrix.budgeter.data.currencyOf
import com.nyxulrix.budgeter.data.detectCountry
import com.nyxulrix.budgeter.data.editSetup
import com.nyxulrix.budgeter.data.finishSetup

/** First run when [existing] is null, otherwise the settings editor. */
@Composable
fun SetupScreen(existing: Setup?) {
    val ctx = LocalContext.current
    val first = existing == null
    val nav = if (first) null else LocalNav.current
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var country by rememberSaveable { mutableStateOf(existing?.country ?: detectCountry(ctx)) }
    var currency by rememberSaveable { mutableStateOf(existing?.currency ?: currencyOf(country)) }
    var startDay by rememberSaveable { mutableStateOf((existing?.startDay ?: 1).toString()) }
    var income by rememberSaveable { mutableStateOf("") }
    var balance by rememberSaveable { mutableStateOf("") }
    var pickCountry by remember { mutableStateOf(false) }
    var pickCurrency by remember { mutableStateOf(false) }
    val day = startDay.toIntOrNull()
    val dayOk = day != null && day in 1..28
    val incomeMinor = if (income.isBlank()) 0L else parseMoney(income, currency)
    val balanceMinor = if (balance.isBlank()) 0L else parseMoney(balance, currency)
    val ok = dayOk && incomeMinor != null && balanceMinor != null

    Page {
        if (first) {
            Window("Welcome.exe") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Art(artId("hero_welcome", R.drawable.char_princess_face), 96.dp, description = "Pixel-art guide character")
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("HELLO!", style = Type.hero)
                        Body("Let's set up your budget. You can change all of this later in Profile.")
                    }
                }
            }
        } else PageHeader("Settings")

        Window("You") {
            PixelField(name, { name = it }, "Name (optional)")
        }
        Window("Where you are") {
            Label("Country")
            PickerBox(countryName(country) + " ($country)") { pickCountry = true }
            Label("Currency")
            PickerBox(currency) { pickCurrency = true }
            Small("Currency follows your country. Change it if you budget in another one.")
        }
        Window("Budget month") {
            PixelField(startDay, { startDay = it.filter(Char::isDigit).take(2) }, "Month starts on day (1-28)",
                keyboard = KeyboardType.Number, error = if (dayOk) null else "Pick a day from 1 to 28")
            Small("Use your payday if you're paid mid-month.")
            if (first) {
                PixelField(income, { income = it }, "Monthly income or allowance ($currency)", keyboard = KeyboardType.Decimal,
                    placeholder = "0.00", error = if (incomeMinor == null) "Not a number" else null)
                PixelField(balance, { balance = it }, "Money you have now (optional)", keyboard = KeyboardType.Decimal,
                    placeholder = "0.00", error = if (balanceMinor == null) "Not a number" else null)
                Small("Your starting money is added to this month as extra money.")
            }
        }
        PixelButton(if (first) "Start budgeting" else "Save", {
            val s = Setup(country, currency, day!!, name.trim())
            if (first) App.store.finishSetup(s, incomeMinor!!, balanceMinor!!) else { App.store.editSetup(s); nav?.back() }
        }, Modifier.fillMaxWidth(), enabled = ok)
    }

    if (pickCountry) SearchPicker("Country", countries.map { it.code to "${it.name} (${it.code})" }, { pickCountry = false }) {
        country = it; currency = currencyOf(it); pickCountry = false
    }
    if (pickCurrency) SearchPicker("Currency", currencyCodes.map { it to it }, { pickCurrency = false }) {
        currency = it; pickCurrency = false
    }
}

/** A field-looking box that opens a picker. */
@Composable
fun PickerBox(text: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().frame().heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 8.dp, vertical = 10.dp),
    ) { Body("$text  ▾") }
}

/** Searchable list in a dialog. [options] are (value, label). */
@Composable
fun SearchPicker(title: String, options: List<Pair<String, String>>, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var q by remember { mutableStateOf("") }
    PixelDialog(title, onDismiss) {
        PixelField(q, { q = it }, "", placeholder = "Search")
        options.filter { q.isBlank() || it.second.contains(q, ignoreCase = true) }.take(60).forEach { (value, label) ->
            Box(
                Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable { onPick(value) }.padding(horizontal = 4.dp, vertical = 8.dp),
            ) { Body(label) }
        }
    }
}
