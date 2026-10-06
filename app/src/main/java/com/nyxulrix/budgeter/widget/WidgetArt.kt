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
import com.nyxulrix.budgeter.ui.Glyphs
import com.nyxulrix.budgeter.ui.alertArgb
import com.nyxulrix.budgeter.ui.monthBlocks

/**
 * Draws widgets as pictures using the app's own pixel fonts, frames and colours, since home-screen widgets can't
 * load custom fonts. Layout constants are in dp so the tap areas laid over the picture line up with it.
 */
object WidgetArt {
    const val PAD = 10f          // inside the frame
    const val FRAME = 3f
    const val SHADOW = 3f
    const val BUTTON = 44f       // square buttons on the main widget
    const val GAP = 8f           // between the camera and "+" buttons

    private const val ORANGE = 0xFFF4512A.toInt()
    private const val CREAM = 0xFFE7D6AD.toInt()
    private const val CREAM_LIGHT = 0xFFF1E4C6.toInt()
    private const val BROWN = 0xFF2B1D12.toInt()
    private const val MUTED = 0x8C2B1D12.toInt()
    private const val RED = 0xFFC93721.toInt()
    private const val GREEN = 0xFF4A9A78.toInt()
    private const val ORANGE_DARK = 0xFFB8461F.toInt()   // today's budget used up, spending saved-up money

    /** A canvas with dp helpers, the three fonts and the window frame already drawn. */
    private class Art(ctx: Context, wDp: Float, hDp: Float) {
        // Cap the scale so a big widget stays well under the launcher's bitmap memory limit.
        val d = minOf(ctx.resources.displayMetrics.density, 1_800_000f / (wDp * hDp).coerceAtLeast(1f)).coerceAtLeast(1f)
        val bmp: Bitmap = Bitmap.createBitmap((wDp * d).toInt().coerceAtLeast(1), (hDp * d).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val display: Typeface = ResourcesCompat.getFont(ctx, R.font.press_start) ?: Typeface.MONOSPACE
        val label: Typeface = ResourcesCompat.getFont(ctx, R.font.silkscreen_bold) ?: Typeface.MONOSPACE
        val body: Typeface = ResourcesCompat.getFont(ctx, R.font.vt323) ?: Typeface.MONOSPACE
        private val fill = Paint()
        fun px(v: Float) = v * d
        val right = bmp.width - px(SHADOW)
        val bottom = bmp.height - px(SHADOW)
        val l = px(FRAME + PAD)
        val r = right - px(FRAME + PAD)

        init {
            rect(px(SHADOW), px(SHADOW), bmp.width.toFloat(), bmp.height.toFloat(), BROWN)   // hard shadow
            rect(0f, 0f, right, bottom, BROWN)
            rect(px(FRAME), px(FRAME), right - px(FRAME), bottom - px(FRAME), CREAM_LIGHT)
        }

        fun rect(l: Float, t: Float, r: Float, b: Float, color: Int) { fill.color = color; c.drawRect(l, t, r, b, fill) }

        fun text(s: String, x: Float, y: Float, face: Typeface, sizeSp: Float, color: Int, right: Boolean = false) =
            c.drawText(s, x, y, Paint().apply { typeface = face; textSize = px(sizeSp); this.color = color; isAntiAlias = false; textAlign = if (right) Paint.Align.RIGHT else Paint.Align.LEFT })

        fun fitSize(s: String, face: Typeface, startSp: Float, maxWidth: Float): Float {
            var sp = startSp
            val p = Paint().apply { typeface = face }
            while (sp > 8f) { p.textSize = px(sp); if (p.measureText(s) <= maxWidth) break; sp -= 1f }
            return sp
        }

        /** Orange square button with hard shadow and a 9×9 pixel glyph, like PixelButton. */
        fun button(b: RectF, glyph: List<String>) {
            rect(b.left + px(3f), b.top + px(3f), b.right + px(3f), b.bottom + px(3f), BROWN)
            rect(b.left, b.top, b.right, b.bottom, BROWN)
            rect(b.left + px(3f), b.top + px(3f), b.right - px(3f), b.bottom - px(3f), ORANGE)
            val cell = (b.width() * 0.5f / glyph.size).toInt().toFloat().coerceAtLeast(1f)
            val ox = b.centerX() - cell * glyph.size / 2; val oy = b.centerY() - cell * glyph.size / 2
            glyph.forEachIndexed { y, row -> row.forEachIndexed { x, ch -> if (ch == '#') rect(ox + x * cell, oy + y * cell, ox + (x + 1) * cell, oy + (y + 1) * cell, CREAM_LIGHT) } }
        }

        /** Segmented block bar with a 2dp outline: [colors] per block, null = empty. */
        fun blocks(t: Float, b: Float, colors: List<Int?>, outline: Int = BROWN) {
            rect(l, t, r, b, outline)
            rect(l + px(2f), t + px(2f), r - px(2f), b - px(2f), CREAM)
            val n = colors.size; val gap = px(2f)
            val bw = ((r - l) - px(4f) - gap * (n - 1)) / n
            colors.forEachIndexed { i, c ->
                val x = l + px(2f) + i * (bw + gap)
                rect(x, t + px(3f), x + bw, b - px(3f), CREAM_LIGHT)
                if (c != null) rect(x, t + px(3f), x + bw, b - px(3f), c)
            }
        }

        /** Same bar, filled to [frac] in one colour (20 blocks, like the app's progress bars). */
        fun blocks(t: Float, b: Float, frac: Float, color: Int) {
            val n = 20
            val filled = (frac * n).let { if (it > 0f && it < 1f) 1 else it.toInt() }
            blocks(t, b, List(n) { if (it < filled) color else null })
        }
    }

