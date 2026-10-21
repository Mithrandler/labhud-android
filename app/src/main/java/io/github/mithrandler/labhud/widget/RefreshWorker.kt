package io.github.mithrandler.labhud.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.mithrandler.labhud.LabHudApp
import java.util.concurrent.TimeUnit

/** Every 15 minutes while a widget is placed: one /api/v1/state for the shown server, kept, drawn. */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (GlanceAppWidgetManager(ctx).getGlanceIds(StatusWidget::class.java).isEmpty()) return Result.success()
        val repo = (ctx as LabHudApp).repo
        val server = repo.store.currentNow() ?: return Result.success()
        val api = repo.api(server) ?: return Result.success()
        runCatching { api.state() }.onSuccess { repo.keep(server.id, it, now = true) }
        StatusWidget().updateAll(ctx)
        return Result.success()   // unreachable now is fine: the next run tries again
    }

    companion object {
        private const val NAME = "labhud-widget"

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<RefreshWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}
