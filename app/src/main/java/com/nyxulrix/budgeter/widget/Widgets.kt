package com.nyxulrix.budgeter.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nyxulrix.budgeter.App
import com.nyxulrix.budgeter.MainActivity
import com.nyxulrix.budgeter.core.money
import com.nyxulrix.budgeter.data.currency
import com.nyxulrix.budgeter.data.snapshot
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

private fun open(ctx: Context, what: String) = actionStartActivity(
    Intent(ctx, MainActivity::class.java)
        .setData(Uri.parse("budgeter://widget/$what"))       // distinct data keeps each tap target separate
        .putExtra(MainActivity.EXTRA_OPEN, what)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
)

/**
 * Main widget, drawn in the app's own look: left to spend today, a camera button that opens the receipt scanner
 * (read on the phone) and a "+" that opens a new expense. Tapping anywhere else opens the app.
 */
class SpendWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) = provideContent {
        val size = LocalSize.current
        val st = App.store.value
        val snap = st.snapshot()
        Box(GlanceModifier.fillMaxSize().clickable(open(context, "home"))) {
            Image(
                ImageProvider(WidgetArt.main(context, st, size.width.value, size.height.value)),
                "Budgeter: ${money(snap.day.remaining, st.currency)} left to spend today",
                GlanceModifier.fillMaxSize(), contentScale = ContentScale.FillBounds,
            )
            Box(
                GlanceModifier.fillMaxSize().padding(end = (WidgetArt.SHADOW + WidgetArt.FRAME + WidgetArt.PAD).dp, bottom = WidgetArt.SHADOW.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Row {
                    Box(GlanceModifier.size(WidgetArt.BUTTON.dp).clickable(open(context, "scan")).semantics { contentDescription = "Scan a receipt" }) {}
                    Spacer(GlanceModifier.width(WidgetArt.GAP.dp))
                    Box(GlanceModifier.size(WidgetArt.BUTTON.dp).clickable(open(context, "add")).semantics { contentDescription = "Add expense" }) {}
                }
            }
        }
    }
}

/** Bars widget: today's progress above the month's colour-coded bar. Tap to open the app. */
class BarsWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) = provideContent {
        val size = LocalSize.current
        val st = App.store.value
        val snap = st.snapshot()
        val cur = st.currency
        Image(
            ImageProvider(WidgetArt.bars(context, st, size.width.value, size.height.value)),
            "Today ${money(snap.day.spent, cur)} of ${money(snap.day.budget, cur)}. Month ${money(snap.spent, cur)} of ${money(snap.spendable, cur)}" +
                if (snap.over) ", over budget" else "",
            GlanceModifier.fillMaxSize().clickable(open(context, "home")), contentScale = ContentScale.FillBounds,
        )
    }
}

class SpendReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = SpendWidget() }
class BarsReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = BarsWidget() }

object Widgets {
    suspend fun redraw(ctx: Context) { SpendWidget().updateAll(ctx); BarsWidget().updateAll(ctx) }

    /** Redraw every widget. Called after each change. */
    fun refresh(ctx: Context) { App.scope.launch { redraw(ctx) } }

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
        Widgets.redraw(applicationContext)
        Widgets.scheduleMidnight(applicationContext)
        return Result.success()
    }
}
