package com.nyxulrix.budgeter.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URL
import java.time.LocalDate

/** Daily exchange rates from open.er-api.com (free, no key). Only currency codes are sent. Always user-editable. */
object Rates {
    private val cache = mutableMapOf<String, Pair<LocalDate, Map<String, Double>>>()

    /** Home units per 1 unit of [from], or null when offline or unknown. */
    suspend fun rate(from: String, to: String): Double? = withContext(Dispatchers.IO) {
        if (from == to) return@withContext 1.0
        val today = LocalDate.now()
        val table = cache[from]?.takeIf { it.first == today }?.second ?: runCatching {
            val o = json.parseToJsonElement(URL("https://open.er-api.com/v6/latest/$from").readText()).jsonObject
            o["rates"]!!.jsonObject.mapValues { it.value.jsonPrimitive.double }.also { cache[from] = today to it }
        }.getOrNull()
        table?.get(to)
    }
}
