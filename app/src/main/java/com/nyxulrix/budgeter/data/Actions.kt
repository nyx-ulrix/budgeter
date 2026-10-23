package com.nyxulrix.budgeter.data

import java.time.LocalDate

/** Every state change the UI can make. Each one is a single [Store.update]. */

fun Store.finishSetup(s: Setup, income: Long, startingBalance: Long) = update { st ->
    val key = st.copy(setup = s).periodOf(LocalDate.now()).key
    val lines = if (startingBalance > 0) listOf(Line(kind = LineKind.EXTRA, name = "Starting balance", amount = startingBalance)) else emptyList()
    st.copy(setup = s, plans = st.plans + (key to (st.plans[key] ?: Plan(income, lines))))
}

fun Store.editSetup(s: Setup) = update { it.copy(setup = s) }

/** Edits the plan for [key], materialising the inherited plan first. */
fun Store.editPlan(key: String, f: (Plan) -> Plan) = update { st -> st.copy(plans = st.plans + (key to f(st.planFor(key)))) }

/** Adds a line dated today, so it changes spendable money from today on, not for days already past. */
fun Store.addLine(key: String, line: Line) = editPlan(key) { it.copy(lines = it.lines + line.copy(added = line.added ?: LocalDate.now().toString())) }
/** Removes a line. A reservation already spent by "Buy now" stays: its purchase counts on it. */
fun Store.removeLine(key: String, id: String) = update { st ->
    st.copy(plans = st.plans + (key to st.planFor(key).let { p -> p.copy(lines = p.lines.filterNot { it.id == id && !st.isSpentReserve(it) }) }))
}

fun Store.saveTxn(t: Txn) = update { it.withTxn(t, System.currentTimeMillis()) }

/**
 * Saves [t]. A "Buy now" expense edited below what was reserved for it frees the extra reservation
 * (newest first), so that money isn't left neither spent nor free.
 */
fun AppState.withTxn(t: Txn, now: Long): AppState {
    val pid = t.plannedId
    val excess = t.fromReserve - t.myShare.coerceAtLeast(0)
    val st = if (pid != null && excess > 0) withReserved(Planned(id = pid, name = t.merchant, price = 0, targetMonth = ""), reserved(pid) - excess, LocalDate.now()) else this
    val stamped = (if (excess > 0) t.copy(fromReserve = t.fromReserve - excess) else t).copy(updatedAt = now)
    return st.copy(txns = if (st.txns.any { it.id == t.id }) st.txns.map { if (it.id == t.id) stamped else it } else st.txns + stamped)
}

/** Removes [t]: dropped outright if it never reached Sheets and sync is off, otherwise a tombstone sync can carry. */
private fun AppState.tombstone(t: Txn): Txn? =
    if (t.syncedAt == null && !sync.enabled) null else t.copy(deleted = true, updatedAt = System.currentTimeMillis())

/**
 * Deletes a transaction. Deleting a planned item's "Buy now" expense puts the item back on the list,
 * with its reservations, so that money isn't left neither spent nor free.
 */
fun Store.deleteTxn(id: String) = update { it.withoutTxn(id) }

/**
 * Deletes a transaction. Deleting a "Buy now" expense puts the item back on the planned list, where its
 * reservations still are, so that money is neither spent nor lost.
 */
fun AppState.withoutTxn(id: String): AppState {
    val t = txns.firstOrNull { it.id == id }
    val pid = t?.plannedId
    val back = if (t != null && pid != null && planned.none { it.id == pid })
        Planned(id = pid, name = t.merchant, price = t.total, targetMonth = periodOf(LocalDate.parse(t.date)).key) else null
    return copy(
        txns = txns.mapNotNull { x -> if (x.id != id) x else tombstone(x) },
        meals = meals.filterNot { it.txnId == id },   // the meal logged for it goes too
        planned = planned.map { if (pid != null && it.id == pid) it.copy(status = PlannedStatus.OPEN) else it } + listOfNotNull(back),
    )
}

