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
import kotlinx.coroutines.launch

/**
 * Wall mode: the wall page on the whole screen, kept on, without the system bars. It asks for no
 * unlock (it is a wall: labhud's own PIN guards its buttons); leaving it to the app does.
 * Also the home screen activity when "Start in wall mode" is on, so it comes back after a reboot.
 */
class WallActivity : ComponentActivity() {
    private var web: WebView? = null

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
            web = wallWebView(this@WallActivity, server, token).also { root.addView(it) }
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isTaskRoot) startActivity(android.content.Intent(this@WallActivity, io.github.mithrandler.labhud.MainActivity::class.java))
                finish()
            }
        })
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
