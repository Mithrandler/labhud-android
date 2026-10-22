package io.github.mithrandler.labhud.ui.settings

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import io.github.mithrandler.labhud.data.AppSettings
import io.github.mithrandler.labhud.data.ThemeMode
import io.github.mithrandler.labhud.data.LabRepository
import io.github.mithrandler.labhud.data.LockMode
import io.github.mithrandler.labhud.data.Server
import io.github.mithrandler.labhud.lock.AppLock
import io.github.mithrandler.labhud.ui.CardShape
import io.github.mithrandler.labhud.ui.Lab
import io.github.mithrandler.labhud.ui.LabIcons
import io.github.mithrandler.labhud.ui.Panel
import kotlinx.coroutines.launch

/** The saved servers list: select, rename, remove, add. */
@Composable
fun ServersSection(repo: LabRepository, nav: SettingsNav) {
    val servers by repo.store.servers.collectAsStateWithLifecycle(initialValue = emptyList())
    val current by repo.server.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var removing by remember { mutableStateOf<Server?>(null) }
    var renaming by remember { mutableStateOf<Server?>(null) }

    SettingsPage(section("servers")!!, nav) {
        items(servers, key = { it.id }) { s ->
            Surface(
                onClick = { scope.launch { repo.store.select(s.id) } },
                shape = CardShape, color = Lab.card,
                border = BorderStroke(1.dp, if (s.id == current?.id) Lab.accent.copy(alpha = 0.6f) else Lab.line),
            ) {
                Row(Modifier.fillMaxWidth().padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (s.id == current?.id) Icon(LabIcons.checkCircle, null, tint = Lab.accent, modifier = Modifier.padding(end = 10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.name, fontWeight = FontWeight.SemiBold)
                        Text(s.url + if (s.fingerprint.isNotEmpty()) " · pinned certificate" else "", fontSize = 12.sp, color = Lab.muted)
                        if (s.deviceName.isNotEmpty()) Text("Paired as \"${s.deviceName}\"", fontSize = 12.sp, color = Lab.muted)
                    }
                    IconButton(onClick = { renaming = s }) { Icon(LabIcons.edit, "Rename") }
                    IconButton(onClick = { removing = s }) { Icon(LabIcons.delete, "Remove") }
                }
            }
        }
        item {
            Button(onClick = nav.addServer, modifier = Modifier.fillMaxWidth()) {
                Icon(LabIcons.add, null); Text("  Add a server")
            }
        }
    }

    removing?.let { s ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove ${s.name}?") },
            text = { Text("This phone forgets it and its token. To end the token on the server too, run there: init.py unpair \"${s.deviceName}\".") },
            confirmButton = {
                TextButton(onClick = { removing = null; scope.launch { repo.store.remove(s.id); repo.forget(s.id) } }) { Text("Remove", color = Lab.red) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } },
        )
    }
    renaming?.let { s ->
        var name by remember(s.id) { mutableStateOf(s.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Name") },
            text = { OutlinedTextField(name, { name = it.take(40) }, singleLine = true) },
            confirmButton = { TextButton(onClick = { renaming = null; scope.launch { repo.store.rename(s.id, name.ifBlank { s.name }) } }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
        )
    }
}

/** System, dark, light or HUD: the app's colours (wall mode stays HUD). */
@Composable
fun ThemeSection(repo: LabRepository, nav: SettingsNav) {
    val settings by repo.store.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val scope = rememberCoroutineScope()
    val options = listOf(
        Triple(ThemeMode.System, "System", "Follows the phone's dark mode; colours from the wallpaper (Android 12+)."),
        Triple(ThemeMode.Dark, "Dark", "Always dark, colours from the wallpaper (Android 12+)."),
        Triple(ThemeMode.Light, "Light", "Always light, colours from the wallpaper (Android 12+)."),
        Triple(ThemeMode.Hud, "HUD", "As the wall: black and blue, or the theme the board sets in ⚙ > Dashboard."),
    )
    SettingsPage(section("theme")!!, nav) {
        item {
            Surface(shape = CardShape, color = Lab.card, border = BorderStroke(1.dp, Lab.line)) {
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    options.forEach { (mode, title, hint) ->
                        Row(
                            Modifier.fillMaxWidth().selectable(selected = mode == settings.theme, role = Role.RadioButton) {
                                scope.launch { repo.store.setTheme(mode) }
                            }.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = mode == settings.theme, onClick = null, modifier = Modifier.padding(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(title, fontSize = 15.sp, color = Lab.text)
                                Text(hint, fontSize = 12.sp, color = Lab.muted)
                            }
                        }
                    }
                    Text("Wall mode is always the HUD. Status colours stay green, orange and red.", fontSize = 12.sp, color = Lab.muted,
                        modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 6.dp))
                }
            }
        }
    }
}

