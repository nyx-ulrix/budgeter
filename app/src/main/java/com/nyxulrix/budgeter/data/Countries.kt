package com.nyxulrix.budgeter.data

import android.content.Context
import android.telephony.TelephonyManager
import java.util.Currency
import java.util.Locale

data class Country(val code: String, val name: String)

val countries: List<Country> by lazy {
    Locale.getISOCountries().map { Country(it, Locale("", it).displayCountry) }.sortedBy { it.name }
}

fun countryName(code: String): String = Locale("", code).displayCountry.ifEmpty { code }

/** The country the phone is in (mobile network), else its SIM, else the phone's language region. No permission needed. */
fun detectCountry(ctx: Context): String {
    val tm = ctx.getSystemService(TelephonyManager::class.java)
    return listOf(tm?.networkCountryIso, tm?.simCountryIso, Locale.getDefault().country)
        .firstOrNull { !it.isNullOrBlank() && it.length == 2 }?.uppercase() ?: "US"
}

fun currencyOf(country: String): String =
    runCatching { Currency.getInstance(Locale("", country)).currencyCode }.getOrNull() ?: "USD"

val currencyCodes: List<String> by lazy { Currency.getAvailableCurrencies().map { it.currencyCode }.sorted() }
