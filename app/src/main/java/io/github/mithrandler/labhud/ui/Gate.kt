package io.github.mithrandler.labhud.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import io.github.mithrandler.labhud.data.LabRepository
import io.github.mithrandler.labhud.lock.PinVault
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * What stands before a button: this phone signed in as the admin, and, when the display has a
 * PIN, a live unlock ticket (asked here: the PIN, or a fingerprint standing in for it). The
 * ticket lasts as long as on the display (2 minutes without a press), so a run of presses asks once.
 */
class Gate(private val repo: LabRepository, private val scope: CoroutineScope) {
    var asking by mutableStateOf<(() -> Unit)?>(null)
    var notAdmin by mutableStateOf(false)

    fun press(run: () -> Unit) {
        when {
            !repo.rights.value.admin -> notAdmin = true
            repo.mayPress() -> scope.launch {   // a held PIN renews the ticket by itself (repo.api)
                if (!repo.rights.value.pin || repo.renewTicket()) run() else asking = run
            }
            else -> asking = run
        }
    }

    internal suspend fun unlock(pin: String) = repo.unlock(pin)
    internal fun launch(block: suspend CoroutineScope.() -> Unit) = scope.launch(block = block)
    internal suspend fun serverId() = repo.store.currentNow()?.id
    internal suspend fun sealed(id: String) = repo.store.sealedPin(id)
    internal suspend fun keep(id: String, sealed: String?) = repo.store.setSealedPin(id, sealed)
}

@Composable
fun rememberGate(repo: LabRepository): Gate {
    val scope = rememberCoroutineScope()
    return remember(repo) { Gate(repo, scope) }
}

@Composable
fun GateDialogs(gate: Gate, onSignIn: () -> Unit) {
    if (gate.notAdmin) AlertDialog(
        onDismissRequest = { gate.notAdmin = false },
        title = { Text("Only the admin") },
        text = { Text("This phone only looks. Sign in as LabHUD's admin (Settings > Account) to use the buttons.") },
        confirmButton = { TextButton(onClick = { gate.notAdmin = false; onSignIn() }) { Text("Sign in") } },
        dismissButton = { TextButton(onClick = { gate.notAdmin = false }) { Text("Cancel") } },
    )
    val run = gate.asking ?: return
    val activity = LocalActivity.current as FragmentActivity
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val canFinger = remember { PinVault.available(activity) }
    // the PIN dialog waits for the fingerprint when one holds the PIN: never both at once
    var typing by remember { mutableStateOf(false) }
    var keepIt by remember { mutableStateOf(canFinger) }

    fun done() {
        gate.asking = null
        run()
    }

    // a fingerprint first, when it holds the PIN already
    LaunchedEffect(Unit) {
        val id = gate.serverId()
        val sealed = id?.let { gate.sealed(it) }
        if (id == null || sealed == null) { typing = true; return@LaunchedEffect }
        PinVault.open(activity, sealed) { opened ->
            if (opened == null) { typing = true; return@open }
            gate.launch {
                val e = gate.unlock(opened)
                if (e == null) done() else { gate.keep(id, null); error = "The PIN changed: type the new one."; typing = true }
            }
        }
    }

    fun submit() {
        if (busy || pin.length < 4) return
        busy = true
        error = ""
        gate.launch {
            val e = gate.unlock(pin)
            busy = false
            if (e != null) { error = e; pin = ""; return@launch }
            val id = gate.serverId()
            if (keepIt && canFinger && id != null && gate.sealed(id) == null) {
                PinVault.seal(activity, pin) { sealed -> gate.launch { if (sealed != null) gate.keep(id, sealed); done() } }
            } else done()
        }
    }

    if (!typing) return
    AlertDialog(
        onDismissRequest = { gate.asking = null },
        title = { Text("The wall's PIN") },
        text = {
            Column {
                Text("LabHUD has a PIN: asked once, the buttons then stay open while the app is open.", fontSize = LabScale.textMd, color = Lab.sub)
                OutlinedTextField(
                    pin, { pin = it.filter(Char::isDigit).take(8) },
                    singleLine = true, label = { Text("PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.padding(top = LabScale.space3),
                )
                if (canFinger) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = LabScale.space2)) {
                    Checkbox(keepIt, { keepIt = it })
                    Text("Use my fingerprint next time", fontSize = LabScale.textMd)
                }
                if (error.isNotEmpty()) Text(error, color = Lab.warn, fontSize = LabScale.textSm, modifier = Modifier.padding(top = LabScale.space2))
            }
        },
        confirmButton = { TextButton(onClick = ::submit, enabled = !busy && pin.length >= 4) { Text(if (busy) "…" else "Unlock") } },
        dismissButton = { TextButton(onClick = { gate.asking = null }) { Text("Cancel") } },
    )
}
