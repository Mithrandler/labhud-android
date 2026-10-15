package io.github.mithrandler.labhud

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.mithrandler.labhud.lock.AppLock
import io.github.mithrandler.labhud.ui.AddServerScreen
import io.github.mithrandler.labhud.ui.CardScreen
import io.github.mithrandler.labhud.ui.HomeScreen
import io.github.mithrandler.labhud.ui.Lab
import io.github.mithrandler.labhud.ui.LabTheme
import io.github.mithrandler.labhud.ui.ServersScreen
import io.github.mithrandler.labhud.ui.TodayScreen
import io.github.mithrandler.labhud.wall.WallActivity
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : FragmentActivity() {
    /** A labhud:// link this activity was opened with: card/<id>, pair?…, open. */
    private val link = MutableStateFlow<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        link.value = intent?.data
        val repo = (application as LabHudApp).repo
        setContent {
            LabTheme {
                val unlocked by AppLock.unlocked.collectAsState()
                val servers by repo.store.servers.collectAsState(initial = null)
                val pending by link.collectAsState()
                when {
                    servers == null -> Column(Modifier.fillMaxSize().background(Lab.bg)) {}
                    !unlocked -> LockScreen()
                    servers!!.isEmpty() -> AddServerScreen(repo, pending?.takeIf { it.host == "pair" }?.toString(), onBack = null,
                        onDone = { link.value = null })
                    else -> Nav(pending, onLinkUsed = { link.value = null })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        link.value = intent.data
    }

    override fun onResume() {
        super.onResume()
        if (!AppLock.unlocked.value) AppLock.prompt(this) {}
    }

    @Composable
    private fun LockScreen() {
        Column(
            Modifier.fillMaxSize().background(Lab.bg).padding(32.dp),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Default.Lock, null, tint = Lab.title, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(16.dp))
            Text("LabHUD is locked", fontSize = 20.sp)
            Spacer(Modifier.height(24.dp))
            Button(onClick = { AppLock.prompt(this@MainActivity) {} }) { Text("Unlock") }
        }
    }

    @Composable
    private fun Nav(pending: Uri?, onLinkUsed: () -> Unit) {
        val nav = rememberNavController()
        val repo = (application as LabHudApp).repo
        var pairLink by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(pending) {
            val u = pending ?: return@LaunchedEffect
            when (u.host) {
                "card" -> u.pathSegments.firstOrNull()?.let { nav.navigate("card/$it") }
                "pair" -> { pairLink = u.toString(); nav.navigate("add") }
            }
            onLinkUsed()
        }
        NavHost(nav, startDestination = "home", modifier = Modifier.background(Lab.bg)) {
            composable("home") {
                HomeScreen(
                    repo,
                    onCard = { nav.navigate("card/$it") },
                    onToday = { nav.navigate("today") },
                    onServers = { nav.navigate("servers") },
                    onWall = { startActivity(Intent(this@MainActivity, WallActivity::class.java)) },
                    onLock = { AppLock.lockNow() },
                )
            }
            composable("card/{id}") { CardScreen(repo, it.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() }) }
            composable("today") { TodayScreen(repo, onBack = { nav.popBackStack() }, onCard = { id -> nav.navigate("card/$id") }) }
            composable("servers") {
                ServersScreen(
                    repo, onBack = { nav.popBackStack() }, onAdd = { pairLink = null; nav.navigate("add") },
                    onWall = { startActivity(Intent(this@MainActivity, WallActivity::class.java)) },
                    onHomeApp = ::setHomeApp,
                )
            }
            composable("add") {
                AddServerScreen(repo, pairLink, onBack = { nav.popBackStack() }, onDone = { pairLink = null; nav.back(to = "home") })
            }
        }
    }

    /** Turns the wall's home screen entry on or off; Android then asks which home app to use. */
    private fun setHomeApp(on: Boolean) {
        packageManager.setComponentEnabledSetting(
            ComponentName(packageName, "io.github.mithrandler.labhud.WallHome"),
            if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
        if (on) runCatching { startActivity(Intent(android.provider.Settings.ACTION_HOME_SETTINGS)) }
    }
}

private fun NavHostController.back(to: String) {
    if (!popBackStack(to, inclusive = false)) navigate(to)
}