// Macros

fun Store.saveMeal(m: Meal) = update { st -> st.copy(meals = st.meals.filterNot { it.id == m.id } + m) }
fun Store.savePreset(p: FoodPreset) = update { st -> st.copy(presets = st.presets.filterNot { it.id == p.id } + p) }
fun Store.deletePreset(id: String) = update { st -> st.copy(presets = st.presets.filterNot { it.id == id }) }
fun Store.deleteMeal(id: String) = update { st -> st.copy(meals = st.meals.filterNot { it.id == id }) }
fun Store.editMacros(f: (MacroGoals) -> MacroGoals) = update { it.copy(macros = f(it.macros)) }

// Planned purchases

/** Saves [p]. If its price drops below what's already set aside, the extra is freed (newest first). */
fun Store.savePlanned(p: Planned) = update { st ->
    st.copy(planned = if (st.planned.any { it.id == p.id }) st.planned.map { if (it.id == p.id) p else it } else st.planned + p)
        .let { if (p.status == PlannedStatus.OPEN && it.reserved(p.id) > p.price) it.withReserved(p, p.price, LocalDate.now()) else it }
}

/** Changes the total set aside for [p]: more is reserved this month; less is released from the latest reservations first. */
fun Store.setReserved(p: Planned, total: Long) = update { it.withReserved(p, total, LocalDate.now()) }

fun AppState.withReserved(p: Planned, total: Long, today: LocalDate): AppState {
    val delta = total.coerceAtLeast(0) - reserved(p.id)
    if (delta > 0) {
        val key = periodOf(today).key
        val plan = planFor(key)
        return copy(plans = plans + (key to plan.copy(lines = plan.lines + Line(kind = LineKind.RESERVE, name = p.name, amount = delta, plannedId = p.id, added = today.toString()))))
    }
    var cut = -delta
    return copy(plans = plans.toSortedMap(reverseOrder()).mapValues { (_, plan) ->
        plan.copy(lines = plan.lines.reversed().mapNotNull { l ->
            if (cut == 0L || l.plannedId != p.id || l.kind != LineKind.RESERVE) l
            else minOf(cut, l.amount).let { take -> cut -= take; if (take == l.amount) null else l.copy(amount = l.amount - take) }
        }.reversed())
    })
}

/**
 * Buys now: one expense for the full price, and the item leaves the planned list. What was already reserved
 * isn't counted twice; those reservations become part of the purchase and can't be removed on their own.
 */
fun Store.buyNow(p: Planned, category: String) = update { it.boughtNow(p, category, LocalDate.now()) }

fun AppState.boughtNow(p: Planned, category: String, today: LocalDate): AppState {
    val st = if (reserved(p.id) > p.price) withReserved(p, p.price, today) else this   // free any excess first
    val txn = Txn(date = today.toString(), total = p.price, category = category, merchant = p.name,
        note = "Planned purchase", plannedId = p.id, fromReserve = st.reserved(p.id))
    return st.copy(txns = st.txns + txn, planned = st.planned.filterNot { it.id == p.id })
}

/** A reservation whose item was bought (or is no longer planned): spent, not set aside. */
fun AppState.isSpentReserve(l: Line) =
    l.kind == LineKind.RESERVE && l.plannedId != null && planned.none { it.id == l.plannedId && it.status == PlannedStatus.OPEN }

/** Deleting an unbought item releases its reservations; a bought one keeps them (its expense relies on them). */
fun Store.deletePlanned(id: String) = update { st ->
    val open = st.planned.any { it.id == id && it.status == PlannedStatus.OPEN }
    st.copy(
        planned = st.planned.filterNot { it.id == id },
        plans = if (!open) st.plans else st.plans.mapValues { (_, p) -> p.copy(lines = p.lines.filterNot { it.plannedId == id && it.kind == LineKind.RESERVE }) },
    )
}

