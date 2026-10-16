package io.github.mithrandler.labhud.push

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.mithrandler.labhud.LabHudApp
import io.github.mithrandler.labhud.MainActivity
import io.github.mithrandler.labhud.R
import io.github.mithrandler.labhud.data.Note
import io.github.mithrandler.labhud.data.NotifyMode
import io.github.mithrandler.labhud.data.Server
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.PushService
import org.unifiedpush.android.connector.UnifiedPush
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage

/**
 * Instant notifications without Google in the app: UnifiedPush. A distributor on the phone (the
 * ntfy app is one; its Play version rides on Google's push) gives an endpoint, which every paired
 * labhud gets (POST /api/v1/push). labhud POSTs there a wake-up with no news in it; the app then
 * reads /api/v1/notifications itself, over the home network or VPN, and shows what is new.
 */
object Push {
    const val ALERTS = "alerts"
    const val NEWS = "news"

    /** Asks the distributor for an endpoint. False: no distributor on this phone (install ntfy). */
    fun enable(activity: Activity, done: (Boolean) -> Unit) {
        UnifiedPush.tryUseCurrentOrDefaultDistributor(activity) { ok ->
            if (ok) UnifiedPush.register(activity, messageForDistributor = "LabHUD")
            done(ok)
        }
    }

    fun distributors(context: Context): List<String> = UnifiedPush.getDistributors(context)

    /** Ends the registration; the servers forget the endpoint. */
    suspend fun disable(context: Context) {
        runCatching { UnifiedPush.unregister(context) }
        share(context, "")
        (context.applicationContext as LabHudApp).store.setPushEndpoint(null)
    }

    /** Hands [endpoint] ("" = none) to every paired labhud. */
    suspend fun share(context: Context, endpoint: String) {
        val repo = (context.applicationContext as LabHudApp).repo
        repo.servers().forEach { s -> runCatching { repo.api(s)?.push(endpoint) } }
    }

    /** A server just paired: it gets the endpoint this phone already has. */
    suspend fun shareWith(context: Context, server: Server) {
        val app = context.applicationContext as LabHudApp
        if (app.store.settings.first().notify != NotifyMode.Instant) return
        val endpoint = app.store.pushEndpoint() ?: return
        runCatching { app.repo.api(server)?.push(endpoint) }
    }

    fun channels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(ALERTS, "Problems", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "A host down, a disk full, a rule that fired"
        })
        nm.createNotificationChannel(NotificationChannel(NEWS, "Other news", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Back up again, summaries after the quiet hours"
        })
    }

    /** Reads the news now (a wake-up came, or the app opened). */
    fun fetch(context: Context) {
        val req = OneTimeWorkRequestBuilder<NotesWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("labhud-notes", ExistingWorkPolicy.APPEND_OR_REPLACE, req)
    }

    fun show(context: Context, tag: String, id: Int, title: String, text: String, bad: Boolean, card: String?) {
        val open = Intent(Intent.ACTION_VIEW, Uri.parse(if (card != null) "labhud://card/$card" else "labhud://open"), context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(context, id, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(context, if (bad) ALERTS else NEWS)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(if (bad) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(tag, id, n) }   // no permission: nothing shown
    }
}

/** The distributor's side: a new endpoint, a wake-up, the end of the registration. */
class PushServiceImpl : PushService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewEndpoint(endpoint: PushEndpoint, instance: String) {
        scope.launch {
            (applicationContext as LabHudApp).store.setPushEndpoint(endpoint.url)
            Push.share(applicationContext, endpoint.url)
        }
    }

    override fun onMessage(message: PushMessage, instance: String) = Push.fetch(applicationContext)

    override fun onRegistrationFailed(reason: FailedReason, instance: String) {
        scope.launch { (applicationContext as LabHudApp).store.setPushEndpoint(null) }
    }

    override fun onUnregistered(instance: String) {
        scope.launch {
            (applicationContext as LabHudApp).store.setPushEndpoint(null)
            Push.share(applicationContext, "")
        }
    }
}

/**
 * What is new in every paired labhud's outbox, as notifications. The first read of a server only
 * marks where it stands (no flood of old news); more than [MAX] new at once become one summary.
 * When a server cannot be reached (away from home, no VPN), one notification says so.
 */
class NotesWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as LabHudApp
        if (app.store.settings.first().notify != NotifyMode.Instant) return Result.success()
        for (s in app.repo.servers()) {
            val api = app.repo.api(s) ?: continue
            val last = app.store.lastNote(s.id)
            val answer = runCatching { api.notes(last ?: 0) }.getOrElse {
                Push.show(applicationContext, "reach-${s.id}", 1, s.name, "LabHUD has news. Open the app at home or on the VPN to read it.", true, null)
                continue
            }
            val fresh = answer.notifications.filter { last == null || it.id > last }
            fresh.maxOfOrNull { it.id }?.let { app.store.setLastNote(s.id, it) }
            if (last == null || answer.via == "ntfy") continue
            NotificationManagerCompat.from(applicationContext).cancel("reach-${s.id}", 1)
            show(s, fresh)
        }
        return Result.success()
    }

    private fun show(s: Server, fresh: List<Note>) {
        if (fresh.size > MAX) {
            val bad = fresh.count { it.bad }
            Push.show(applicationContext, "sum-${s.id}", 0, s.name, "${fresh.size} new: " +
                fresh.takeLast(4).joinToString("; ") { it.text } + if (bad > 0) " ($bad bad)" else "", bad > 0, null)
            return
        }
        fresh.forEach { n -> Push.show(applicationContext, "note-${s.id}", (n.id % Int.MAX_VALUE).toInt(), n.title.ifBlank { s.name }, n.text, n.bad, n.card) }
    }

    companion object {
        const val MAX = 5
    }
}
