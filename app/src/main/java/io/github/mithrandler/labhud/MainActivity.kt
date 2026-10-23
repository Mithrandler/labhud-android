package io.github.mithrandler.labhud

import android.content.ComponentName
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import io.github.mithrandler.labhud.ui.LabIcons
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxHeight
import io.github.mithrandler.labhud.ui.CompactTabs
import androidx.compose.material3.Scaffold
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.mithrandler.labhud.lock.AppLock
import io.github.mithrandler.labhud.ui.AddServerScreen
import io.github.mithrandler.labhud.ui.CardScreen
import io.github.mithrandler.labhud.ui.AboutScreen
import io.github.mithrandler.labhud.ui.ActivityScreen
import io.github.mithrandler.labhud.ui.AgentScreen
import io.github.mithrandler.labhud.ui.BarNav
import io.github.mithrandler.labhud.ui.DevicesScreen
import io.github.mithrandler.labhud.ui.GameScreen
import io.github.mithrandler.labhud.ui.SettingsScreen
import io.github.mithrandler.labhud.ui.settings.BrandMark
import io.github.mithrandler.labhud.ui.settings.SettingsNav
import io.github.mithrandler.labhud.ui.settings.section
import io.github.mithrandler.labhud.ui.StatusScreen
import io.github.mithrandler.labhud.ui.ZoneScreen
import io.github.mithrandler.labhud.ui.HostHistoryScreen
import io.github.mithrandler.labhud.ui.Lab
import io.github.mithrandler.labhud.ui.LabTheme
import io.github.mithrandler.labhud.ui.isDarkTheme
import io.github.mithrandler.labhud.data.AppSettings
import io.github.mithrandler.labhud.data.BoardTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.compose.runtime.DisposableEffect
import io.github.mithrandler.labhud.wall.WallActivity
import kotlinx.coroutines.flow.MutableStateFlow
import android.widget.Toast
import io.github.mithrandler.labhud.data.LabRepository
import io.github.mithrandler.labhud.data.Sso

