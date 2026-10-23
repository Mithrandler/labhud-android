package io.github.mithrandler.labhud.ui.settings

import io.github.mithrandler.labhud.ui.LabScale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import io.github.mithrandler.labhud.data.AdminSession
import io.github.mithrandler.labhud.data.LabRepository
import io.github.mithrandler.labhud.data.Phone
import io.github.mithrandler.labhud.data.SessionsAnswer
import io.github.mithrandler.labhud.ui.Gate
import io.github.mithrandler.labhud.ui.Lab
import io.github.mithrandler.labhud.ui.isRefusal
import io.github.mithrandler.labhud.ui.refusalText
import kotlinx.coroutines.launch

/** "Signed in as boss" / "Only looking": the line the Account section opens with. */
fun accountSummary(admin: Boolean, user: String): String = when {
    !admin -> "Only looking"
    user.isNotBlank() -> "Signed in as $user"
    else -> "Signed in as the admin"
}

/** One session as a line: where it came from and what it is, and how long ago it was used. */
fun sessionLine(s: AdminSession, nowSeconds: Long): String {
    val ago = (nowSeconds - s.last.toLong()).coerceAtLeast(0)
    val when_ = when {
        ago < 90 -> "just now"
        ago < 5400 -> "${ago / 60} min ago"
        ago < 129_600 -> "${ago / 3600} h ago"
        else -> "${ago / 86_400} days ago"
    }
    return listOf(s.what, s.where, "used $when_").filter { it.isNotBlank() }.joinToString(" · ")
}

/** The admin's other sessions (browsers and the wall): sign one out, or all. Read behind the gate, when the panel opens. */
@Composable
fun SessionsPanel(repo: LabRepository, gate: Gate) {
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<SessionsAnswer?>(null) }
    var message by remember { mutableStateOf("") }
    var confirmAll by remember { mutableStateOf(false) }

    fun load() = gate.press {
        scope.launch {
            val api = repo.api() ?: return@launch
            message = ""
            runCatching { api.sessions() }.onSuccess { data = it }.onFailure { e ->
                val code = (e as? io.github.mithrandler.labhud.data.ApiException)?.code
                message = when (code) {
                    403 -> refusalText("forbidden", repo.rights.value).also { repo.refreshRights() }
                    404 -> "This LabHUD is too old for this list: update it."
                    else -> e.message ?: "Not loaded."
                }
            }
        }
    }
    fun done(r: io.github.mithrandler.labhud.data.ActionAnswer?) {
        if (r?.ok == true) load() else message = if (r == null) "No server paired." else refusalText(r.error, repo.rights.value)
        if (r != null && !r.ok && isRefusal(r.error)) scope.launch { repo.refreshRights() }
    }

    val shown = data
    CollapsiblePanel("Sessions", shown?.let { "${it.sessions.size} browser(s), ${it.phones.count { p -> p.admin }} phone(s)" } ?: "Where the admin is signed in",
        onOpen = { load() }) {
        if (shown == null && message.isEmpty()) Text("Loading…", fontSize = LabScale.textSm, color = Lab.muted)
        if (message.isNotEmpty()) Text(message, color = Lab.warn, fontSize = LabScale.textSm)
        shown?.let { a ->
            val now = System.currentTimeMillis() / 1000
            if (a.sessions.isEmpty()) Text("No browser is signed in.", fontSize = LabScale.textSm, color = Lab.muted)
            a.sessions.forEach { s ->
                Row(Modifier.fillMaxWidth().padding(vertical = LabScale.spaceHalf), verticalAlignment = Alignment.CenterVertically) {
                    Text(sessionLine(s, now), fontSize = LabScale.textSm, color = Lab.sub, modifier = Modifier.weight(1f))
                    TextButton(onClick = { gate.press { scope.launch { done(runCatching { repo.api()?.revokeSession(s.id) }.getOrNull()) } } }) {
                        Text("Sign out", color = Lab.red)
                    }
                }
            }
            if (a.phones.isNotEmpty()) Text("Phones that are the admin: " + a.phones.joinToString(", ") { it.name }, fontSize = LabScale.textSm, color = Lab.muted,
                modifier = Modifier.padding(top = LabScale.space1))
            OutlinedButton(onClick = { confirmAll = true }, enabled = a.sessions.isNotEmpty(), modifier = Modifier.padding(top = LabScale.space2)) {
                Text("Sign out every browser")
            }
        }
    }
    if (confirmAll) AlertDialog(
        onDismissRequest = { confirmAll = false },
        title = { Text("Sign out every browser?") },
        text = { Text("Each screen and browser asks for the password and code again. Phones are not touched.") },
        confirmButton = {
            TextButton(onClick = {
                confirmAll = false
                gate.press { scope.launch { done(runCatching { repo.api()?.logoutAllSessions() }.getOrNull()) } }
            }) { Text("Sign out", color = Lab.red) }
        },
        dismissButton = { TextButton(onClick = { confirmAll = false }) { Text("Cancel") } },
    )
}

/** The phones paired with this LabHUD, with Remove (the same route as ⚙ > Phones). Read behind the gate, when the panel opens. */
@Composable
fun PairedPhonesPanel(repo: LabRepository, gate: Gate) {
    val scope = rememberCoroutineScope()
    var phones by remember { mutableStateOf<List<Phone>?>(null) }
    var message by remember { mutableStateOf("") }
    var removing by remember { mutableStateOf<Phone?>(null) }

    fun load() = gate.press {
        scope.launch {
            val api = repo.api() ?: return@launch
            message = ""
            runCatching { api.phones().devices }.onSuccess { phones = it }.onFailure { message = it.message ?: "Not loaded." }
        }
    }
    CollapsiblePanel("Paired phones", phones?.let { "${it.size} paired" } ?: "Phones that may use this LabHUD", onOpen = { load() }) {
        if (phones == null && message.isEmpty()) Text("Loading…", fontSize = LabScale.textSm, color = Lab.muted)
        if (message.isNotEmpty()) Text(message, color = Lab.warn, fontSize = LabScale.textSm)
        phones?.let { list ->
            if (list.isEmpty()) Text("No phone paired.", color = Lab.muted, fontSize = LabScale.textMd)
            list.forEach { p ->
                Row(Modifier.fillMaxWidth().padding(vertical = LabScale.space1), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name, fontWeight = FontWeight.Medium, fontSize = LabScale.textMd)
                        Text(listOfNotNull(
                            if (p.admin) "admin" else "looks only",
                            if (p.push) "instant notifications" else null,
                            p.seen.takeIf { it > 0 }?.let { "seen " + stamp(it) },
                        ).joinToString(" · "), fontSize = LabScale.textSm, color = Lab.muted)
                    }
                    TextButton(onClick = { removing = p }) { Text("Remove", color = Lab.red) }
                }
            }
            Text("A new phone pairs by scanning the code in ⚙ > Phones > Pair a phone, on the wall.", fontSize = LabScale.textSm, color = Lab.muted,
                modifier = Modifier.padding(top = LabScale.space1))
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
                            if (r?.ok == true) phones = phones?.filter { it.name != p.name }
                            else message = r?.let { refusalText(it.error, repo.rights.value) } ?: "Not removed."
                        }
                    }
                }) { Text("Remove", color = Lab.red) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } },
        )
    }
}
