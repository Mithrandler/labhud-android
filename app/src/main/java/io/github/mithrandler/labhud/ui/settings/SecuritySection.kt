package io.github.mithrandler.labhud.ui.settings

import io.github.mithrandler.labhud.ui.LabScale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
    val rights by repo.rights.collectAsStateWithLifecycle()
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
        item {
            AccountPanel(rights.admin, rights.pin, rights.user, onSignIn = { signingIn = true }, onSignOut = { scope.launch { repo.signOut() } },
                onSso = if (sso && !rights.admin) ({ scope.launch { Sso.start(context, repo)?.let { message = it } } }) else null,
                onLock = if (rights.admin && rights.pin) ({ repo.lockNow(); message = "Locked: the next button asks for the PIN." }) else null)
        }
        if (message.isNotEmpty()) item { Text(message, color = Lab.warn, fontSize = LabScale.textSm) }
        if (rights.admin) {
            item { SessionsPanel(repo, gate) }
            item { PairedPhonesPanel(repo, gate) }
        }
        item {
            CollapsiblePanel("PIN on the wall", if (rights.pin) "Set: ${state?.pinMinutes ?: 2} min without a press" else "None") {
                Text(
                    if (rights.pin) "Buttons and settings ask for it (or your fingerprint, once kept), and it lasts ${state?.pinMinutes ?: 2} min without a press. " +
                        "Wall mode on this phone uses the same unlock."
                    else "Anyone at the screen can use its buttons.",
                    fontSize = LabScale.textSm, color = Lab.muted,
                )
                if (rights.admin) PinButtons(repo, gate, rights.pin) { message = it; load() }
            }
        }
        if (rights.admin) {
            val st = state
            if (st == null) item { Unlock { gate.press { load() } } }
            else {
                item { CollapsiblePanel("Single sign-on", if (st.oidc != null) "On" else "Off") { SsoPanel(st) } }
                item {
                    CollapsiblePanel("Session timeout", "${st.sessionHours} h browser · ${st.pinMinutes} min PIN") {
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
                }
                item { CollapsiblePanel("Trusted networks", "This phone comes from ${st.yourIp}") { NetworksPanel(st) } }
            }
        }
        item { WallOnly("⚙ > Security", listOf("The admin's password and authenticator (TOTP): `labhud admin` on the server, on purpose",
            "The single sign-on provider and the networks")) }
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
            fontSize = LabScale.textSm, color = Lab.muted,
        )
        if (o != null && !st.secretSet) Text("No client secret is kept: a confidential provider will refuse.", fontSize = LabScale.textSm, color = Lab.warn)
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
        Text("A screen stays open without the PIN", fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = LabScale.space2))
        Chips(MINUTES, minutes) { minutes = it }
        Text("Both count from the last use.", fontSize = LabScale.textSm, color = Lab.muted)
        if (!st.kept) Text("Take over the config on the wall first (⚙ > System), so LabHUD can write it.", fontSize = LabScale.textSm, color = Lab.warn)
        Button(enabled = st.kept && (hours != st.sessionHours || minutes != st.pinMinutes), onClick = { onSave(hours, minutes) },
            modifier = Modifier.padding(top = LabScale.space2)) { Text("Save") }
    }
}

@Composable
private fun Chips(options: List<Pair<Int, String>>, current: Int, pick: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = LabScale.space1), horizontalArrangement = Arrangement.spacedBy(LabScale.space2)) {
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
        Text("Admin networks: " + st.adminNetworks.ifEmpty { listOf("anywhere") }.joinToString(", "), fontSize = LabScale.textSm, color = Lab.sub)
        Text("Trusted proxies: " + st.trustedProxies.ifEmpty { listOf("none") }.joinToString(", "), fontSize = LabScale.textSm, color = Lab.sub)
        Text("Outside the admin networks nobody signs in, and a phone signed in only looks.", fontSize = LabScale.textSm, color = Lab.muted)
    }
}

/** Signed in as the admin or only looking, with the one button that changes it. */
@Composable
internal fun AccountPanel(admin: Boolean, pin: Boolean, user: String = "", onSignIn: () -> Unit, onSignOut: () -> Unit,
                          onSso: (() -> Unit)? = null, onLock: (() -> Unit)? = null) {
    Panel("") {
        Text(accountSummary(admin, user), fontWeight = FontWeight.Medium)
        Text(
            if (admin) "The buttons work here" + (if (pin) ", after the wall's PIN (or your fingerprint)." else ".")
            else "Sign in as LabHUD's admin (user, password and the authenticator code) for the buttons and the rest of the settings.",
            fontSize = LabScale.textSm, color = Lab.muted,
        )
        Row(Modifier.padding(top = LabScale.space2), horizontalArrangement = Arrangement.spacedBy(LabScale.space2)) {
            if (admin) TextButton(onClick = onSignOut) { Text("Sign out", color = Lab.red) }
            else Button(onClick = onSignIn) { Text("Sign in") }
            if (admin && onLock != null) OutlinedButton(onClick = onLock) { Text("Lock now") }
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
            Column(verticalArrangement = Arrangement.spacedBy(LabScale.space2)) {
                OutlinedTextField(user, { user = it.take(64) }, singleLine = true, label = { Text("User") })
                OutlinedTextField(password, { password = it.take(256) }, singleLine = true, label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation())
                OutlinedTextField(code, { code = it.filter(Char::isDigit).take(8) }, singleLine = true, label = { Text("Authenticator code") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                if (error.isNotEmpty()) Text(error, color = Lab.warn, fontSize = LabScale.textSm)
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
