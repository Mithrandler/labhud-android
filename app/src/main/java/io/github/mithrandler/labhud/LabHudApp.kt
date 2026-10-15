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
            }

            override fun onStop(owner: LifecycleOwner) {
                AppLock.onHidden()
                foreground.value = false
            }
        })
        scope.launch { store.settings.collect { AppLock.required = it.requireUnlock } }
        scope.launch {
            combine(foreground, AppLock.unlocked) { fg, open -> fg && open }.collect { run ->
                if (run) repo.run() else repo.stop()
            }
        }
    }
}
