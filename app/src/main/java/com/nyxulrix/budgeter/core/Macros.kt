package com.nyxulrix.budgeter.core

import java.time.LocalDate
import kotlin.math.roundToLong

/** A nutrient you can track. [more] = more is better (protein, fibre); otherwise the target is a limit. */
data class Macro(val key: String, val label: String, val unit: String, val default: Double, val more: Boolean)

/** Same list, keys and defaults as the web app (src/core/macros.ts). */
val MACROS = listOf(
    Macro("calories", "Calories", "kcal", 2000.0, false),
    Macro("protein", "Protein", "g", 100.0, true),
    Macro("carbs", "Carbs", "g", 250.0, false),
    Macro("fat", "Fat", "g", 70.0, false),
    Macro("fibre", "Fibre", "g", 30.0, true),
    Macro("sugar", "Sugar", "g", 50.0, false),
    Macro("sodium", "Sodium", "mg", 2300.0, false),
)
val DEFAULT_TRACKED = listOf("calories", "protein")

fun macro(key: String) = MACROS.first { it.key == key }

/** 12.5 → "12.5", 1820.0 → "1,820". */
fun fmtMacro(v: Double): String =
    if (v == Math.floor(v) || v >= 100) "%,d".format(v.roundToLong()) else "%,.1f".format(v)

fun onTarget(key: String, value: Double, target: Double) = if (macro(key).more) value >= target else value <= target

/** Adds up [meals] (each a map of macro → amount) for one day. */
fun sumMacros(meals: List<Map<String, Double>>): Map<String, Double> =
    meals.flatMap { it.entries }.groupBy({ it.key }, { it.value }).mapValues { (_, v) -> v.sum() }

/** One macro over a report: the average per logged day and how many logged days hit the target. */
data class MacroStat(val key: String, val average: Double, val onTargetDays: Int)

/**
 * Report over the [days] days ending [end]: totals per day (days with nothing logged left out) and per macro
 * the average over logged days and the days on target. [byDay] maps an ISO day to that day's meals' macros.
 */
fun macroReport(byDay: Map<String, List<Map<String, Double>>>, keys: List<String>, targets: Map<String, Double>, end: LocalDate, days: Int):
    Pair<List<Pair<String, Map<String, Double>>>, List<MacroStat>> {
    val logged = (0 until days).map { end.minusDays(it.toLong()).toString() }
        .mapNotNull { d -> byDay[d]?.takeIf { it.isNotEmpty() }?.let { d to sumMacros(it) } }
    val stats = keys.map { k ->
        val t = targets[k] ?: macro(k).default
        MacroStat(k, if (logged.isEmpty()) 0.0 else logged.sumOf { it.second[k] ?: 0.0 } / logged.size,
            logged.count { onTarget(k, it.second[k] ?: 0.0, t) })
    }
    return logged to stats
}
