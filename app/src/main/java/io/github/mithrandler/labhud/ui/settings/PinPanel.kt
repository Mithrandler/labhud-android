package io.github.mithrandler.labhud.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mithrandler.labhud.data.LabRepository
import io.github.mithrandler.labhud.data.pinOk
import io.github.mithrandler.labhud.ui.Gate
import io.github.mithrandler.labhud.ui.Lab
import kotlinx.coroutines.launch

/**
 * Set, change or remove the wall's PIN: four digits, typed twice; the current one is asked to change or remove it. Behind the gate
 * (signed in as the admin and unlocked). The admin's password and authenticator stay on the server (`labhud admin`) on purpose:
 * a phone that is stolen and unlocked must not be able to take the account over.
 */
@Composable
internal fun PinButtons(repo: LabRepository, gate: Gate, pinSet: Boolean, onDone: (String) -> Unit) {
    var mode by remember { mutableStateOf<String?>(null) }     // set, change or remove
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!pinSet) OutlinedButton(onClick = { gate.press { mode = "set" } }) { Text("Set a PIN") }
        else {
            OutlinedButton(onClick = { gate.press { mode = "change" } }) { Text("Change") }
            OutlinedButton(onClick = { gate.press { mode = "remove" } }) { Text("Remove", color = Lab.red) }
        }
    }
    mode?.let { m -> PinDialog(repo, m, onClose = { mode = null }, onDone = { mode = null; onDone(it) }) }
}

@Composable
private fun PinDialog(repo: LabRepository, mode: String, onClose: () -> Unit, onDone: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var old by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val needsOld = mode != "set"
    val needsNew = mode != "remove"
    val ok = (!needsOld || old.length == 4) && (!needsNew || pinOk(new, again))
    @Composable
    fun field(value: String, label: String, set: (String) -> Unit) = OutlinedTextField(
        value, { set(it.filter { c -> c.isDigit() }.take(4)) }, singleLine = true, label = { Text(label) },
        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth(),
    )
    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text(when (mode) { "set" -> "Set the wall's PIN"; "change" -> "Change the wall's PIN"; else -> "Remove the wall's PIN" }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (mode == "remove") Text("Anyone at the screen can use its buttons after this.", fontSize = 13.sp, color = Lab.sub)
                if (needsOld) field(old, "Current PIN") { old = it }
                if (needsNew) { field(new, "New PIN (4 digits)") { new = it }; field(again, "New PIN again") { again = it } }
                if (needsNew && again.length == 4 && new != again) Text("The two do not match.", fontSize = 12.sp, color = Lab.warn)
                if (error.isNotEmpty()) Text(error, fontSize = 12.sp, color = Lab.warn, modifier = Modifier.padding(top = 4.dp))
            }
        },
        confirmButton = {
            TextButton(enabled = ok && !busy, onClick = {
                busy = true
                scope.launch {
                    val e = repo.changePin(old, if (needsNew) new else null)
                    busy = false
                    if (e == null) onDone(when (mode) { "set" -> "PIN set."; "change" -> "PIN changed."; else -> "PIN removed." }) else error = e
                }
            }) { Text(if (mode == "remove") "Remove" else "Save") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onClose) { Text("Cancel") } },
    )
}