// Trips

fun Store.addTrip(t: Trip) = update { it.copy(trips = it.trips + t) }

/**
 * Changes the monthly set-aside. Months already past keep what they set aside: their amounts become
 * fixed trip-fund lines, and the new amount runs from the current period.
 */
fun Store.setTripMonthly(t: Trip, amount: Long) = update { st ->
    val now = st.periodOf(LocalDate.now()).key
    val past = st.tripMonthly(t).filterKeys { it < now }
    var plans = st.plans
    past.forEach { (key, amt) ->
        val p = st.copy(plans = plans).planFor(key)
        plans = plans + (key to p.copy(lines = p.lines + Line(kind = LineKind.TRIP_FUND, name = "Trip: ${t.name} (monthly)", amount = amt, tripId = t.id)))
    }
    st.copy(plans = plans, trips = st.trips.map {
        if (it.id == t.id) it.copy(monthly = amount, monthlyFrom = if (amount > 0) now else null) else it
    })
}

fun Store.saveTripCost(tripId: String, c: TripCost) = update { st ->
    st.copy(trips = st.trips.map { t ->
        if (t.id != tripId) t else t.copy(costs = if (t.costs.any { it.id == c.id }) t.costs.map { if (it.id == c.id) c else it } else t.costs + c)
    })
}

fun Store.deleteTripCost(tripId: String, costId: String) = update { st ->
    st.copy(trips = st.trips.map { t -> if (t.id != tripId) t else t.copy(costs = t.costs.filterNot { it.id == costId }) })
}

/** Records a planned cost as paid today: a trip expense for its amount, linked back to the cost. */
fun Store.payTripCost(t: Trip, c: TripCost) = update { st ->
    val txn = Txn(date = LocalDate.now().toString(), total = c.amount, category = c.kind, merchant = c.name, tripId = t.id, note = "Planned trip cost")
    st.copy(
        txns = st.txns + txn,
        trips = st.trips.map { tr -> if (tr.id != t.id) tr else tr.copy(costs = tr.costs.map { if (it.id == c.id) it.copy(paidTxnId = txn.id) else it }) },
    )
}

fun Store.topUpTrip(t: Trip, amount: Long) {
    val key = value.periodOf(LocalDate.now()).key
    addLine(key, Line(kind = LineKind.TRIP_FUND, name = "Trip: ${t.name}", amount = amount, tripId = t.id))
}

fun Store.saveTrip(t: Trip) = update { st -> st.copy(trips = st.trips.map { if (it.id == t.id) t else it }) }

/** Deletes the trip with its set-asides (one-off and monthly) and its expenses, as if it never happened. */
fun Store.deleteTrip(id: String) = update { st ->
    st.copy(
        trips = st.trips.filterNot { it.id == id },
        txns = st.txns.mapNotNull { if (it.tripId == id) st.tombstone(it) else it },
        plans = st.plans.mapValues { (_, p) -> p.copy(lines = p.lines.filterNot { it.tripId == id }) },
    )
}

/** Ticks or unticks that [who] has paid their share of a split bill. */
fun Store.togglePaidBack(txnId: String, who: String) = update { st ->
    st.copy(txns = st.txns.map { t ->
        if (t.id != txnId) t else t.copy(paidBack = if (who in t.paidBack) t.paidBack - who else t.paidBack + who, updatedAt = System.currentTimeMillis())
    })
}

fun Store.setCategories(list: List<String>) = update { it.copy(categories = list) }

/** Daily categories use up the day's budget; monthly ones only lower the month (and so the days left). */
fun Store.setDaily(category: String, daily: Boolean) = update {
    it.copy(monthlyCategories = if (daily) it.monthlyCategories - category else it.monthlyCategories + category)
}

fun Store.setSync(f: (SyncInfo) -> SyncInfo) = update { it.copy(sync = f(it.sync)) }
