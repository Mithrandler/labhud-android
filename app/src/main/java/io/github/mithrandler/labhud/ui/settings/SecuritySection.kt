package io.github.mithrandler.labhud.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.LocalContext
import io.github.mithrandler.labhud.data.SecurityState
import io.github.mithrandler.labhud.data.Sso
import io.github.mithrandler.labhud.data.oidcOffered
import io.github.mithrandler.labhud.data.saveTimeouts
import io.github.mithrandler.labhud.data.security
import io.github.mithrandler.labhud.ui.GateDialogs
import io.github.mithrandler.labhud.ui.rememberGate
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mithrandler.labhud.data.LabRepository
import io.github.mithrandler.labhud.ui.Lab
import io.github.mithrandler.labhud.ui.Panel
import kotlinx.coroutines.launch

/**
 * Security: whether this phone is the admin's (sign in with the password and code, or with single
 * sign-on through the browser), the wall's PIN, and for the admin ⚙ > Security as the server keeps
 * it (api/security.py): single sign-on, how long a session and an unlock last (editable here), the
 * networks. What needs typing (the provider, the networks) is done on the wall.
 */
@Composable
fun SecuritySection(repo: LabRepository, nav: SettingsNav) {
    val rights by repo.rights.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val gate = rememberGate(repo)
    var signingIn by remember { mutableStateOf(false) }
    var sso by remember { mutableStateOf(false) }
    var state by remember { mutableStateOf<SecurityState?>(null) }
    var message by remember { mutableStateOf("") }

    fun load() = scope.launch {
        val api = repo.api() ?: return@launch
        runCatching { api.security() }.onSuccess { if (it.ok) state = it else message = it.error.ifBlank { "Refused." } }
            .onFailure { message = it.message ?: "Not loaded." }
    }
    LaunchedEffect(rights.admin) {
        sso = runCatching { repo.api()?.oidcOffered() }.getOrNull() == true
        if (rights.admin) gate.press { load() }
    }

    SettingsPage(section("security")!!, nav) {
        heading("This phone")
        item {
            AccountPanel(rights.admin, rights.pin, onSignIn = { signingIn = true }, onSignOut = { scope.launch { repo.signOut() } },
                onSso = if (sso && !rights.admin) ({ scope.launch { Sso.start(context, repo)?.let { message = it } } }) else null)
        }
        if (message.isNotEmpty()) item { Text(message, color = Lab.warn, fontSize = 13.sp) }
        heading("The wall")
        item {
            Panel("") {
                Text(if (rights.pin) "The screen has a PIN" else "No PIN on the screen", fontWeight = FontWeight.Medium)
                Text(
                    if (rights.pin) "Buttons and settings ask for it (or your fingerprint, once kept), and it lasts ${state?.pinMinutes ?: 2} min without a press."
                    else "Anyone at the screen can use its buttons.",
                    fontSize = 12.sp, color = Lab.muted,
                )
            }
        }
        if (rights.admin) {
            val st = state
            if (st == null) item { Unlock { gate.press { load() } } }
            else {
                heading("Single sign-on")
                item { SsoPanel(st) }
                heading("Session timeout")
                item {
                    TimeoutPanel(st) { hours, minutes ->
                        gate.press {
                            scope.launch {
                                val r = runCatching { repo.api()?.saveTimeouts(hours, minutes) }.getOrNull()
                                message = if (r?.ok == true) "Saved." else r?.error?.ifBlank { null } ?: "Not saved."
                                if (r?.ok == true) state = st.copy(sessionHours = hours, pinMinutes = minutes)
                            }
                        }
                    }
                }
                heading("Trusted networks")
                item { NetworksPanel(st) }
            }
        }
        item { WallOnly("⚙ > Security", listOf("The admin account and its authenticator (TOTP)", "Setting or removing the PIN",
            "Signing out the other sessions", "The single sign-on provider and the networks")) }
    }
    if (signingIn) SignInDialog(repo, onDone = { signingIn = false })
    GateDialogs(gate, nav.signIn)
}

