package io.github.mithrandler.labhud.wall

import android.os.Bundle
import android.view.WindowManager
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import io.github.mithrandler.labhud.LabHudApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Wall mode: the wall page on the whole screen, kept on, without the system bars. It asks for no
 * unlock (it is a wall: labhud's own PIN guards its buttons); leaving it to the app does.
 * Also the home screen activity when "Start in wall mode" is on, so it comes back after a reboot.
 */
class WallActivity : ComponentActivity() {
    private var web: WebView? = null
    private var touchedAt = 0L
    private var red = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        val root = FrameLayout(this).apply { setBackgroundColor(android.graphics.Color.BLACK) }
        setContentView(root)
        root.addView(exitButton())
        val repo = (application as LabHudApp).repo
        lifecycleScope.launch {
            val server = repo.store.currentNow()
            val token = server?.let { repo.store.token(it.id) }
            if (server == null || token == null) {
                root.addView(TextView(this@WallActivity).apply {
                    text = "Pair a LabHUD in the app first."
                    setTextColor(android.graphics.Color.WHITE)
                    setPadding(48, 48, 48, 48)
                })
                return@launch
            }
            val glance = io.github.mithrandler.labhud.ui.wallGlance(repo.store.settings.first().wallGlance, repo.state.value?.view)
            web = wallWebView(this@WallActivity, server, token, repo.unlockTicket(), glance).also { root.addView(it); exit?.bringToFront() }
        }
        watchSchedule(repo)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = leave()
        })
    }

    private fun leave() {
        if (isTaskRoot) startActivity(android.content.Intent(this, io.github.mithrandler.labhud.MainActivity::class.java))
        finish()
    }

    private var exit: TextView? = null
    private val hideExit = Runnable { exit?.animate()?.alpha(0f)?.withEndAction { exit?.visibility = android.view.View.GONE }?.setDuration(250)?.start() }

    /** A button to leave the wall, there only for a few seconds after a touch: the wall stays clean, and the way out is not a hidden gesture. */
    private fun exitButton(): TextView {
        val d = resources.displayMetrics.density
        return TextView(this).apply {
            text = "✕  Exit wall mode"
            setTextColor(android.graphics.Color.WHITE)
            textSize = 14f
            setPadding((16 * d).toInt(), (10 * d).toInt(), (16 * d).toInt(), (10 * d).toInt())
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 24 * d
                setColor(0xCC202020.toInt())
            }
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.BOTTOM or android.view.Gravity.START).apply { setMargins((16 * d).toInt(), 0, 0, (28 * d).toInt()) }
            visibility = android.view.View.GONE
            setOnClickListener { leave() }
            exit = this
        }
    }

    private fun showExit() {
        val b = exit ?: return
        b.removeCallbacks(hideExit)
        b.animate().cancel()
        b.alpha = 1f
        b.visibility = android.view.View.VISIBLE
        b.postDelayed(hideExit, 4_000)
    }

    /** A touch anywhere restarts the minute the screen stays awake; the page still gets the touch. */
    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        touchedAt = android.os.SystemClock.elapsedRealtime()
        if (ev.actionMasked == android.view.MotionEvent.ACTION_DOWN) showExit()
        applyBrightness(repo())
        return super.dispatchTouchEvent(ev)
    }

    private fun repo() = (application as LabHudApp).repo
    private var schedule = ScreenSchedule()

    private fun applyBrightness(repo: io.github.mithrandler.labhud.data.LabRepository) {
        val cal = java.util.Calendar.getInstance()
        val minute = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
        val b = schedule.brightness(minute, touchedAt, android.os.SystemClock.elapsedRealtime(), red)
        window.attributes = window.attributes.apply {
            screenBrightness = if (b < 0f) WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE else b.coerceAtLeast(ScreenSchedule.MIN)
        }
    }

    /**
     * The schedule is read live from the settings; the repo's stream is off while the wall shows (the page
     * has its own), so a red card is looked for here every 20 seconds, only while a schedule is on.
     */
    private fun watchSchedule(repo: io.github.mithrandler.labhud.data.LabRepository) {
        lifecycleScope.launch { repo.store.settings.collect { schedule = it.screen; applyBrightness(repo) } }
        lifecycleScope.launch {
            while (true) {
                if (schedule.enabled) {
                    red = runCatching { anyRed(repo.api()?.state()) }.getOrDefault(red)
                }
                applyBrightness(repo)
                kotlinx.coroutines.delay(if (schedule.enabled) 20_000L else 60_000L)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        (application as LabHudApp).wallShown.value = true
        web?.onResume()
    }

    override fun onPause() {
        web?.onPause()
        (application as LabHudApp).wallShown.value = false
        super.onPause()
    }

    override fun onDestroy() {
        web?.destroy()
        super.onDestroy()
    }
}
