package com.nyxulrix.budgeter.core

enum class SplitMethod(val label: String) {
    EQUAL("Equal"), EXACT("Exact"), PERCENT("Percent"), SHARES("Shares"), ITEMS("By item")
}

/**
 * Each person's share of [total]. [values] is one entry per person:
 * EQUAL ignores it, EXACT is amounts that must sum to total, PERCENT is basis points (1% = 100) summing to 10000,
 * SHARES is weights. ITEMS is handled by [itemShares]. Throws IllegalArgumentException with a user-facing message.
 */
fun split(total: Long, method: SplitMethod, values: List<Long>): List<Long> {
    require(values.isNotEmpty()) { "Add at least one person" }
    return when (method) {
        SplitMethod.EQUAL -> allocate(total, values.map { 1L })
        SplitMethod.SHARES -> {
            require(values.all { it >= 0 } && values.any { it > 0 }) { "Shares must be positive" }
            allocate(total, values)
        }
        SplitMethod.PERCENT -> {
            require(values.all { it >= 0 }) { "Percentages can't be negative" }
            require(values.sum() == 10_000L) { "Percentages add up to ${values.sum() / 100.0}%, not 100%" }
            allocate(total, values)
        }
        SplitMethod.EXACT -> {
            require(values.sum() == total) { "Amounts must add up to the total" }
            values
        }
        SplitMethod.ITEMS -> throw IllegalArgumentException("Use itemShares for item splits")
    }
}

/**
 * Item split: [costs] are each item's cost (already including its share of tax and service charge),
 * [owners] the people indexes sharing each item (empty = everyone). Returns a share per person.
 */
fun itemShares(costs: List<Long>, owners: List<Set<Int>>, people: Int): List<Long> {
    val out = LongArray(people)
    costs.forEachIndexed { i, cost ->
        val who = owners.getOrNull(i).orEmpty().filter { it in 0 until people }.ifEmpty { (0 until people).toList() }
        allocate(cost, who.map { 1L }).forEachIndexed { k, part -> out[who[k]] += part }
    }
    return out.toList()
}
