package io.github.mithrandler.labhud.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mithrandler.labhud.data.LabRepository
import io.github.mithrandler.labhud.data.Phone
import io.github.mithrandler.labhud.ui.GateDialogs
import io.github.mithrandler.labhud.ui.Lab
import io.github.mithrandler.labhud.ui.Panel
import io.github.mithrandler.labhud.ui.rememberGate
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * The model for a section that reads from the server: load behind the gate (admin, then the PIN or
 * a fingerprint), an "Unlock" panel until then, a message line, the content, and the gate's dialogs.
 * Remove ends a phone's token at once (POST /api/v1/unpair, the same route as ⚙ > Phones).
 */
@Composable
fun PhonesSection(repo: LabRepository, nav: SettingsNav) {
    val scope = rememberCoroutineScope()
    val gate = rememberGate(repo)
    var phones by remember { mutableStateOf<List<Phone>?>(null) }
    var message by remember { mutableStateOf("") }
    var removing by remember { mutableStateOf<Phone?>(null) }

    fun load() = scope.launch {
        val api = repo.api() ?: return@launch
        message = ""
        runCatching { api.phones().devices }.onSuccess { phones = it }.onFailure { message = it.message ?: "Not loaded." }
    }
    LaunchedEffect(Unit) { gate.press { load() } }

    SettingsPage(section("phones")!!, nav) {
        if (phones == null) item { Unlock { gate.press { load() } } }
        if (message.isNotEmpty()) item { Text(message, color = Lab.warn, fontSize = 13.sp) }
        phones?.let { list ->
            item {
                Panel("") {
                    if (list.isEmpty()) Text("No phone paired.", color = Lab.muted, fontSize = 14.sp)
                    list.forEach { p ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(p.name, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                Text(listOfNotNull(
                                    if (p.admin) "admin" else "looks only",
                                    if (p.push) "instant notifications" else null,
                                    p.seen.takeIf { it > 0 }?.let { "seen " + stamp(it) },
                                ).joinToString(" · "), fontSize = 12.sp, color = Lab.muted)
                            }
                            TextButton(onClick = { removing = p }) { Text("Remove", color = Lab.red) }
                        }
                    }
                }
            }
            item { WallOnly("⚙ > Phones > Pair a phone", listOf("A new phone pairs by scanning the code shown there.")) }
        }
    }

    removing?.let { p ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove ${p.name}?") },
            text = { Text("Its token stops working at once. To use it again, pair it again.") },
            confirmButton = {
                TextButton(onClick = {
                    removing = null
                    gate.press {
                        scope.launch {
                            val r = runCatching { repo.api()?.unpair(p.name) }.getOrNull()
                            if (r?.ok == true) phones = phones?.filter { it.name != p.name } else message = r?.error?.ifBlank { null } ?: "Not removed."
                        }
                    }
                }) { Text("Remove", color = Lab.red) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } },
        )
    }
    GateDialogs(gate, nav.signIn)
}

/** Shown until a section's data is loaded: the admin's PIN (or fingerprint) opens it. */
@Composable
fun Unlock(onUnlock: () -> Unit) {
    Panel("") {
        Text("Unlock to see this LabHUD's settings.", color = Lab.sub, fontSize = 14.sp)
        Button(onClick = onUnlock, modifier = Modifier.padding(top = 8.dp)) { Text("Unlock") }
    }
}

/** A Unix time as the phone's short date and time. */
fun stamp(t: Long): String = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(t * 1000))
