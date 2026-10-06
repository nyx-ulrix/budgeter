package com.nyxulrix.budgeter.ui

import android.provider.Settings
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nyxulrix.budgeter.core.money
import com.nyxulrix.budgeter.data.AppState
import com.nyxulrix.budgeter.data.DEFAULT_CATEGORIES
import com.nyxulrix.budgeter.data.Snapshot
import com.nyxulrix.budgeter.data.currency

/** One colour per category, kept inside the pixel palette's warm family where possible. Red is reserved for "over". */
object CategoryColors {
    val FIXED = 0xFF7A6248.toInt()
    val OVER = 0xFFC93721.toInt()
    private val defaults = listOf(
        0xFFF4512A, // Food: orange
        0xFF2459A6, // Transport: blue
        0xFF4A9A78, // Groceries: green
        0xFFE0A030, // Shopping: amber
        0xFF10172F, // Bills: navy
        0xFF9A4A8C, // Fun: plum
        0xFF3FA7C0, // Health: teal
        0xFF8C7B5A, // Other: khaki
    ).map { it.toInt() }
    private val extras = listOf(0xFFB86B3A, 0xFF5B6F2E, 0xFF6A5ACD, 0xFFD46A8F, 0xFF2F8F8F, 0xFF8A5A2B).map { it.toInt() }

    fun of(category: String): Int {
        val i = DEFAULT_CATEGORIES.indexOf(category)
        return if (i >= 0) defaults[i] else extras[Math.floorMod(category.hashCode(), extras.size)]
    }
}

data class Segment(val label: String, val amount: Long, val argb: Int)

/**
 * The month as one bar: fixed costs, then spending by category, then what's left of the budget.
 * When spending passes the budget the bar is all red.
 */
fun AppState.monthSegments(snap: Snapshot): List<Segment> {
    val order = categories + snap.categories.keys.filterNot { it in categories }
    return listOf(Segment("Fixed costs", snap.fixed, CategoryColors.FIXED)) +
        order.mapNotNull { c -> snap.categories[c]?.takeIf { it > 0 }?.let { Segment(c, it, CategoryColors.of(c)) } }
}

/** Denominator for [monthSegments]: fixed costs plus the budget, or plus spending once it's over. */
fun Snapshot.barTotal(): Long = (fixed + maxOf(spendable, spent)).coerceAtLeast(1)

@Composable
fun reducedMotion(): Boolean = Settings.Global.getFloat(LocalContext.current.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/** Colour-coded month bar with legend. Pulses red in hard steps when over budget. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MonthBar(st: AppState, snap: Snapshot, legend: Boolean = true) {
    var details by remember { mutableStateOf(false) }
    val cur = st.currency
    val segs = st.monthSegments(snap)
    val total = snap.barTotal()
    val still = reducedMotion()
    val pulse = if (snap.over && !still) rememberInfiniteTransition(label = "over").animateFloat(
        1f, 0.35f, infiniteRepeatable(tween(900, easing = { t -> if (t < 0.5f) 0f else 1f }), RepeatMode.Reverse, StartOffset(0)), label = "pulse",
    ).value else 1f
    val used = segs.sumOf { it.amount }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.fillMaxWidth().height(22.dp)
                .clickable(role = Role.Button, onClickLabel = "Show spending by category") { details = true }
                .border(3.dp, if (snap.over) Px.red else Px.brown).background(Px.creamLight).padding(3.dp)
                .semantics {
                    contentDescription = (if (snap.over) "Over budget. " else "") +
                        segs.joinToString { "${it.label} ${money(it.amount, cur)}" } + ". ${money(snap.left.coerceAtLeast(0), cur)} left"
                },
        ) {
            segs.filter { it.amount > 0 }.forEach { s ->
                Box(Modifier.weight(s.amount.toFloat() / total).height(16.dp).alpha(if (snap.over) pulse else 1f)
                    .background(if (snap.over) Px.red else Color(s.argb)))
            }
            val rest = (total - used).coerceAtLeast(0)
            if (rest > 0) Box(Modifier.weight(rest.toFloat() / total).height(16.dp))
        }
        if (legend) FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            segs.filter { it.amount > 0 }.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(10.dp).border(1.dp, Px.brown).background(Color(s.argb)))
                    Text("${s.label} ${money(s.amount, cur)}", style = Type.small)
                }
            }
        }
    }
    if (details) MonthDetails(st, snap) { details = false }
}

/** The month in numbers: fixed costs and each category, with its share and whether it counts toward each day. */
@Composable
private fun MonthDetails(st: AppState, snap: Snapshot, onDismiss: () -> Unit) {
    val cur = st.currency
    val segs = st.monthSegments(snap)
    val total = snap.barTotal()
    PixelDialog("This month", onDismiss) {
        segs.forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(14.dp).border(2.dp, Px.brown).background(Color(s.argb)))
                Column(Modifier.weight(1f)) {
                    Body(s.label)
                    if (s.label in st.monthlyCategories) Small("Monthly: spread over the days left")
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(money(s.amount, cur), style = Type.body)
                    Small("${s.amount * 100 / total}%")
                }
            }
        }
        Rule()
        KeyValue("Spent (not counting fixed)", money(snap.spent, cur))
        KeyValue("Budget", money(snap.spendable, cur))
        KeyValue(if (snap.over) "Over by" else "Left", money(kotlin.math.abs(snap.left), cur), if (snap.over) Px.red else Px.brown)
    }
}
