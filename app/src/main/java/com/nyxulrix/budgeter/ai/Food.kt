package com.nyxulrix.budgeter.ai

import android.content.Context
import com.nyxulrix.budgeter.core.MACROS
import com.nyxulrix.budgeter.core.fmtMacro
import com.nyxulrix.budgeter.data.json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.math.roundToLong

/*
 * Macros for food you key in, from the same AI provider and key as receipt reading (each call is one request).
 * Look up gives a few likely matches per 100 g with a typical portion; Fill all totals a whole description.
 * Same as the web's ai/food.ts.
 */

/** One match: macros per 100 g, and one typical portion. */
data class FoodHit(val name: String, val per100: Map<String, Double>, val portion: Portion? = null)
data class Portion(val grams: Double, val label: String)

object Food {
    /** Units for how much was eaten, portion first. ml counts as grams (close enough for drinks). */
    val UNITS = listOf("portion" to "Portion", "g" to "Grams (g)", "ml" to "Millilitres (ml)", "oz" to "Ounces (oz)", "cup" to "Cups", "tbsp" to "Tablespoons", "tsp" to "Teaspoons")
    private val GRAMS = mapOf("g" to 1.0, "ml" to 1.0, "oz" to 28.35, "cup" to 240.0, "tbsp" to 15.0, "tsp" to 5.0)

    /** Grams in [amount] [unit] of [hit]; a portion with no size given counts as 100 g. */
    fun gramsOf(amount: Double, unit: String, hit: FoodHit) = amount * if (unit == "portion") hit.portion?.grams ?: 100.0 else GRAMS[unit] ?: 1.0

    private fun round1(v: Double) = (v * 10).roundToLong() / 10.0

    /** [per100] scaled to [grams], to one decimal. */
    fun scale(per100: Map<String, Double>, grams: Double) = per100.mapValues { (_, v) -> round1(v * grams / 100) }

    private fun num(o: JsonObject?, key: String) = (o?.get(key) as? JsonPrimitive)?.doubleOrNull?.takeIf { it >= 0 && it.isFinite() }

    /** Known macros in [o], rounded to one decimal. */
    private fun macrosIn(o: JsonObject?) = MACROS.mapNotNull { m -> num(o, m.key)?.let { m.key to round1(it) } }.toMap()

    /** The JSON object in an AI reply (models sometimes wrap it in prose or code fences). */
    private fun jsonIn(reply: String, p: Provider): JsonObject =
        runCatching { json.parseToJsonElement(reply.substring(reply.indexOf('{'), reply.lastIndexOf('}') + 1)).jsonObject }
            .getOrElse { throw Exception("${p.label} didn't return nutrition data. Try again.") }

    /** Reads the matches out of a Look up reply. */
    fun readHits(d: JsonObject): List<FoodHit> = runCatching { d["foods"]!!.jsonArray.toList() }.getOrDefault(emptyList()).mapNotNull { e ->
        val f = e as? JsonObject ?: return@mapNotNull null
        val per100 = macrosIn(f["per100g"] as? JsonObject)
        val name = (f["name"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        if (name.isEmpty() || "calories" !in per100) return@mapNotNull null
        val portion = num(f, "portion_grams")?.takeIf { it > 0 }?.let { g ->
            val label = (f["portion"] as? JsonPrimitive)?.contentOrNull?.trim()?.ifEmpty { null } ?: "1 portion"
            Portion(round1(g), "$label (${fmtMacro(round1(g))} g)")
        }
        FoodHit(name, per100, portion)
    }

    private const val LOOKUP = """You are a nutrition database. For the food the user names, list up to 5 likely matches: the plain version first,
then common variants (brands, regional or restaurant versions). Reply with JSON only, no prose, no code fences:
{"foods": [{"name": string, "portion": string like "1 scoop" or "1 cup" or "1 plate", "portion_grams": number,
 "per100g": {"calories": kcal, "protein": grams, "carbs": grams, "fat": grams, "fibre": grams, "sugar": grams, "sodium": milligrams}}]}
Values are per 100 g (per 100 ml for drinks). Use plain numbers."""

    /** Likely matches for [query], from the AI provider [p]. */
    suspend fun lookup(ctx: Context, p: Provider, query: String): List<FoodHit> = readHits(jsonIn(Ai.chat(ctx, p, LOOKUP, query.trim()), p))

    private const val SYSTEM = """You estimate nutrition for food someone ate. Reply with JSON only, no prose, no code fences:
{"calories": kcal, "protein": grams, "carbs": grams, "fat": grams, "fibre": grams, "sugar": grams, "sodium": milligrams}
Totals are for everything described, as eaten. If no amount is given, assume one typical serving. Use plain numbers."""

    /** Macros for a free-text description as a whole, from the AI provider [p]. */
    suspend fun aiMacros(ctx: Context, p: Provider, description: String): Map<String, Double> {
        val out = macrosIn(jsonIn(Ai.chat(ctx, p, SYSTEM, description), p))
        if (out.isEmpty()) throw Exception("${p.label} didn't return nutrition data. Try again.")
        return out
    }
}
