package com.nyxulrix.budgeter.core

import java.time.LocalDate
import java.time.Month

/**
 * Rule-based receipt parser for raw OCR text, one printed row per line. Used when no AI provider is selected
 * or the AI call fails. Only reads what is printed; never computes a tax or service charge.
 * ponytail: keyword heuristics cover common till receipts; odd layouts fall back to the user correcting the editor.
 */
object ReceiptText {
    /** A price at the end of a line, shaped by how many decimals the currency uses (¥1,200 / 12.50 / 1.250). */
    private fun priceRegex(decimals: Int): Regex {
        val num = when (decimals) {
            0 -> """\d{1,3}(?:[,.]\d{3})+|\d+"""
            2 -> """\d{1,3}(?:,\d{3})+\.\d{2}|\d+[.,]\d{2}"""
            else -> """\d{1,3}(?:,\d{3})+\.\d{$decimals}|\d+[.,]\d{$decimals}"""
        }
        return Regex("""(-)?\s*(?:S\$|RM|US\$|\$|€|£|¥|円|₩|SGD|MYR|USD|JPY|KD|KWD)?\s*($num)\s*(-)?\s*[A-Za-z*#円]?\s*$""")
    }
    private val qtyPrefix = Regex("""^(\d{1,2})\s*[xX@]?\s+(.*[A-Za-z].*)$""")
    private val skip = Regex("""\b(CHANGE|CASH|TENDER|VISA|MASTER|AMEX|NETS|PAYNOW|CARD|PAID|PAYMENT|ROUNDING|ITEMS?\s*COUNT|QTY)\b""")
    private val subtotalWord = Regex("""SUB\s*-?\s*TOTAL""")
    private val totalWord = Regex("""\b(GRAND\s+TOTAL|TOTAL|AMOUNT\s+DUE|NETT|BALANCE\s+DUE)\b""")
    private val serviceWord = Regex("""\b(SERVICE|SVC|SVR|S/C|SC)\b""")
    private val taxWord = Regex("""\b(GST|TAX|VAT|SST)\b""")
    private val discountWord = Regex("""\b(DISC|DISCOUNT|LESS|PROMO|VOUCHER)\b""")
    private val inclusive = Regex("""INCL|INCLUSIVE|INCLUDES|INCLUDED""")
    /** Section headers like "*** Retail/Takeaway ***" or "== DINE IN ==": never items. */
    private val banner = Regex("""^[*=#~-]{2,}.*[*=#~-]{2,}$""")

    private fun item(name: String, amount: Long) =
        qtyPrefix.find(name)?.let { ReceiptItem(it.groupValues[2].trim(), amount, it.groupValues[1].toInt()) } ?: ReceiptItem(name, amount)

    fun parse(text: String, fallbackCurrency: String): ParsedReceipt {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val upper = text.uppercase()
        val currency = when {
            "S$" in upper || "SGD" in upper -> "SGD"
            Regex("""\bRM\b|MYR""").containsMatchIn(upper) -> "MYR"
            "€" in text -> "EUR"
            "£" in text -> "GBP"
            "¥" in text || "円" in text -> "JPY"
            else -> null
        }
        val cur = currency ?: fallbackCurrency
        val price = priceRegex(digits(cur))
        var merchant = ""
        var date: String? = null
        val items = mutableListOf<ReceiptItem>()
        var subtotal: Long? = null
        var total: Long? = null
        var service = 0L
        var tax = 0L
        var discount = 0L
        var taxIncluded = false
        var pendingName = ""
        var pendingPrice: Long? = null   // a price that landed on a header row (tilted photo): it belongs to the next item

        for (line in lines) {
            val u = line.uppercase()
            if (date == null) date = findDate(line)
            if (taxWord.containsMatchIn(u) && inclusive.containsMatchIn(u)) taxIncluded = true
            val m = price.find(line)
            if (m == null) {
                if (banner.matches(line)) continue
                if (pendingPrice != null && line.any { it.isLetter() }) { items += item(line, pendingPrice); pendingPrice = null; continue }
                if (merchant.isEmpty() && line.count { it.isLetter() } >= 3) merchant = line
                else if (total == null && line.any { it.isLetter() }) pendingName = line
                continue
            }
            val negative = m.groupValues[1].isNotEmpty() || m.groupValues[3].isNotEmpty()
            val amount = (parseMoney(m.groupValues[2], cur) ?: continue).let { if (negative) -it else it }
            val name = line.substring(0, m.range.first).trim().ifEmpty { pendingName }
            val n = name.uppercase()
            pendingName = ""
            pendingPrice = null
            if (banner.matches(name)) { if (total == null && subtotal == null) pendingPrice = amount; continue }
            when {
                skip.containsMatchIn(n) -> Unit
                subtotalWord.containsMatchIn(n) -> subtotal = amount
                totalWord.containsMatchIn(n) -> if (total == null || n.contains("GRAND")) total = amount
                serviceWord.containsMatchIn(n) -> service += amount
                taxWord.containsMatchIn(n) -> tax += amount
                discountWord.containsMatchIn(n) ->
                    if (subtotal == null && total == null) items += ReceiptItem(name, -kotlin.math.abs(amount))
                    else discount += kotlin.math.abs(amount)
                total != null || name.isEmpty() -> Unit
                else -> items += item(name, amount)
            }
        }
        return ParsedReceipt(merchant, date, currency, items, discount, subtotal, service, tax, taxIncluded, total)
    }

    private val iso = Regex("""\b(\d{4})-(\d{1,2})-(\d{1,2})\b""")
    private val dmy = Regex("""\b(\d{1,2})[/.-](\d{1,2})[/.-](\d{2,4})\b""")
    private val dMonY = Regex("""\b(\d{1,2})\s*([A-Za-z]{3})[A-Za-z]*[\s,]*(\d{2,4})\b""")

    /** ISO date from a receipt line. Numeric dates are read day-first unless that is impossible. */
    fun findDate(line: String): String? {
        fun mk(y: Int, m: Int, d: Int) = runCatching { LocalDate.of(if (y < 100) 2000 + y else y, m, d).toString() }.getOrNull()
        iso.find(line)?.let { r -> mk(r.groupValues[1].toInt(), r.groupValues[2].toInt(), r.groupValues[3].toInt())?.let { return it } }
        dmy.find(line)?.let { r ->
            val (a, b, y) = r.destructured
            return mk(y.toInt(), b.toInt(), a.toInt()) ?: mk(y.toInt(), a.toInt(), b.toInt())
        }
        dMonY.find(line)?.let { r ->
            val mon = Month.entries.firstOrNull { it.name.startsWith(r.groupValues[2].uppercase()) } ?: return null
            return mk(r.groupValues[3].toInt(), mon.value, r.groupValues[1].toInt())
        }
        return null
    }
}
