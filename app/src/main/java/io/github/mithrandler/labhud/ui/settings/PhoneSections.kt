package io.github.mithrandler.labhud.ui.settings

import io.github.mithrandler.labhud.ui.LabScale
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
import io.github.mithrandler.labhud.ui.textScaleOf
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
import io.github.mithrandler.labhud.ui.ViewChoice
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilterChip
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
                border = BorderStroke(LabScale.hairline, if (s.id == current?.id) Lab.accent.copy(alpha = 0.6f) else Lab.line),
            ) {
                Row(Modifier.fillMaxWidth().padding(start = LabScale.space3, top = LabScale.space3, bottom = LabScale.space3, end = LabScale.space1), verticalAlignment = Alignment.CenterVertically) {
                    if (s.id == current?.id) Icon(LabIcons.checkCircle, null, tint = Lab.accent, modifier = Modifier.padding(end = LabScale.space3))
                    Column(Modifier.weight(1f)) {
                        Text(s.name, fontWeight = FontWeight.SemiBold)
                        Text(s.url + if (s.fingerprint.isNotEmpty()) " · pinned certificate" else "", fontSize = LabScale.textSm, color = Lab.muted)
                        if (s.deviceName.isNotEmpty()) Text("Paired as \"${s.deviceName}\"", fontSize = LabScale.textSm, color = Lab.muted)
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
            Surface(shape = CardShape, color = Lab.card, border = BorderStroke(LabScale.hairline, Lab.line)) {
                Column(Modifier.fillMaxWidth().padding(vertical = LabScale.space2)) {
                    options.forEach { (mode, title, hint) ->
                        Row(
                            Modifier.fillMaxWidth().selectable(selected = mode == settings.theme, role = Role.RadioButton) {
                                scope.launch { repo.store.setTheme(mode) }
                            }.padding(horizontal = LabScale.space2, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = mode == settings.theme, onClick = null, modifier = Modifier.padding(LabScale.space2))
                            Column(Modifier.weight(1f)) {
                                Text(title, fontSize = LabScale.textMd, color = Lab.text)
                                Text(hint, fontSize = LabScale.textSm, color = Lab.muted)
                            }
                        }
                    }
                    Text("Wall mode is always the HUD. Status colours stay green, orange and red.", fontSize = LabScale.textSm, color = Lab.muted,
                        modifier = Modifier.padding(start = LabScale.space3, end = LabScale.space3, top = LabScale.space1, bottom = LabScale.space2))
                }
            }
        }
        item {
            Panel("Text", summary = "${settings.textScale}%" + if (settings.monoFont) " · monospace" else "") {
                Text("The font and its size in this app, on this phone only. The wall page keeps its own.", fontSize = LabScale.textSm, color = Lab.muted)
                Row(Modifier.fillMaxWidth().padding(top = LabScale.space2), horizontalArrangement = Arrangement.spacedBy(LabScale.space2)) {
                    FilterChip(selected = !settings.monoFont, onClick = { scope.launch { repo.store.setMonoFont(false) } }, label = { Text("Sans") })
                    FilterChip(selected = settings.monoFont, onClick = { scope.launch { repo.store.setMonoFont(true) } }, label = { Text("Monospace") })
                }
                var drag by remember(settings.textScale) { mutableStateOf(settings.textScale.toFloat()) }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Size", fontSize = LabScale.textSm, color = Lab.sub)
                    androidx.compose.material3.Slider(drag, { drag = it }, valueRange = 85f..130f, steps = 8, modifier = Modifier.weight(1f).padding(horizontal = LabScale.space3),
                        onValueChangeFinished = { scope.launch { repo.store.setTextScale(drag.toInt()) } })
                    Text("${textScaleOf(drag.toInt())}%", fontSize = LabScale.textSm, color = Lab.text)
                }
            }
        }
        item {
            Panel("Opens as", summary = "${settings.phoneView.name}" + if (settings.phoneView == ViewChoice.Auto) " · the board decides for your role" else "") {
                Text("Auto follows the board's view for this phone's role (labhud 0.1.12): Glance is compact tiles without buttons, " +
                    "Actions the cards with their buttons, Full everything. The wall page has its own switch in Wall mode.", fontSize = LabScale.textSm, color = Lab.muted)
                Row(Modifier.fillMaxWidth().padding(top = LabScale.space2), horizontalArrangement = Arrangement.spacedBy(LabScale.space2)) {
                    ViewChoice.entries.forEach { v ->
                        FilterChip(selected = v == settings.phoneView, onClick = { scope.launch { repo.store.setPhoneView(v) } }, label = { Text(v.name) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Lab.accent.copy(alpha = 0.2f), selectedLabelColor = Lab.title))
                    }
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
                    "It is the admin of this phone, and the PIN you unlocked with here carries over. Back leaves it.", fontSize = LabScale.textSm, color = Lab.sub)
                OutlinedButton(onClick = nav.wall, modifier = Modifier.padding(top = LabScale.space2)) { Text("Open wall mode") }
            }
        }
        item {
            Setting("Glance view", "Big coloured tiles with one number each, the group's count in its title, a large clock. The details open on a tap. " +
                "Needs LabHUD 0.1.12 or later; an older one shows its normal wall.", settings.wallGlance) {
                scope.launch { repo.store.setWallGlance(it) }
            }
        }
        item { ScreenScheduleCard(settings.screen) { scope.launch { repo.store.setScreen(it) } } }
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
    Surface(shape = CardShape, color = Lab.card, border = BorderStroke(LabScale.hairline, Lab.line)) {
        Column(Modifier.fillMaxWidth().padding(vertical = LabScale.space2)) {
            Text("Lock", fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = LabScale.space3, top = LabScale.space2))
            Text("Asked when the app opens and after 5 minutes in the background, never per button.",
                fontSize = LabScale.textSm, color = Lab.muted, modifier = Modifier.padding(start = LabScale.space3, end = LabScale.space3, bottom = LabScale.space1))
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
                        .padding(horizontal = LabScale.space2, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = mode == current, onClick = null, enabled = usable, modifier = Modifier.padding(LabScale.space2))
                    Column(Modifier.weight(1f)) {
                        Text(title, fontSize = LabScale.textMd, color = if (usable) Lab.text else Lab.muted)
                        Text(if (usable) hint else if (mode == LockMode.Fingerprint) "No fingerprint set up on this phone." else "This phone has no screen lock.",
                            fontSize = LabScale.textSm, color = Lab.muted)
                    }
                }
            }
            if (error.isNotEmpty()) Text(error, fontSize = LabScale.textSm, color = Lab.warn, modifier = Modifier.padding(start = LabScale.space3, top = LabScale.space1, bottom = LabScale.space2))
        }
    }
}