class MainActivity : FragmentActivity() {
    /** A labhud:// link this activity was opened with: card/<id>, pair?…, settings[/<id>], oidc?r=…, open. */
    private val link = MutableStateFlow<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        link.value = intent?.data
        val repo = (application as LabHudApp).repo
        setContent {
            val look by repo.store.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
            val darkBars = isDarkTheme(look.theme)
            DisposableEffect(darkBars) {
                enableEdgeToEdge(
                    statusBarStyle = if (darkBars) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT) else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
                    navigationBarStyle = if (darkBars) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT) else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
                )
                onDispose {}
            }
            val board by remember { repo.state.map { it?.theme ?: BoardTheme() }.distinctUntilChanged() }
                .collectAsStateWithLifecycle(initialValue = BoardTheme())
            LabTheme(look.theme, board, look.textScale, look.monoFont) {
                val unlocked by AppLock.unlocked.collectAsStateWithLifecycle()
                val servers by repo.store.servers.collectAsStateWithLifecycle(initialValue = null)
                val settings by repo.store.settings.collectAsStateWithLifecycle(initialValue = null)
                val pending by link.collectAsStateWithLifecycle()
                // the lock follows the stored choice before anything is shown, and asks by itself
                settings?.let { AppLock.mode = it.lockMode }
                LaunchedEffect(unlocked, settings?.lockMode) { if (settings != null && !unlocked) AppLock.prompt(this@MainActivity) {} }
                when {
                    servers == null || settings == null -> Column(Modifier.fillMaxSize().background(Lab.bg)) {}
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

    /** labhud://oidc?r=… single sign-on comes back from the browser; Settings › Security shows the result. */
    private suspend fun oidcReturn(u: Uri, repo: LabRepository, nav: NavHostController) {
        val said = Sso.finish(u, repo)
        Toast.makeText(this, said, Toast.LENGTH_LONG).show()
        nav.navigate("settings/security")
    }

    @Composable
    private fun LockScreen() {
        Column(
            Modifier.fillMaxSize().background(Lab.bg).padding(32.dp),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BrandMark(56.dp)
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(LabIcons.lock, null, tint = Lab.muted, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("LabHUD is locked", fontSize = 20.sp)
            }
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
                "settings" -> u.pathSegments.firstOrNull()?.let { nav.navigate("settings/$it") } ?: nav.tab(SETTINGS)
                "oidc" -> oidcReturn(u, repo, nav)
            }
            onLinkUsed()
        }
        val bar = remember(nav, AppLock.on) { BarNav(
            onCard = { nav.navigate("card/$it") },
            onPage = { repo.pagePick.value = it; nav.tab("home") },
            onSettings = { nav.navigate("settings/$it") },
            onAction = { card, action -> nav.navigate("card/$card?ask=" + Uri.encode(action)) },
            onWall = { startActivity(Intent(this@MainActivity, WallActivity::class.java)) },
            onOpenSettings = { nav.navigate(SETTINGS) { launchSingleTop = true } },
            onAbout = { nav.navigate("about") { launchSingleTop = true } },
            onLock = if (AppLock.on) ({ AppLock.lockNow() }) else null,
        ) }
        val tabItems = remember { TABS.map { Triple(it.route, it.label, it.icon) } }
        val route = nav.currentBackStackEntryAsState().value?.destination?.route
        val landscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val tabsShown = TABS.any { it.route == route }
        androidx.compose.runtime.CompositionLocalProvider(io.github.mithrandler.labhud.ui.LocalTileH provides (if (landscape) 84.dp else 112.dp)) {
        Scaffold(
            containerColor = Lab.bg,
            contentWindowInsets = WindowInsets(0),
            // icons only and thin; on a wide screen they stand in a column at the side, so the height is the cards'
            bottomBar = { if (tabsShown && !landscape) CompactTabs(tabItems, route, false) { nav.tab(it) } },
        ) { pad ->
          androidx.compose.foundation.layout.Row(Modifier.fillMaxSize().padding(pad).consumeWindowInsets(pad)) {
            if (tabsShown && landscape) CompactTabs(tabItems, route, true) { nav.tab(it) }
            NavHost(nav, startDestination = "home",
                enterTransition = { EnterTransition.None }, exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None }, popExitTransition = { ExitTransition.None },
                modifier = Modifier.weight(1f).fillMaxHeight().background(Lab.bg)) {
                composable("home") {
                    StatusScreen(repo, bar, onGame = { nav.navigate("game/$it") }, onServers = { nav.navigate("settings/servers") })
                }
                composable("card/{id}?ask={ask}", arguments = listOf(navArgument("ask") { type = NavType.StringType; defaultValue = "" })) {
                    CardScreen(repo, it.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() },
                        onHistory = { id -> nav.navigate("host/$id") }, onSignIn = { nav.navigate("signin") },
                        askFirst = it.arguments?.getString("ask").orEmpty(), onCard = { id -> nav.navigate("card/$id") })
                }
                composable("game/{id}") {
                    GameScreen(repo, it.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() }, onSignIn = { nav.navigate("signin") })
                }
                composable("host/{id}") {
                    HostHistoryScreen(repo, it.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() },
                        onCard = { id -> nav.navigate("card/$id") })
                }
                composable("devices") { DevicesScreen(repo, bar) }
                composable("zone") { ZoneScreen(repo, bar, onSignIn = { nav.navigate("signin") }) }
                composable("activity") { ActivityScreen(repo, bar) }
                composable("about") { AboutScreen(repo, onBack = { nav.popBackStack() }) }
                composable(SETTINGS) { Settings(nav, bar, signIn = false) }
                // Settings opened over a card or a game by "Sign in" from a button: Back returns there
                composable("signin") { Settings(nav, bar, signIn = true) }
                // one settings section on its own screen: ui/settings/Sections.kt lists them
                composable("settings/{id}") {
                    val s = section(it.arguments?.getString("id").orEmpty())
                    if (s == null) LaunchedEffect(Unit) { nav.popBackStack() }
                    else s.screen(repo, SettingsNav(
                        back = { nav.popBackStack() },
                        signIn = { nav.navigate("signin") },
                        agent = { id -> nav.navigate("agent/$id") },
                        addServer = { nav.navigate("add") },
                        wall = { startActivity(Intent(this@MainActivity, WallActivity::class.java)) },
                        homeApp = ::setHomeApp,
                    ))
                }
                composable("agent/{id}") {
                    AgentScreen(repo, it.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() }, onSignIn = { nav.navigate("signin") })
                }
                composable("add") {
                    AddServerScreen(repo, pairLink, onBack = { nav.popBackStack() }, onDone = { pairLink = null; nav.back(to = "home") })
                }
            }
          }
        }
        }
    }

    @Composable
    private fun Settings(nav: NavHostController, bar: BarNav, signIn: Boolean) {
        val repo = (application as LabHudApp).repo
        SettingsScreen(repo, bar, onOpen = { nav.navigate("settings/$it") }, signInFirst = signIn)
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

/** The places of the bottom bar (Settings, Wall mode and About are in the top bar's menu); a card or "add a server" opens over them, without the bar. */
private class Tab(val route: String, val label: String, val icon: ImageVector)

private const val SETTINGS = "settings"

private val TABS = listOf(
    Tab("home", "Status", LabIcons.status),
    Tab("devices", "Devices", LabIcons.devices),
    Tab("zone", "Servers", LabIcons.servers),
    Tab("activity", "Activity", LabIcons.activity),
)

/** Switches tab the usual way: one copy of each, Back from any tab goes Home, each keeps its scroll. */
private fun NavHostController.tab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

private fun NavHostController.back(to: String) {
    if (!popBackStack(to, inclusive = false)) navigate(to)
}