/** None, the screen lock or a fingerprint. Any change is confirmed with the lock it turns on (or, when turning it off, the one it had), so a wrong choice never shuts you out of the app. */
@Composable
fun LockSection(repo: LabRepository, nav: SettingsNav) {
    val settings by repo.store.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val scope = rememberCoroutineScope()

    SettingsPage(section("lock")!!, nav) {
        item { LockChoice(settings.lockMode) { scope.launch { repo.store.setLockMode(it) } } }
    }
}

/** The whole wall page on this screen, kept on, for an old phone or tablet on the wall, plus the "start in wall mode" boot switch. */
@Composable
fun WallSection(repo: LabRepository, nav: SettingsNav) {
    val settings by repo.store.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val scope = rememberCoroutineScope()

    SettingsPage(section("wall")!!, nav) {
        item {
            Panel("") {
                Text("The whole wall page on this screen, kept on, for an old phone or tablet on the wall. " +
                    "It is the admin of this phone, and the PIN you unlocked with here carries over. Back leaves it.", fontSize = 13.sp, color = Lab.sub)
                OutlinedButton(onClick = nav.wall, modifier = Modifier.padding(top = 8.dp)) { Text("Open wall mode") }
            }
        }
        item {
            Setting("Glance view", "Big coloured tiles with one number each, the group's count in its title, a large clock. The details open on a tap. " +
                "Needs LabHUD 0.1.12 or later; an older one shows its normal wall.", settings.wallGlance) {
                scope.launch { repo.store.setWallGlance(it) }
            }
        }
        item {
            Setting("Start in wall mode", "LabHUD becomes a home screen app: choose it as Home once, and the wall comes back after every reboot.", settings.wallOnBoot) {
                scope.launch { repo.store.setWallOnBoot(it) }
                nav.homeApp(it)
            }
        }
    }
}

@Composable
private fun LockChoice(current: LockMode, set: (LockMode) -> Unit) {
    val activity = LocalActivity.current as FragmentActivity
    var error by remember { mutableStateOf("") }
    val options = listOf(
        Triple(LockMode.None, "None", "The app opens straight away."),
        Triple(LockMode.ScreenLock, "Phone PIN, pattern or password", "The phone's own screen lock."),
        Triple(LockMode.Fingerprint, "Fingerprint", "With the screen lock as the way out when it fails."),
    )
    Surface(shape = CardShape, color = Lab.card, border = BorderStroke(1.dp, Lab.line)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Text("Lock", fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 14.dp, top = 8.dp))
            Text("Asked when the app opens and after 5 minutes in the background, never per button.",
                fontSize = 12.sp, color = Lab.muted, modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 4.dp))
            options.forEach { (mode, title, hint) ->
                val usable = AppLock.canUse(activity, mode)
                Row(
                    Modifier.fillMaxWidth()
                        .selectable(selected = mode == current, enabled = usable, role = Role.RadioButton) {
                            if (mode == current) return@selectable
                            error = ""
                            AppLock.ask(activity, if (mode == LockMode.None) current else mode,
                                onOk = { set(mode) }, onError = { error = it })
                        }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = mode == current, onClick = null, enabled = usable, modifier = Modifier.padding(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(title, fontSize = 15.sp, color = if (usable) Lab.text else Lab.muted)
                        Text(if (usable) hint else if (mode == LockMode.Fingerprint) "No fingerprint set up on this phone." else "This phone has no screen lock.",
                            fontSize = 12.sp, color = Lab.muted)
                    }
                }
            }
            if (error.isNotEmpty()) Text(error, fontSize = 12.sp, color = Lab.warn, modifier = Modifier.padding(start = 14.dp, top = 4.dp, bottom = 6.dp))
        }
    }
}

@Composable
private fun Setting(title: String, hint: String, on: Boolean, set: (Boolean) -> Unit) {
    Surface(shape = CardShape, color = Lab.card, border = BorderStroke(1.dp, Lab.line), onClick = { set(!on) }) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(title, fontWeight = FontWeight.Medium)
                Text(hint, fontSize = 12.sp, color = Lab.muted)
            }
            Switch(checked = on, onCheckedChange = set)
        }
    }
}