/** Dims the wall in a window of the night; a touch or a red card wakes it. */
@Composable
private fun ScreenScheduleCard(cur: io.github.mithrandler.labhud.wall.ScreenSchedule, set: (io.github.mithrandler.labhud.wall.ScreenSchedule) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    fun pick(minute: Int, done: (Int) -> Unit) =
        android.app.TimePickerDialog(context, { _, h, m -> done(h * 60 + m) }, minute / 60, minute % 60, true).show()
    Surface(shape = CardShape, color = Lab.card, border = BorderStroke(LabScale.hairline, Lab.line)) {
        Column(Modifier.fillMaxWidth().padding(LabScale.space3)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f).padding(end = LabScale.space3)) {
                    Text("Screen schedule", fontWeight = FontWeight.Medium)
                    Text("Dims the wall at night. A touch wakes it for a minute, a red card for as long as it is red.", fontSize = LabScale.textSm, color = Lab.muted)
                }
                Switch(checked = cur.enabled, onCheckedChange = { set(cur.copy(enabled = it)) })
            }
            if (cur.enabled) {
                Row(Modifier.padding(top = LabScale.space2), horizontalArrangement = Arrangement.spacedBy(LabScale.space2), verticalAlignment = Alignment.CenterVertically) {
                    Text("Dim from", fontSize = LabScale.textSm, color = Lab.sub)
                    OutlinedButton(onClick = { pick(cur.from) { set(cur.copy(from = it)) } }) { Text(io.github.mithrandler.labhud.wall.minuteLabel(cur.from)) }
                    Text("to", fontSize = LabScale.textSm, color = Lab.sub)
                    OutlinedButton(onClick = { pick(cur.to) { set(cur.copy(to = it)) } }) { Text(io.github.mithrandler.labhud.wall.minuteLabel(cur.to)) }
                }
                Text("Brightness while dimmed: ${cur.dim}%", fontSize = LabScale.textSm, color = Lab.sub, modifier = Modifier.padding(top = LabScale.space2))
                androidx.compose.material3.Slider(value = cur.dim.toFloat(), onValueChange = { set(cur.copy(dim = it.toInt())) }, valueRange = 0f..50f)
            }
        }
    }
}

@Composable
private fun Setting(title: String, hint: String, on: Boolean, set: (Boolean) -> Unit) {
    Surface(shape = CardShape, color = Lab.card, border = BorderStroke(LabScale.hairline, Lab.line), onClick = { set(!on) }) {
        Row(Modifier.fillMaxWidth().padding(LabScale.space3), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = LabScale.space3)) {
                Text(title, fontWeight = FontWeight.Medium)
                Text(hint, fontSize = LabScale.textSm, color = Lab.muted)
            }
            Switch(checked = on, onCheckedChange = set)
        }
    }
}
