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
    // OCR often reads TOTAL's last letter as 1, I, |, O or 0 ("Net Tota1", "Subtotao"), so those count too.
    private const val TOTAL = """T[O0]TA[L1I|!O0\]]"""
    private val subtotalWord = Regex("""SUB\s*-?\s*(T[O0E]T|$TOTAL)""")   // "Sub Teta )" too
    private val totalWord = Regex("""\b(GRAND|$TOTAL|AMOUNT\s+DUE|NETT|BALANCE)""")
    private val serviceWord = Regex("""\b(SERVICE|SERV|SVC|SVR|S/C|SC|CHG|CHRG)\b""")
    private val taxWord = Regex("""\b(GST|TAX|VAT|SST)\b""")
    private val discountWord = Regex("""\b(DISC|DISCOUNT|LESS|PROMO|VOUCHER)\b""")
    private val inclusive = Regex("""INCL|INCLUSIVE|INCLUDES|INCLUDED""")
    /** Section headers like "*** Retail/Takeaway ***" or "== DINE IN ==": never items. */
    private val banner = Regex("""^[*=#~-]{2,}.*[*=#~-]{2,}$""")

    /** Subtotal, total and payment lines: never items, whoever read the receipt. */
    fun isSummary(name: String): Boolean =
        name.uppercase().let { subtotalWord.containsMatchIn(it) || totalWord.containsMatchIn(it) || skip.containsMatchIn(it) }

    /** One recognised line of text: centre, size and slant (radians) in image pixels. */
    data class Seg(val text: String, val cx: Double, val cy: Double, val w: Double, val h: Double, val angle: Double)

    private val priceOnly = Regex("""^(?:S\$|RM|US\$|SGD|MYR|\$)?\s*-?\s*\$?\d[\d,]*[.,]\d{2,3}\s*-?\s*[A-Z*#]?$""")

    /**
     * Joins OCR lines into printed rows. Words on the same row are grouped first; then each price-only line goes to the
     * row whose own slant, carried across to the price, passes closest to it. A curled or tilted receipt then keeps
     * "Svc Chg 10%" with 5.65 instead of the price drifting onto the GST row below.
     * ponytail: price-only lines need decimals, so whole-number prices (yen) fall back to plain row grouping.
     */
    fun joinRows(segs: List<Seg>): String {
        if (segs.isEmpty()) return ""
        val pitch = segs.map { it.h }.sorted()[segs.size / 2]
        val slant = segs.filter { it.w > 3 * it.h }.map { it.angle }.sorted().let { if (it.isEmpty()) 0.0 else it[it.size / 2] }
        val (sin, cos) = kotlin.math.sin(slant) to kotlin.math.cos(slant)
        fun ry(s: Seg) = -s.cx * sin + s.cy * cos
        val (prices, words) = segs.partition { priceOnly.matches(it.text.trim().uppercase()) }
        val rows = mutableListOf<MutableList<Seg>>()
        for (s in words.sortedBy(::ry)) {
            val row = rows.lastOrNull()
            if (row != null && kotlin.math.abs(ry(row.first()) - ry(s)) <= pitch * 0.5) row += s else rows += mutableListOf(s)
        }
        // How far the price sits from the row's baseline carried across to it; the row's widest piece sets the slant.
        fun miss(row: List<Seg>, p: Seg): Double {
            val l = row.maxBy { it.w }
            val a = if (l.w > 3 * l.h) l.angle else slant
            return kotlin.math.abs(p.cy - (l.cy + (p.cx - l.cx) * kotlin.math.tan(a)))
        }
        val pairs = prices.flatMap { p -> rows.indices.filter { r -> rows[r].all { it.cx < p.cx } }.map { r -> Triple(p, r, miss(rows[r], p)) } }
            .filter { it.third < pitch * 0.75 }.sortedBy { it.third }
        val priceOf = mutableMapOf<Int, Seg>()
        val used = mutableSetOf<Seg>()
        for ((p, r, _) in pairs) if (p !in used && r !in priceOf) { priceOf[r] = p; used += p }
        val out = rows.mapIndexed { r, row -> ry(row.first()) to (row.sortedBy { it.cx } + listOfNotNull(priceOf[r])).joinToString("   ") { it.text } } +
            prices.filter { it !in used }.map { ry(it) to it.text }
        return out.sortedBy { it.first }.joinToString("\n") { it.second }
    }

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
