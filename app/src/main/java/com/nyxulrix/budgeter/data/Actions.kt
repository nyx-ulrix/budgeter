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

fun Store.addLine(key: String, line: Line) = editPlan(key) { it.copy(lines = it.lines + line) }
fun Store.removeLine(key: String, id: String) = editPlan(key) { p -> p.copy(lines = p.lines.filterNot { it.id == id }) }

fun Store.saveTxn(t: Txn) = update { st ->
    val stamped = t.copy(updatedAt = System.currentTimeMillis())
    st.copy(txns = if (st.txns.any { it.id == t.id }) st.txns.map { if (it.id == t.id) stamped else it } else st.txns + stamped)
}

/** Marks deleted so the removal can reach Sheets; synced-or-never-synced tombstones are purged by sync. */
fun Store.deleteTxn(id: String) = update { st ->
    st.copy(txns = st.txns.mapNotNull { t ->
        when {
            t.id != id -> t
            t.syncedAt == null && !st.sync.enabled -> null
            else -> t.copy(deleted = true, updatedAt = System.currentTimeMillis())
        }
    })
}

// Planned purchases

fun Store.savePlanned(p: Planned) = update { st ->
    st.copy(planned = if (st.planned.any { it.id == p.id }) st.planned.map { if (it.id == p.id) p else it } else st.planned + p)
}

/** Earmarks [amount] in the current period: it leaves spendable now. */
fun Store.reserve(p: Planned, amount: Long) {
    val key = value.periodOf(LocalDate.now()).key
    addLine(key, Line(kind = LineKind.RESERVE, name = p.name, amount = amount, plannedId = p.id))
}

/** Buys now: one expense for the full price; the part already reserved isn't counted twice. */
fun Store.buyNow(p: Planned, category: String) = update { st ->
    val reserved = st.reserved(p.id).coerceAtMost(p.price)
    val txn = Txn(date = LocalDate.now().toString(), total = p.price, category = category, merchant = p.name,
        note = "Planned purchase", plannedId = p.id, fromReserve = reserved)
    st.copy(
        txns = st.txns + txn,
        planned = st.planned.map { if (it.id == p.id) it.copy(status = PlannedStatus.BOUGHT) else it },
    )
}

/** Deleting an unbought item releases its reservations; a bought one keeps them (its expense relies on them). */
fun Store.deletePlanned(id: String) = update { st ->
    val open = st.planned.any { it.id == id && it.status == PlannedStatus.OPEN }
    st.copy(
        planned = st.planned.filterNot { it.id == id },
        plans = if (!open) st.plans else st.plans.mapValues { (_, p) -> p.copy(lines = p.lines.filterNot { it.plannedId == id && it.kind == LineKind.RESERVE }) },
    )
}

// People and groups

fun Store.addPerson(name: String): String {
    val p = Person(name = name.trim())
    update { it.copy(people = it.people + p) }
    return p.id
}

fun Store.saveGroup(g: Group) = update { st ->
    st.copy(groups = if (st.groups.any { it.id == g.id }) st.groups.map { if (it.id == g.id) g else it } else st.groups + g)
}

fun Store.deleteGroup(id: String) = update { st ->
    st.copy(groups = st.groups.filterNot { it.id == id }, settlements = st.settlements.filterNot { it.groupId == id })
}

fun Store.settleUp(groupId: String, from: String, to: String, amount: Long) = update {
    it.copy(settlements = it.settlements + Settlement(groupId = groupId, from = from, to = to, amount = amount, date = LocalDate.now().toString()))
}

// Trips

/** Creates a trip and sets its fund aside in the current period. */
fun Store.addTrip(t: Trip, fund: Long) {
    update { it.copy(trips = it.trips + t) }
    if (fund > 0) topUpTrip(t, fund)
}

fun Store.topUpTrip(t: Trip, amount: Long) {
    val key = value.periodOf(LocalDate.now()).key
    addLine(key, Line(kind = LineKind.TRIP_FUND, name = "Trip: ${t.name}", amount = amount, tripId = t.id))
}

fun Store.saveTrip(t: Trip) = update { st -> st.copy(trips = st.trips.map { if (it.id == t.id) t else it }) }

/** Deletes the trip with its fund set-asides and its expenses, as if it never happened. */
fun Store.deleteTrip(id: String) = update { st ->
    st.copy(
        trips = st.trips.filterNot { it.id == id },
        txns = st.txns.map { if (it.tripId == id) it.copy(deleted = true, updatedAt = System.currentTimeMillis()) else it },
        plans = st.plans.mapValues { (_, p) -> p.copy(lines = p.lines.filterNot { it.tripId == id }) },
    )
}

fun Store.setCategories(list: List<String>) = update { it.copy(categories = list) }

fun Store.setSync(f: (SyncInfo) -> SyncInfo) = update { it.copy(sync = f(it.sync)) }
