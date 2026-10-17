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
fun SettingsScreen(
    repo: LabRepository,
    onAdd: () -> Unit,
    onWall: () -> Unit,
    onHomeApp: (Boolean) -> Unit,
    onManage: () -> Unit,
    signInFirst: Boolean = false,
) {
    val rights by repo.rights.collectAsState()
    var signingIn by remember { mutableStateOf(signInFirst) }
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
            item { Text("ACCOUNT", style = Heading) }
            item {
                Panel("") {
                    Text(if (rights.admin) "Signed in as the admin" else "Only looking", fontWeight = FontWeight.Medium)
                    Text(
                        if (rights.admin) "The buttons work here" + (if (rights.pin) ", after the wall's PIN (or your fingerprint)." else ".")
                        else "Sign in as LabHUD's admin (user, password and the authenticator code) for the buttons and Server management.",
                        fontSize = 12.sp, color = Lab.muted,
                    )
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (rights.admin) {
                            OutlinedButton(onClick = onManage) { Text("Server management") }
                            TextButton(onClick = { scope.launch { repo.signOut() } }) { Text("Sign out", color = Lab.red) }
                        } else Button(onClick = { signingIn = true }) { Text("Sign in") }
                    }
                }
            }
            item { Text("NOTIFICATIONS", style = Heading, modifier = Modifier.padding(top = 16.dp)) }
            item { NotifyChoice(repo, settings.notify, rights.admin) }
            item { Text("SERVERS", style = Heading, modifier = Modifier.padding(top = 16.dp)) }
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
                Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                    Icon(LabIcons.add, null); Text("  Add a server")
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

    if (signingIn) SignInDialog(repo, onDone = { signingIn = false })
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

/** User, password and the authenticator code: this phone becomes the admin's (POST /api/v1/admin-login). */
@Composable
private fun SignInDialog(repo: LabRepository, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var user by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Sign in as the admin") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(user, { user = it.take(64) }, singleLine = true, label = { Text("User") })
                OutlinedTextField(password, { password = it.take(256) }, singleLine = true, label = { Text("Password") },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                OutlinedTextField(code, { code = it.filter(Char::isDigit).take(8) }, singleLine = true, label = { Text("Authenticator code") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword))
                if (error.isNotEmpty()) Text(error, color = Lab.warn, fontSize = 13.sp)
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && user.isNotBlank() && password.isNotEmpty() && code.length >= 6, onClick = {
                busy = true
                scope.launch {
                    val e = repo.signIn(user.trim(), password, code)
                    busy = false
                    if (e == null) onDone() else { error = e; code = "" }
                }
            }) { Text(if (busy) "…" else "Sign in") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } },
    )
}

/**
 * Instant (UnifiedPush through the ntfy app: labhud wakes this app, which reads the news itself),
 * ntfy only (the ntfy app shows labhud's own messages), or off. labhud's own `via` decides whether
 * the app is woken at all; the admin can change it here.
 */
@Composable
private fun NotifyChoice(repo: LabRepository, mode: io.github.mithrandler.labhud.data.NotifyMode, admin: Boolean) {
    val activity = LocalActivity.current as FragmentActivity
    val scope = rememberCoroutineScope()
    var via by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }
    androidx.compose.runtime.LaunchedEffect(mode) {
        via = runCatching { repo.api()?.notes(Long.MAX_VALUE / 2)?.via }.getOrNull()
    }
    val ask = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }
    fun pick(m: io.github.mithrandler.labhud.data.NotifyMode) {
        note = ""
        scope.launch {
            repo.store.setNotify(m)
            if (m == io.github.mithrandler.labhud.data.NotifyMode.Instant) {
                io.github.mithrandler.labhud.push.Push.channels(activity)
                if (android.os.Build.VERSION.SDK_INT >= 33) ask.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                io.github.mithrandler.labhud.push.Push.enable(activity) { ok ->
                    if (!ok) note = "No UnifiedPush app on this phone. Install ntfy (Play Store or F-Droid), open it once, then choose Instant again."
                    else io.github.mithrandler.labhud.push.Push.fetch(activity)
                }
            } else io.github.mithrandler.labhud.push.Push.disable(activity)
        }
    }
    val options = listOf(
        Triple(io.github.mithrandler.labhud.data.NotifyMode.Instant, "Instant",
            "Through the ntfy app (UnifiedPush). LabHUD only knocks; this app reads the news itself, at home or on the VPN."),
        Triple(io.github.mithrandler.labhud.data.NotifyMode.Ntfy, "ntfy only", "The ntfy app shows LabHUD's own messages, if a topic is set on the wall."),
        Triple(io.github.mithrandler.labhud.data.NotifyMode.Off, "Off", "Nothing on this phone."),
    )
    Surface(shape = CardShape, color = Lab.card, border = BorderStroke(1.dp, Lab.line)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            options.forEach { (m, title, hint) ->
                Row(
                    Modifier.fillMaxWidth().selectable(selected = m == mode, role = Role.RadioButton) { if (m != mode) pick(m) }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = m == mode, onClick = null, modifier = Modifier.padding(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(title, fontSize = 15.sp)
                        Text(hint, fontSize = 12.sp, color = Lab.muted)
                    }
                }
            }
            if (note.isNotEmpty()) Text(note, fontSize = 12.sp, color = Lab.warn, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
            if (mode == io.github.mithrandler.labhud.data.NotifyMode.Instant && via == "ntfy") {
                Text("LabHUD sends its news only to ntfy now, so this app is not woken.", fontSize = 12.sp, color = Lab.warn,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
                if (admin) TextButton(onClick = {
                    scope.launch {
                        val api = repo.api() ?: return@launch
                        val r = runCatching { api.saveNotify(api.alertSetup(), "both") }.getOrNull()
                        if (r?.ok == true) via = "both" else note = r?.error?.ifBlank { null } ?: "Not saved (unlock with the PIN first?)"
                    }
                }, modifier = Modifier.padding(horizontal = 6.dp)) { Text("Send to the app too") }
            }
        }
    }
}
