package io.github.mithrandler.labhud

import android.app.Application
import android.os.SystemClock
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import io.github.mithrandler.labhud.data.LabRepository
import io.github.mithrandler.labhud.data.ServerStore
import io.github.mithrandler.labhud.lock.AppLock
import io.github.mithrandler.labhud.widget.StatusWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import io.github.mithrandler.labhud.data.NotifyMode
import io.github.mithrandler.labhud.push.Push
import kotlinx.coroutines.launch

/**
 * The app's one graph: the store and the repository. The live stream runs only while the app is
 * in front and unlocked; in the background nothing runs (the widget's worker aside).
 */
class LabHudApp : Application() {
    lateinit var store: ServerStore
    lateinit var repo: LabRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var widgetAt = 0L

    override fun onCreate() {
        super.onCreate()
        store = ServerStore(this)
        repo = LabRepository(this, store)
        repo.onNewState = { _, _ ->
            // the widget follows the live state, at most once a minute
            val now = SystemClock.elapsedRealtime()
            if (now - widgetAt > 60_000) {
                widgetAt = now
                scope.launch { runCatching { StatusWidget().updateAll(this@LabHudApp) } }
            }
        }
        val foreground = kotlinx.coroutines.flow.MutableStateFlow(false)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                AppLock.onShown()
                foreground.value = true
                // news missed while no wake-up could come (no network, the distributor asleep)
                scope.launch { if (store.settings.first().notify == NotifyMode.Instant) Push.fetch(this@LabHudApp) }
            }

            override fun onStop(owner: LifecycleOwner) {
                AppLock.onHidden()
                foreground.value = false
            }
        })
        scope.launch { store.settings.collect { AppLock.mode = it.lockMode } }
        Push.channels(this)
        // a server paired after Instant was chosen gets this phone's push endpoint too
        scope.launch {
            var known: Set<String>? = null
            store.servers.collect { list ->
                val ids = list.map { it.id }.toSet()
                known?.let { old -> list.filter { it.id !in old }.forEach { Push.shareWith(this@LabHudApp, it) } }
                known = ids
            }
        }
        scope.launch {
            combine(foreground, AppLock.unlocked) { fg, open -> fg && open }.collect { run ->
                if (run) repo.run() else repo.stop()
            }
        }
    }
}