@Composable
private fun SsoPanel(st: SecurityState) {
    Panel("") {
        val o = st.oidc
        Text(if (o != null) "On" else "Off", fontWeight = FontWeight.Medium)
        Text(
            if (o != null) "Through ${o.issuer.substringAfter("://").substringBefore("/")}: " +
                (o.allow + o.allowGroups.map { "group $it" }).joinToString(", ") + " sign in as the admin. The password and code still work."
            else "Sign in with Authentik (or another OpenID Connect provider) instead of the password and code. Set it up on the wall.",
            fontSize = 12.sp, color = Lab.muted,
        )
        if (o != null && !st.secretSet) Text("No client secret is kept: a confidential provider will refuse.", fontSize = 12.sp, color = Lab.warn)
    }
}

private val HOURS = listOf(1 to "1 h", 4 to "4 h", 12 to "12 h", 24 to "1 day", 72 to "3 days", 168 to "7 days", 336 to "14 days", 720 to "30 days")
private val MINUTES = listOf(1 to "1 min", 2 to "2 min", 5 to "5 min", 10 to "10 min", 15 to "15 min", 30 to "30 min", 60 to "1 h", 120 to "2 h")

@Composable
private fun TimeoutPanel(st: SecurityState, onSave: (Int, Int) -> Unit) {
    var hours by remember(st) { mutableIntStateOf(st.sessionHours) }
    var minutes by remember(st) { mutableIntStateOf(st.pinMinutes) }
    Panel("") {
        Text("A browser stays signed in", fontWeight = FontWeight.Medium)
        Chips(HOURS, hours) { hours = it }
        Text("A screen stays open without the PIN", fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 8.dp))
        Chips(MINUTES, minutes) { minutes = it }
        Text("Both count from the last use.", fontSize = 12.sp, color = Lab.muted)
        if (!st.kept) Text("Take over the config on the wall first (⚙ > System), so LabHUD can write it.", fontSize = 12.sp, color = Lab.warn)
        Button(enabled = st.kept && (hours != st.sessionHours || minutes != st.pinMinutes), onClick = { onSave(hours, minutes) },
            modifier = Modifier.padding(top = 8.dp)) { Text("Save") }
    }
}

@Composable
private fun Chips(options: List<Pair<Int, String>>, current: Int, pick: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (v, label) ->
            FilterChip(selected = v == current, onClick = { pick(v) }, label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Lab.accent.copy(alpha = 0.2f), selectedLabelColor = Lab.title))
        }
    }
}

@Composable
private fun NetworksPanel(st: SecurityState) {
    Panel("") {
        Text("This phone comes from ${st.yourIp}" + if (st.viaProxy) " (through a trusted proxy)" else "", fontWeight = FontWeight.Medium)
        Text("Admin networks: " + st.adminNetworks.ifEmpty { listOf("anywhere") }.joinToString(", "), fontSize = 13.sp, color = Lab.sub)
        Text("Trusted proxies: " + st.trustedProxies.ifEmpty { listOf("none") }.joinToString(", "), fontSize = 13.sp, color = Lab.sub)
        Text("Outside the admin networks nobody signs in, and a phone signed in only looks.", fontSize = 12.sp, color = Lab.muted)
    }
}

/** Signed in as the admin or only looking, with the one button that changes it. */
@Composable
internal fun AccountPanel(admin: Boolean, pin: Boolean, onSignIn: () -> Unit, onSignOut: () -> Unit, onSso: (() -> Unit)? = null) {
    Panel("") {
        Text(if (admin) "Signed in as the admin" else "Only looking", fontWeight = FontWeight.Medium)
        Text(
            if (admin) "The buttons work here" + (if (pin) ", after the wall's PIN (or your fingerprint)." else ".")
            else "Sign in as LabHUD's admin (user, password and the authenticator code) for the buttons and the rest of the settings.",
            fontSize = 12.sp, color = Lab.muted,
        )
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (admin) TextButton(onClick = onSignOut) { Text("Sign out", color = Lab.red) }
            else Button(onClick = onSignIn) { Text("Sign in") }
            if (!admin && onSso != null) OutlinedButton(onClick = onSso) { Text("Single sign-on") }
        }
    }
}

/** User, password and the authenticator code: this phone becomes the admin's (POST /api/v1/admin-login). */
@Composable
internal fun SignInDialog(repo: LabRepository, onDone: () -> Unit) {
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
                    visualTransformation = PasswordVisualTransformation())
                OutlinedTextField(code, { code = it.filter(Char::isDigit).take(8) }, singleLine = true, label = { Text("Authenticator code") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
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
