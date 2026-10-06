package com.nyxulrix.budgeter.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.nyxulrix.budgeter.R
import com.nyxulrix.budgeter.core.money
import com.nyxulrix.budgeter.data.AppState
import com.nyxulrix.budgeter.data.currency
import com.nyxulrix.budgeter.data.snapshot
import com.nyxulrix.budgeter.ui.CategoryColors
import com.nyxulrix.budgeter.ui.barTotal
import com.nyxulrix.budgeter.ui.monthSegments

/**
 * Draws the main widget as a picture using the app's own pixel fonts, frames and colours, since home-screen
 * widgets can't load custom fonts. Layout constants are in dp so the tap areas laid over it line up.
 */
object WidgetArt {
    const val PAD = 10f          // inside the frame
    const val FRAME = 3f
    const val SHADOW = 3f
    const val BUTTON = 44f       // the "+" button, top-right

    private const val ORANGE = 0xFFF4512A.toInt()
    private const val CREAM = 0xFFE7D6AD.toInt()
    private const val CREAM_LIGHT = 0xFFF1E4C6.toInt()
    private const val BROWN = 0xFF2B1D12.toInt()
    private const val MUTED = 0x8C2B1D12.toInt()
    private const val RED = 0xFFC93721.toInt()

    /** Main widget: left to spend today, a "+" button, then today's bar above the month's colour-coded bar. */
    fun main(ctx: Context, st: AppState, wDp: Float, hDp: Float): Bitmap {
        // Cap the scale so a big widget stays well under the launcher's bitmap memory limit.
        val d = minOf(ctx.resources.displayMetrics.density, 1_800_000f / (wDp * hDp).coerceAtLeast(1f)).coerceAtLeast(1f)
        val w = (wDp * d).toInt().coerceAtLeast(1)
        val h = (hDp * d).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        fun px(v: Float) = v * d
        val display = ResourcesCompat.getFont(ctx, R.font.press_start) ?: Typeface.MONOSPACE
        val label = ResourcesCompat.getFont(ctx, R.font.silkscreen_bold) ?: Typeface.MONOSPACE
        val body = ResourcesCompat.getFont(ctx, R.font.vt323) ?: Typeface.MONOSPACE
        val fill = Paint()
        fun rect(l: Float, t: Float, r: Float, b: Float, color: Int) { fill.color = color; c.drawRect(l, t, r, b, fill) }
        fun text(s: String, x: Float, y: Float, face: Typeface, sizeSp: Float, color: Int, right: Boolean = false) {
            val p = Paint().apply { typeface = face; textSize = px(sizeSp); this.color = color; isAntiAlias = false; textAlign = if (right) Paint.Align.RIGHT else Paint.Align.LEFT }
            c.drawText(s, x, y, p)
        }
        fun fitSize(s: String, face: Typeface, startSp: Float, maxWidth: Float): Float {
            var sp = startSp
            val p = Paint().apply { typeface = face }
            while (sp > 8f) { p.textSize = px(sp); if (p.measureText(s) <= maxWidth) break; sp -= 1f }
            return sp
        }

        // Window frame with hard shadow, like every window in the app.
        val right = w - px(SHADOW); val bottom = h - px(SHADOW)
        rect(px(SHADOW), px(SHADOW), w.toFloat(), h.toFloat(), BROWN)
        rect(0f, 0f, right, bottom, BROWN)
        rect(px(FRAME), px(FRAME), right - px(FRAME), bottom - px(FRAME), CREAM_LIGHT)

        val l = px(FRAME + PAD); val r = right - px(FRAME + PAD); val top = px(FRAME + 8f)
        val snap = st.snapshot()
        val cur = st.currency

        // "+" button, top-right: orange, brown outline, hard shadow.
        val b = RectF(r - px(BUTTON), top, r, top + px(BUTTON))
        rect(b.left + px(3f), b.top + px(3f), b.right + px(3f), b.bottom + px(3f), BROWN)
        rect(b.left, b.top, b.right, b.bottom, BROWN)
        rect(b.left + px(3f), b.top + px(3f), b.right - px(3f), b.bottom - px(3f), ORANGE)
        val cx = b.centerX(); val cy = b.centerY(); val arm = px(11f); val thick = px(5f)
        rect(cx - arm, cy - thick / 2, cx + arm, cy + thick / 2, CREAM_LIGHT)
        rect(cx - thick / 2, cy - arm, cx + thick / 2, cy + arm, CREAM_LIGHT)

        // Left to spend today: one number.
        val left = snap.day.remaining
        text("LEFT TODAY", l, top + px(10f), label, 10f, BROWN)
        val amount = money(left, cur)
        val sp = fitSize(amount, display, 22f, b.left - l - px(8f))
        text(amount, l, top + px(10f + 6f) + px(sp), display, sp, if (left < 0) RED else BROWN)

        val compact = hDp < 110f
        if (compact) return bmp

        // The two bars share the space under the number, growing with the widget's height.
        val area = (bottom - px(FRAME + 8f)) - (top + px(BUTTON + 6f))
        val section = area / 2
        val barH = (section - px(26f)).coerceIn(px(12f), px(30f))
        val dayTop = top + px(BUTTON + 6f) + (section - barH - px(18f)) / 2

        // Today's bar (blocks, like the app's progress bars).
        var y = dayTop + px(12f)
        val dayFrac = if (snap.day.budget <= 0) (if (snap.day.spent > 0) 1f else 0f) else (snap.day.spent.toFloat() / snap.day.budget).coerceIn(0f, 1f)
        text("TODAY", l, y, label, 9f, BROWN)
        text("${money(snap.day.spent, cur)} / ${money(snap.day.budget, cur)}", r, y, body, 15f, if (left < 0) RED else MUTED, right = true)
        y += px(4f)
        drawBlocks(c, fill, l, y, r, y + barH, d, dayFrac, if (left < 0) RED else ORANGE)
        y = dayTop + section + px(12f)

        // Month bar: fixed costs and each category in its colour, all red once over budget.
        text("MONTH", l, y, label, 9f, BROWN)
        text(if (snap.over) "${money(snap.spent - snap.spendable, cur)} over" else "${money(snap.left, cur)} left", r, y, body, 15f, if (snap.over) RED else MUTED, right = true)
        y += px(4f)
        val barB = y + barH
        rect(l, y, r, barB, if (snap.over) RED else BROWN)
        rect(l + px(2f), y + px(2f), r - px(2f), barB - px(2f), CREAM_LIGHT)
        val total = snap.barTotal().toFloat()
        var x = l + px(2f)
        val span = (r - l) - px(4f)
        for (s in st.monthSegments(snap)) {
            if (s.amount <= 0) continue
            val wSeg = span * (s.amount / total)
            rect(x, y + px(2f), (x + wSeg).coerceAtMost(r - px(2f)), barB - px(2f), if (snap.over) CategoryColors.OVER else s.argb)
            x += wSeg
        }
        return bmp
    }

    /** Segmented block bar with a 2dp outline. */
    private fun drawBlocks(c: Canvas, fill: Paint, l: Float, t: Float, r: Float, b: Float, d: Float, frac: Float, color: Int) {
        fill.color = BROWN; c.drawRect(l, t, r, b, fill)
        fill.color = CREAM; c.drawRect(l + 2 * d, t + 2 * d, r - 2 * d, b - 2 * d, fill)
        val n = 20; val gap = 2 * d
        val bw = ((r - l) - 4 * d - gap * (n - 1)) / n
        val filled = (frac * n).let { if (it > 0f && it < 1f) 1 else it.toInt() }
        for (i in 0 until n) {
            fill.color = if (i < filled) color else CREAM_LIGHT
            val x = l + 2 * d + i * (bw + gap)
            c.drawRect(x, t + 2 * d + d, x + bw, b - 2 * d - d, fill)
        }
    }
}
