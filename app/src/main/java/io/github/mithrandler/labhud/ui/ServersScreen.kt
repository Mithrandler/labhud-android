package io.github.mithrandler.labhud.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mithrandler.labhud.BuildConfig
import io.github.mithrandler.labhud.data.AppSettings
import io.github.mithrandler.labhud.data.LockMode
import io.github.mithrandler.labhud.lock.AppLock
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.RadioButton
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.semantics.Role
import androidx.fragment.app.FragmentActivity
import io.github.mithrandler.labhud.data.LabRepository
import io.github.mithrandler.labhud.data.Server
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServersScreen(
    repo: LabRepository,
    onAdd: () -> Unit,
    onWall: () -> Unit,
    onHomeApp: (Boolean) -> Unit,
) {
    val servers by repo.store.servers.collectAsState(initial = emptyList())
    val current by repo.server.collectAsState()
    val settings by repo.store.settings.collectAsState(initial = AppSettings())
    val scope = rememberCoroutineScope()
    var removing by remember { mutableStateOf<Server?>(null) }
    var renaming by remember { mutableStateOf<Server?>(null) }

    Scaffold(
        containerColor = Lab.bg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Lab.bg),
                title = { Text("Settings") },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { Text("SERVERS", style = Heading) }
            items(servers, key = { it.id }) { s ->
                Surface(
                    onClick = { scope.launch { repo.store.select(s.id) } },
                    shape = CardShape, color = Lab.card,
                    border = BorderStroke(1.dp, if (s.id == current?.id) Lab.accent.copy(alpha = 0.6f) else Lab.line),
                ) {
                    Row(Modifier.fillMaxWidth().padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (s.id == current?.id) Icon(Icons.Default.CheckCircle, null, tint = Lab.accent, modifier = Modifier.padding(end = 10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.name, fontWeight = FontWeight.SemiBold)
                            Text(s.url + if (s.fingerprint.isNotEmpty()) " · pinned certificate" else "", fontSize = 12.sp, color = Lab.muted)
                            if (s.deviceName.isNotEmpty()) Text("Paired as \"${s.deviceName}\"", fontSize = 12.sp, color = Lab.muted)
                        }
                        IconButton(onClick = { renaming = s }) { Icon(Icons.Default.Edit, "Rename") }
                        IconButton(onClick = { removing = s }) { Icon(Icons.Default.Delete, "Remove") }
                    }
                }
            }
            item {
                Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, null); Text("  Add a server")
                }
            }
            item { Text("THIS PHONE", style = Heading, modifier = Modifier.padding(top = 16.dp)) }
            item { LockChoice(settings.lockMode) { scope.launch { repo.store.setLockMode(it) } } }
            item { Text("WALL MODE", style = Heading, modifier = Modifier.padding(top = 16.dp)) }
            item {
                Panel("") {
                    Text("The whole wall page on this screen, kept on, for an old phone or tablet on the wall. " +
                        "Back leaves it.", fontSize = 13.sp, color = Lab.sub)
                    OutlinedButton(onClick = onWall, modifier = Modifier.padding(top = 8.dp)) { Text("Open wall mode") }
                }
            }
            item {
                Setting("Start in wall mode", "LabHUD becomes a home screen app: choose it as Home once, and the wall comes back after every reboot.", settings.wallOnBoot) {
                    scope.launch { repo.store.setWallOnBoot(it) }
                    onHomeApp(it)
                }
            }
            item {
                Text("LabHUD ${BuildConfig.VERSION_NAME} · github.com/Mithrandler/labhud-android", fontSize = 12.sp, color = Lab.muted,
                    modifier = Modifier.padding(top = 24.dp))
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

/**
 * None, the screen lock or a fingerprint. Any change is confirmed with the lock it turns on (or,
 * when turning it off, the one it had), so a wrong choice never shuts you out of the app.
 */
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
