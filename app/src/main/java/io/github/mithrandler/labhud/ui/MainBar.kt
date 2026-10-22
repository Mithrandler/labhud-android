package io.github.mithrandler.labhud.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mithrandler.labhud.R
import io.github.mithrandler.labhud.data.LabRepository

/** Where the top bar's search, bell and menu lead: the same on the four main screens. */
class BarNav(
    val onCard: (String) -> Unit,
    /** A wall page picked in the palette: Status opens on it. */
    val onPage: (String) -> Unit,
    /** A ⚙ section by its id. */
    val onSettings: (String) -> Unit,
    /** A card's button from the palette: its screen opens and asks there. */
    val onAction: (card: String, action: String) -> Unit,
    val onWall: () -> Unit,
    /** The ⚙ list itself (Settings moved from the bottom bar into the menu) and the About screen. */
    val onOpenSettings: () -> Unit,
    val onAbout: () -> Unit,
    val onLock: (() -> Unit)?,
)

/**
 * The top bar of Status, Devices, Activity and Settings: the mark, the screen's [title], the internet's
 * rates, the live dot, then search, the bell and the menu. Their sheets open from here, over any screen.
 * [extra] goes in front of the icons.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTopBar(
    repo: LabRepository,
    nav: BarNav,
    title: @Composable () -> Unit,
    extra: @Composable RowScope.() -> Unit = {},
) {
    val st by repo.state.collectAsStateWithLifecycle()
    val conn by repo.conn.collectAsStateWithLifecycle()
    val server by repo.server.collectAsStateWithLifecycle()
    val rights by repo.rights.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    var bell by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf(false) }
    val s = st
    val count = (s?.alerts?.size ?: 0) + (s?.problems?.size ?: 0)
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Lab.bg),
        // labhud's mark, as on the wall's top bar
        navigationIcon = {
            Image(painterResource(R.drawable.brand_mark), "LabHUD", Modifier.padding(start = 16.dp).width(26.dp).height(22.dp))
        },
        title = title,
        actions = {
            extra()
            s?.internet?.let { InternetMark(it) }
            if (s != null) LiveMark(s, conn)
            IconButton(onClick = { search = true }) { Icon(LabIcons.search, "Search") }
            IconButton(onClick = { bell = true }) {
                BadgedBox(badge = { if (count > 0) Badge(containerColor = Lab.red) { Text("$count") } }) {
                    Icon(LabIcons.bell, "Alerts")
                }
            }
            // button and menu in one Box: the menu opens from the dots, its right edge on theirs
            Box {
            IconButton(onClick = { menu = true }) { Icon(LabIcons.more, "More") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = Lab.card,
                border = BorderStroke(1.dp, Lab.line)) {
                DropdownMenuItem(text = { Text("Settings") }, leadingIcon = { Icon(LabIcons.settings, null) },
                    onClick = { menu = false; nav.onOpenSettings() })
                DropdownMenuItem(text = { Text("Wall mode") }, leadingIcon = { Icon(LabIcons.wall, null) },
                    onClick = { menu = false; nav.onWall() })
                DropdownMenuItem(text = { Text("About") }, leadingIcon = { Icon(LabIcons.info, null) },
                    onClick = { menu = false; nav.onAbout() })
                nav.onLock?.let { lock ->
                    DropdownMenuItem(text = { Text("Lock now") }, leadingIcon = { Icon(LabIcons.lock, null) },
                        onClick = { menu = false; lock() })
                }
            }
            }
            if (bell && s != null) BellSheet(s, onDismiss = { bell = false }, onCard = { bell = false; nav.onCard(it) })
            if (search && s != null) PaletteSheet(s, rights.admin, server, onDismiss = { search = false },
                onPage = { search = false; nav.onPage(it) }, onCard = { search = false; nav.onCard(it) },
                onAction = { c, a -> search = false; nav.onAction(c, a) }, onSection = { search = false; nav.onSettings(it) })
        },
    )
}
