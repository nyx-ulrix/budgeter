package com.nyxulrix.budgeter.core

import kotlinx.serialization.Serializable

/** One printed line item. [price] is the line total in minor units (qty already multiplied in). */
@Serializable
data class ReceiptItem(val name: String, val price: Long, val qty: Int = 1)

/**
 * What a receipt prints, nothing more. Amounts are minor units. Charges the receipt doesn't print stay 0:
 * the app never applies a tax or service-charge rate on its own.
 */
@Serializable
data class ParsedReceipt(
    val merchant: String = "",
    val date: String? = null,          // ISO yyyy-MM-dd when known
    val currency: String? = null,
    val items: List<ReceiptItem> = emptyList(),
    val discount: Long = 0,            // bill-level discount, positive number
    val subtotal: Long? = null,
    val serviceCharge: Long = 0,       // only when printed as a separate line
    val tax: Long = 0,
    val taxIncluded: Boolean = false,  // "prices inclusive of GST": tax line is informational, not added
    val total: Long? = null,
) {
    /** Charges spread over the items: printed service charge + tax (unless already inside prices) − discount. */
    val charges: Long get() = serviceCharge + (if (taxIncluded) 0 else tax) - discount

    val computedTotal: Long get() = items.sumOf { it.price } + charges

    /** Printed total minus computed total; 0 when they agree or no total was printed. */
    val mismatch: Long get() = total?.let { it - computedTotal } ?: 0

    /** Each item's cost including its proportional share of charges. Sums exactly to [computedTotal]. */
    fun itemCosts(): List<Long> {
        val shares = allocate(charges, items.map { it.price.coerceAtLeast(0) })
        return items.mapIndexed { i, item -> item.price + shares[i] }
    }
}
