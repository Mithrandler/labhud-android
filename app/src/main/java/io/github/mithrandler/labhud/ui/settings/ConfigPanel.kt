package io.github.mithrandler.labhud.ui.settings

import io.github.mithrandler.labhud.ui.LabScale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mithrandler.labhud.data.ConfigStatus
import io.github.mithrandler.labhud.data.ConfigText
import io.github.mithrandler.labhud.data.LabRepository
import io.github.mithrandler.labhud.data.configSaid
import io.github.mithrandler.labhud.data.configSave
import io.github.mithrandler.labhud.data.configStatus
import io.github.mithrandler.labhud.data.configText
import io.github.mithrandler.labhud.data.takeOver
import io.github.mithrandler.labhud.ui.Gate
import io.github.mithrandler.labhud.ui.Lab
import io.github.mithrandler.labhud.ui.Panel
import io.github.mithrandler.labhud.ui.TypedConfirm
import kotlinx.coroutines.launch

/**
 * config.toml as text, with Validate, Save and Discard (never autosave): Validate only asks the server whether the text is a good config,
 * Save writes it (the server checks again first). When the file is mounted from outside LabHUD does not keep it: Take over (the word typed)
 * moves it into LabHUD's own folder and restarts it. Secrets are not in this file. Behind the gate.
 */
@Composable
internal fun ConfigPanel(repo: LabRepository, gate: Gate) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<ConfigStatus?>(null) }
    var loaded by remember { mutableStateOf<ConfigText?>(null) }
    var edited by remember { mutableStateOf("") }
    var said by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var taking by remember { mutableStateOf(false) }
    val dirty = loaded != null && edited != loaded?.text

    fun load() = scope.launch {
        val api = repo.api() ?: return@launch
        runCatching { api.configStatus() }.onSuccess { status = it }
        runCatching { api.configText() }.onSuccess { t -> if (t.ok) { loaded = t; edited = t.text } else said = "✕ " + t.error.ifBlank { "refused" } }
            .onFailure { said = "✕ " + (it.message ?: "not loaded") }
    }
    LaunchedEffect(Unit) { gate.press { load() } }

    Panel("") {
        val st = status
        if (st == null) {
            Text("Unlock to see the config file.", fontSize = LabScale.textSm, color = Lab.sub)
            OutlinedButton(onClick = { gate.press { load() } }) { Text("Unlock") }
            return@Panel
        }
        Text(
            when {
                st.managed -> "LabHUD keeps this file itself (taken over)."
                st.canTakeOver -> "This file is mounted from outside: LabHUD does not keep it, and an edit here may not stay."
                else -> st.problem ?: "This file cannot be taken over."
            }, fontSize = LabScale.textSm, color = if (st.managed) Lab.muted else Lab.warn,
        )
        if (!st.managed && st.canTakeOver) OutlinedButton(enabled = !busy, onClick = { taking = true }) { Text("Take over") }
        loaded?.let { t ->
            OutlinedTextField(
                edited, { edited = it }, minLines = 8, maxLines = 20, label = { Text(t.path.substringAfterLast('/').ifEmpty { "config.toml" }) },
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = LabScale.textSm), modifier = Modifier.fillMaxWidth().padding(top = LabScale.space2),
            )
            if (said.isNotEmpty()) Text(said, fontSize = LabScale.textSm, color = if (said.startsWith("✓")) Lab.muted else Lab.warn, modifier = Modifier.padding(top = LabScale.space1))
            Row(Modifier.padding(top = LabScale.space2), horizontalArrangement = Arrangement.spacedBy(LabScale.space2)) {
                OutlinedButton(enabled = !busy, onClick = {
                    gate.press { busy = true; scope.launch {
                        val r = runCatching { repo.api()?.configSave(edited, check = true) }.getOrNull()
                        said = if (r == null) "✕ no answer" else configSaid(r, saved = false); busy = false
                    } }
                }) { Text("Validate") }
                OutlinedButton(enabled = !busy && dirty && (t.managed || t.writable), onClick = {
                    gate.press { busy = true; scope.launch {
                        val r = runCatching { repo.api()?.configSave(edited, check = false) }.getOrNull()
                        said = if (r == null) "✕ no answer" else configSaid(r, saved = true)
                        if (r?.ok == true && r.problems.isEmpty()) load().join()
                        busy = false
                    } }
                }) { Text("Save") }
                if (dirty) OutlinedButton(onClick = { edited = t.text; said = "" }) { Text("Discard") }
            }
            if (!t.managed && !t.writable) Text("Read only: take it over first.", fontSize = LabScale.textSm, color = Lab.muted)
        }
    }
    if (taking) TypedConfirm("Take over the config file?", "take over", "Take over", onOk = {
        taking = false
        gate.press { busy = true; scope.launch {
            val r = runCatching { repo.api()?.takeOver() }.getOrNull()
            said = if (r?.ok == true) "✓ Taken over: LabHUD restarts; open this page again in a moment." else "✕ " + (r?.error?.ifBlank { null } ?: "refused")
            if (r?.ok == true) { status = null; loaded = null }
            busy = false
        } }
    }, onDismiss = { taking = false })
}
