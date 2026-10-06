package com.nyxulrix.budgeter.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nyxulrix.budgeter.App
import com.nyxulrix.budgeter.MainActivity
import com.nyxulrix.budgeter.R
import com.nyxulrix.budgeter.core.Pace
import com.nyxulrix.budgeter.core.money
import com.nyxulrix.budgeter.data.PlannedStatus
import com.nyxulrix.budgeter.data.currency
import com.nyxulrix.budgeter.data.reserved
import com.nyxulrix.budgeter.data.snapshot
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

private val orange = ColorProvider(Color(0xFFF4512A))
private val cream = ColorProvider(Color(0xFFF1E4C6))
private val brown = ColorProvider(Color(0xFF2B1D12))
private val green = ColorProvider(Color(0xFF4A9A78))
private val red = ColorProvider(Color(0xFFC93721))
private val blue = ColorProvider(Color(0xFF2459A6))

private fun open(ctx: Context, what: String) = actionStartActivity(
    Intent(ctx, MainActivity::class.java)
        .setData(Uri.parse("budgeter://widget/$what"))       // distinct data keeps each widget's tap separate
        .putExtra(MainActivity.EXTRA_OPEN, what)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
)

private fun t(size: Int = 13, color: ColorProvider = brown, bold: Boolean = false) =
    TextStyle(color = color, fontSize = size.sp, fontFamily = FontFamily.Monospace, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)

/** Pixel window: brown frame, orange title strip, cream body. */
@Composable
private fun Frame(title: String, action: androidx.glance.action.Action, content: @Composable () -> Unit) {
    Box(GlanceModifier.fillMaxSize().background(brown).padding(3.dp).clickable(action)) {
        Column(GlanceModifier.fillMaxSize().background(cream)) {
            Row(GlanceModifier.fillMaxWidth().background(orange).padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title.uppercase(), style = t(11, cream, true))
            }
            Spacer(GlanceModifier.fillMaxWidth().height(3.dp).background(brown))
            Column(GlanceModifier.fillMaxSize().padding(8.dp)) { content() }
        }
    }
}

private fun paceColor(p: Pace) = when (p) { Pace.ON_TRACK -> green; Pace.SLIGHTLY_OVER -> orange; Pace.OVER -> red }

class SpendWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) = provideContent {
        val st = App.store.value
        val s = st.snapshot()
        val cur = st.currency
        Frame("This month", open(context, "home")) {
            Text("${money(s.spent, cur)} / ${money(s.spendable, cur)}", style = t(13, bold = true))
            Spacer(GlanceModifier.height(4.dp))
            LinearProgressIndicator(s.fraction, GlanceModifier.fillMaxWidth().height(10.dp), color = paceColor(s.pace), backgroundColor = ColorProvider(Color(0xFFE7D6AD)))
            Spacer(GlanceModifier.height(4.dp))
            Text("${(s.fraction * 100).toInt()}% used · ${s.pace.label}", style = t(12, paceColor(s.pace)))
        }
    }
}

class DailyWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) = provideContent {
        val st = App.store.value
        val d = st.snapshot().day
        val cur = st.currency
        Frame("Today", open(context, "home")) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(ImageProvider(R.drawable.mascot_idle), "Mascot", GlanceModifier.size(32.dp))
                Spacer(GlanceModifier.width(8.dp))
                Column {
                    Text("Left ${money(d.remaining, cur)}", style = t(14, if (d.remaining < 0) red else brown, true))
                    Text("Budget ${money(d.budget, cur)} · spent ${money(d.spent, cur)}", style = t(11))
                }
            }
        }
    }
}

class QuickAddWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) = provideContent {
        Box(GlanceModifier.fillMaxSize().background(brown).padding(3.dp)) {
            Row(GlanceModifier.fillMaxSize().background(cream)) {
                Box(GlanceModifier.defaultWeight().fillMaxSize().background(orange).clickable(open(context, "add")), contentAlignment = Alignment.Center) {
                    Text("+ EXPENSE", style = t(13, cream, true))
                }
                Spacer(GlanceModifier.width(3.dp).fillMaxSize().background(brown))
                Box(GlanceModifier.defaultWeight().fillMaxSize().clickable(open(context, "scan")), contentAlignment = Alignment.Center) {
                    Text("SCAN", style = t(13, brown, true))
                }
            }
        }
    }
}

class PlannedWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) = provideContent {
        val st = App.store.value
        val cur = st.currency
        val top = st.planned.filter { it.status == PlannedStatus.OPEN }.sortedWith(compareBy({ it.priority }, { it.targetMonth })).take(2)
        Frame("Planned", open(context, "planned")) {
            if (top.isEmpty()) Text("Nothing planned", style = t(12))
            top.forEach { p ->
                Text(p.name, style = t(12, bold = true))
                Text("${money(st.reserved(p.id), cur)} of ${money(p.price, cur)} reserved", style = t(11, blue))
                Spacer(GlanceModifier.height(4.dp))
            }
        }
    }
}

class SpendReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = SpendWidget() }
class DailyReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = DailyWidget() }
class QuickAddReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = QuickAddWidget() }
class PlannedReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = PlannedWidget() }

object Widgets {
    /** Redraw every widget. Called after each change. */
    fun refresh(ctx: Context) {
        App.scope.launch {
            SpendWidget().updateAll(ctx); DailyWidget().updateAll(ctx); PlannedWidget().updateAll(ctx)
        }
    }

    /** One job just after midnight so "today" rolls over even if the app isn't opened. */
    fun scheduleMidnight(ctx: Context) {
        val next = LocalDate.now().plusDays(1).atStartOfDay().plusMinutes(1)
        val req = OneTimeWorkRequestBuilder<MidnightWorker>()
            .setInitialDelay(Duration.between(LocalDateTime.now(), next).toMillis(), TimeUnit.MILLISECONDS).build()
        WorkManager.getInstance(ctx).enqueueUniqueWork("midnight", ExistingWorkPolicy.REPLACE, req)
    }
}

class MidnightWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        SpendWidget().updateAll(applicationContext); DailyWidget().updateAll(applicationContext); PlannedWidget().updateAll(applicationContext)
        Widgets.scheduleMidnight(applicationContext)
        return Result.success()
    }
}
