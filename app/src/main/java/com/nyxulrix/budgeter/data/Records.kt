package com.nyxulrix.budgeter.data

import kotlinx.serialization.Serializable

/*
 * Two-way sync between the Android and web apps, through a hidden "_sync" tab in the shared Google Sheet.
 * The state is split into records, each with the time it last changed ("stamp"). On each sync both sides keep
 * whichever copy of a record is newer. A deleted record keeps its stamp with no data, so the delete wins too.
 * Keys: setup, categories, macros, plan:<period>, txn:<id>, planned:<id>, trip:<id>, meal:<id>, preset:<id>. The web app (budgeter-web,
 * src/core/records.ts) uses exactly the same keys and JSON.
 * ponytail: last writer wins per record by wall clock; two devices editing the same record within one sync
 * interval keep only the later edit.
 */

@Serializable
data class Categories(val categories: List<String>, val monthlyCategories: List<String>)

/** One record as stored in the sheet: [json] null means deleted. */
data class Remote(val stamp: Long, val json: String?)

/** Every synced record by key, as objects (cheap to compare). Sync settings stay local. */
fun AppState.recordObjects(): Map<String, Any> = buildMap {
    setup?.let { put("setup", it) }
    put("categories", Categories(categories, monthlyCategories.toList()))
    plans.forEach { (k, p) -> put("plan:$k", p) }
    txns.forEach { put("txn:${it.id}", it) }
    planned.forEach { put("planned:${it.id}", it) }
    trips.forEach { put("trip:${it.id}", it) }
    put("macros", macros)
    meals.forEach { put("meal:${it.id}", it) }
    presets.forEach { put("preset:${it.id}", it) }
}

fun encodeRecord(o: Any): String = when (o) {
    is Setup -> json.encodeToString(Setup.serializer(), o)
    is Categories -> json.encodeToString(Categories.serializer(), o)
    is Plan -> json.encodeToString(Plan.serializer(), o)
    is Txn -> json.encodeToString(Txn.serializer(), o)
    is Planned -> json.encodeToString(Planned.serializer(), o)
    is Trip -> json.encodeToString(Trip.serializer(), o)
    is MacroGoals -> json.encodeToString(MacroGoals.serializer(), o)
    is Meal -> json.encodeToString(Meal.serializer(), o)
    is FoodPreset -> json.encodeToString(FoodPreset.serializer(), o)
    else -> error("Not a record: $o")
}

/** Records that differ between [old] and this state get [now] as their stamp (added, changed or removed). */
fun AppState.stampedSince(old: AppState, now: Long): AppState {
    val a = old.recordObjects()
    val b = recordObjects()
    val changed = (a.keys + b.keys).filter { a[it] != b[it] }
    return if (changed.isEmpty()) this else copy(stamps = stamps + changed.associateWith { now })
}

/** This state with one record set from its JSON, or removed when [json] is null. Unknown keys are ignored. */
fun AppState.withRecord(key: String, json: String?): AppState {
    val id = key.substringAfter(':')
    fun <T> List<T>.put(idOf: (T) -> String, v: T?) = filterNot { idOf(it) == id } + listOfNotNull(v)
    return when (key.substringBefore(':')) {
        "setup" -> copy(setup = json?.let { com.nyxulrix.budgeter.data.json.decodeFromString(Setup.serializer(), it) } ?: setup)
        "categories" -> json?.let { com.nyxulrix.budgeter.data.json.decodeFromString(Categories.serializer(), it) }
            ?.let { copy(categories = it.categories, monthlyCategories = it.monthlyCategories.toSet()) } ?: this
        "plan" -> copy(plans = if (json == null) plans - id else plans + (id to com.nyxulrix.budgeter.data.json.decodeFromString(Plan.serializer(), json)))
        "txn" -> copy(txns = txns.put({ it.id }, json?.let { com.nyxulrix.budgeter.data.json.decodeFromString(Txn.serializer(), it) }))
        "planned" -> copy(planned = planned.put({ it.id }, json?.let { com.nyxulrix.budgeter.data.json.decodeFromString(Planned.serializer(), it) }))
        "trip" -> copy(trips = trips.put({ it.id }, json?.let { com.nyxulrix.budgeter.data.json.decodeFromString(Trip.serializer(), it) }))
        "macros" -> json?.let { copy(macros = com.nyxulrix.budgeter.data.json.decodeFromString(MacroGoals.serializer(), it)) } ?: this
        "meal" -> copy(meals = meals.put({ it.id }, json?.let { com.nyxulrix.budgeter.data.json.decodeFromString(Meal.serializer(), it) }))
        "preset" -> copy(presets = presets.put({ it.id }, json?.let { com.nyxulrix.budgeter.data.json.decodeFromString(FoodPreset.serializer(), it) }))
        else -> this
    }
}

/** Applies every remote record newer than ours. Records we have but never stamped count as stamp 1. */
fun AppState.mergeRemote(remote: Map<String, Remote>): AppState {
    var s = this
    val st = stamps.toMutableMap()
    for ((k, r) in remote) if (r.stamp > (stamps[k] ?: 0)) { s = s.withRecord(k, r.json); st[k] = r.stamp }
    return s.copy(stamps = st)
}

/** Our records (and deletions) newer than the sheet's copy, ready to write. */
fun AppState.toUpload(remote: Map<String, Remote>): Map<String, Remote> {
    val objs = recordObjects()
    return (stamps.keys + objs.keys).mapNotNull { k ->
        val stamp = stamps[k] ?: 1
        if (stamp > (remote[k]?.stamp ?: 0)) k to Remote(stamp, objs[k]?.let(::encodeRecord)) else null
    }.toMap()
}