    /** Main widget: what's left to spend today, then a camera button (scan a receipt) and "+" (add an expense). */
    fun main(ctx: Context, st: AppState, wDp: Float, hDp: Float): Bitmap {
        val a = Art(ctx, wDp, hDp)
        val snap = st.snapshot()
        val cy = a.bottom / 2 + a.px(FRAME) / 2
        val plus = RectF(a.r - a.px(BUTTON), cy - a.px(BUTTON / 2), a.r, cy + a.px(BUTTON / 2))
        val cam = RectF(plus.left - a.px(GAP + BUTTON), plus.top, plus.left - a.px(GAP), plus.bottom)
        a.button(plus, Glyphs.plus)
        a.button(cam, Glyphs.camera)
        val day = snap.day
        val amount = money(day.dailyLeft, st.currency)
        val sp = a.fitSize(amount, a.display, 22f, cam.left - a.l - a.px(10f))
        val color = when { day.remaining < 0 -> RED; day.dailyLeft < 0 -> ORANGE; else -> BROWN }
        val shift = if (day.bonus > 0) a.px(7f) else 0f
        a.text("LEFT TODAY", a.l, cy - a.px(4f) - a.px(sp) / 2 - shift, a.label, 10f, BROWN)
        a.text(amount, a.l, cy + a.px(6f) + a.px(sp) / 2 - shift, a.display, sp, color)
        if (day.bonus > 0) a.text("+${money(day.bonusLeft, st.currency)} saved up", a.l, cy + a.px(20f) + a.px(sp) / 2 - shift, a.body, 15f, GREEN)
        return a.bmp
    }

    /** Bars widget: today's block bar above the month's colour-coded bar, filling the widget's height. */
    fun bars(ctx: Context, st: AppState, wDp: Float, hDp: Float): Bitmap {
        val a = Art(ctx, wDp, hDp)
        val snap = st.snapshot()
        val cur = st.currency
        val top = a.px(FRAME + 6f)
        val section = (a.bottom - a.px(FRAME + 6f) - top) / 2
        val barH = (section - a.px(20f)).coerceIn(a.px(10f), a.px(30f))
        val left = snap.day.dailyLeft

        var y = top + (section - barH - a.px(14f)) / 2 + a.px(10f)
        val budget = snap.day.budget
        val dayFrac = if (budget <= 0) (if (snap.day.spent > 0) 1f else 0f) else (snap.day.spent.toFloat() / budget).coerceIn(0f, 1f)
        val dayColor = when { snap.day.remaining < 0 -> RED; left < 0 -> ORANGE_DARK; else -> ORANGE }
        a.text("TODAY", a.l, y, a.label, 9f, BROWN)
        a.text("${money(snap.day.spent, cur)} / ${money(budget, cur)}" + if (snap.day.bonus > 0) " +${money(snap.day.bonusLeft, cur)}" else "",
            a.r, y, a.body, 15f, if (snap.day.remaining < 0) RED else MUTED, right = true)
        y += a.px(4f)
        a.blocks(y, y + barH, dayFrac, dayColor)

        y = top + section + (section - barH - a.px(14f)) / 2 + a.px(10f)
        a.text("MONTH", a.l, y, a.label, 9f, BROWN)
        val alert = snap.alertArgb()
        a.text(when {
            snap.over -> "${money(snap.spent - snap.spendable - snap.target, cur)} over"
            snap.dipping -> "${money(snap.dipped, cur)} into savings"
            else -> "${money(snap.left, cur)} left"
        }, a.r, y, a.body, 15f, if (snap.over) RED else MUTED, right = true)
        y += a.px(4f)
        a.blocks(y, y + barH, st.monthBlocks(snap), alert ?: BROWN)
        return a.bmp
    }
}
