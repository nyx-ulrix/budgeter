package com.nyxulrix.budgeter.ai

import android.content.Context
import com.nyxulrix.budgeter.core.MACROS
import com.nyxulrix.budgeter.data.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.roundToLong

/*
 * Macros for food you key in. The free USDA FoodData Central database comes first (generic foods like coffee or
 * rice, and branded products like protein powders); the AI provider is the paid fallback. Same as the web's ai/food.ts.
 * ponytail: the shared DEMO_KEY allows about 30 lookups an hour per network; a personal key from api.data.gov
 * lifts that if it ever bites.
 */

/** One database match: macros per 100 g, and a serving size in grams when the label gives one. */
data class FoodHit(val name: String, val per100: Map<String, Double>, val serving: Double? = null)

object Food {
    private const val FDC = "https://api.nal.usda.gov/fdc/v1/foods/search"
    private const val FDC_KEY = "DEMO_KEY"
    /** USDA nutrient ids per macro (energy: kcal, Atwater general, Atwater specific). Sodium comes in mg. */
    private val IDS = mapOf("calories" to listOf(1008, 2047, 2048), "protein" to listOf(1003), "carbs" to listOf(1005),
        "fat" to listOf(1004), "fibre" to listOf(1079), "sugar" to listOf(2000, 1063), "sodium" to listOf(1093))

    private fun round1(v: Double) = (v * 10).roundToLong() / 10.0

    /** [per100] scaled to [grams], to one decimal. */
    fun scale(per100: Map<String, Double>, grams: Double) = per100.mapValues { (_, v) -> round1(v * grams / 100) }

    /** Reads one search result. */
    fun readHit(f: JsonObject): FoodHit? {
        val nutrients = f["foodNutrients"]?.jsonArray.orEmpty().map { it.jsonObject }
        val per100 = IDS.mapNotNull { (k, ids) ->
            nutrients.firstOrNull { n ->
                n["nutrientId"]?.jsonPrimitive?.intOrNull in ids && (k != "calories" || n["unitName"]?.jsonPrimitive?.contentOrNull?.uppercase() == "KCAL")
            }?.get("value")?.jsonPrimitive?.doubleOrNull?.let { k to it }
        }.toMap()
        if ("calories" !in per100) return null
        val desc = f["description"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
        val brand = (f["brandName"] ?: f["brandOwner"])?.jsonPrimitive?.contentOrNull?.ifBlank { null }
        val name = if (desc.any { it.isLowerCase() }) desc else desc.take(1) + desc.drop(1).lowercase()
        val serving = f["servingSize"]?.jsonPrimitive?.doubleOrNull
            ?.takeIf { it > 0 && f["servingSizeUnit"]?.jsonPrimitive?.contentOrNull?.lowercase() == "g" }?.let(::round1)
        return FoodHit(name + (brand?.let { " · $it" } ?: ""), per100, serving)
    }

    /** Searches the food database. */
    suspend fun lookup(query: String): List<FoodHit> = withContext(Dispatchers.IO) {
        val c = URL("$FDC?api_key=$FDC_KEY&pageSize=10&query=" + URLEncoder.encode(query.trim(), "UTF-8")).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 15_000; c.readTimeout = 30_000
            val code = c.responseCode
            if (code == 429) throw Exception("The food database has had too many lookups from this network this hour. Try later, or fill with AI.")
            if (code !in 200..299) throw Exception("Food lookup failed ($code).")
            val body = c.inputStream.bufferedReader().use { it.readText() }
            json.parseToJsonElement(body).jsonObject["foods"]?.jsonArray.orEmpty().mapNotNull { readHit(it.jsonObject) }
        } catch (e: java.io.IOException) {
            throw Exception("Couldn't reach the food database. Check your connection.")
        } finally {
            c.disconnect()
        }
    }

    private const val SYSTEM = """You estimate nutrition for food someone ate. Reply with JSON only, no prose, no code fences:
{"calories": kcal, "protein": grams, "carbs": grams, "fat": grams, "fibre": grams, "sugar": grams, "sodium": milligrams}
Totals are for everything described, as eaten. If no amount is given, assume one typical serving. Use plain numbers."""

    /** Macros for a free-text description, from the AI provider [p] (costs a request). */
    suspend fun aiMacros(ctx: Context, p: Provider, description: String): Map<String, Double> {
        val reply = Ai.chat(ctx, p, SYSTEM, description)
        val o = runCatching { json.parseToJsonElement(reply.substring(reply.indexOf('{'), reply.lastIndexOf('}') + 1)).jsonObject }
            .getOrElse { throw Exception("${p.label} didn't return nutrition data. Try again.") }
        val out = MACROS.mapNotNull { m -> o[m.key]?.jsonPrimitive?.doubleOrNull?.takeIf { it >= 0 && it.isFinite() }?.let { m.key to round1(it) } }.toMap()
        if (out.isEmpty()) throw Exception("${p.label} didn't return nutrition data. Try again.")
        return out
    }
}
