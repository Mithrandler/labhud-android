package io.github.mithrandler.labhud.ui.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mithrandler.labhud.data.Channel
import io.github.mithrandler.labhud.data.Channels
import io.github.mithrandler.labhud.ui.Lab
import io.github.mithrandler.labhud.ui.Panel

/** The channels ⚙ > Notifications edits here (app and ntfy have their own choice above). */
val EDITABLE_CHANNELS = listOf("email", "webhook", "discord", "gotify")

val CHANNEL_NAMES = mapOf("app" to "App", "ntfy" to "ntfy", "email" to "Email", "webhook" to "Webhook", "discord" to "Discord", "gotify" to "Gotify")

/** A field of a channel's dialog: secret ones go to secrets.env and are never shown again. */
data class ChannelField(val key: String, val label: String, val secret: Boolean = false)

/** What each channel asks for, as api/notifications.py takes it (config, or secrets when [ChannelField.secret]). */
val CHANNEL_FIELDS = mapOf(
    "email" to listOf(ChannelField("host", "SMTP server"), ChannelField("port", "Port (empty: from the security)"),
        ChannelField("user", "User"), ChannelField("password", "Password", secret = true),
        ChannelField("from", "From"), ChannelField("to", "To (addresses, with commas)")),
    "webhook" to listOf(ChannelField("url", "Address (https://…)", secret = true), ChannelField("auth", "Authorization header (optional)", secret = true)),
    "discord" to listOf(ChannelField("url", "Discord webhook address", secret = true)),
    "gotify" to listOf(ChannelField("url", "Gotify address (https://…)"), ChannelField("token", "App token", secret = true)),
)

val EMAIL_SECURITY = listOf("starttls", "ssl", "none")

/** What the dialog starts with: the saved non-secret values (email's `to` joined, its security). */
fun channelValues(name: String, c: Channel): Map<String, String> = when (name) {
    "email" -> mapOf("host" to (c.host ?: ""), "port" to (c.port?.toString() ?: ""), "security" to (c.security ?: "starttls"),
        "user" to (c.user ?: ""), "from" to (c.from ?: ""), "to" to (c.to ?: emptyList()).joinToString(", "))
    "gotify" -> mapOf("url" to (c.url ?: ""))
    else -> emptyMap()
}

/** "set · mail.example.org · 12 sent" / "not set" / its problem; the last error after it. */
fun channelLine(name: String, c: Channel): String {
    if (!c.set) return c.problem ?: "not set"
    val where = when (name) { "gotify" -> c.url; else -> c.host }
    return listOfNotNull("set", where?.ifEmpty { null }, c.sent?.let { "$it sent" },
        c.lastError?.let { "last error: $it" }).joinToString(" · ")
}

/** The matrix with [channel] added to or taken from [event]. */
fun toggled(matrix: Map<String, List<String>>, event: String, channel: String): Map<String, List<String>> {
    val now = matrix[event] ?: emptyList()
    return matrix + (event to if (channel in now) now - channel else now + channel)
}

/** Email, webhook, Discord and Gotify: each one's state, Edit, Test and Forget. */
@Composable
fun ChannelsPanel(
    ch: Channels,
    onSave: (String, Map<String, String>, Map<String, String>) -> Unit,
    onTest: (String, Map<String, String>, Map<String, String>) -> Unit,
    onForget: (String) -> Unit,
) {
    var editing by remember { mutableStateOf<String?>(null) }
    var forgetting by remember { mutableStateOf<String?>(null) }
    Panel("Channels", ch.order.filter { it == "app" || ch.channels[it]?.set == true }.joinToString(" · ").ifEmpty { "None set" }) {
        Text("Each with its own test. Passwords, addresses and tokens go to secrets.env and are not shown again.",
            fontSize = 12.sp, color = Lab.muted, modifier = Modifier.padding(bottom = 6.dp))
        if (!ch.managed) Text("Email and Gotify need the config file taken over (on the wall: ⚙ > System > Take over).", fontSize = 13.sp, color = Lab.warn)
        EDITABLE_CHANNELS.forEachIndexed { i, name ->
            val c = ch.channels[name] ?: Channel()
            if (i > 0) HorizontalDivider(color = Lab.line)
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(CHANNEL_NAMES[name] ?: name, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Text(channelLine(name, c), fontSize = 12.sp, color = if (c.lastError != null || (!c.set && c.problem != null)) Lab.warn else Lab.muted)
                }
                TextButton(onClick = { editing = name }) { Text(if (c.set) "Edit" else "Set") }
                if (c.set) TextButton(onClick = { forgetting = name }) { Text("Forget", color = Lab.red) }
            }
        }
    }
    editing?.let { name ->
        ChannelDialog(name, ch.channels[name] ?: Channel(), onDismiss = { editing = null },
            onTest = { cfg, sec -> onTest(name, cfg, sec) }, onSave = { cfg, sec -> editing = null; onSave(name, cfg, sec) })
    }
    forgetting?.let { name ->
        AlertDialog(
            onDismissRequest = { forgetting = null },
            title = { Text("Forget ${CHANNEL_NAMES[name]}?") },
            text = { Text("Its settings and secrets are removed, and it is taken out of every row below.") },
            confirmButton = { TextButton(onClick = { forgetting = null; onForget(name) }) { Text("Forget", color = Lab.red) } },
            dismissButton = { TextButton(onClick = { forgetting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ChannelDialog(
    name: String, c: Channel, onDismiss: () -> Unit,
    onTest: (Map<String, String>, Map<String, String>) -> Unit,
    onSave: (Map<String, String>, Map<String, String>) -> Unit,
) {
    val fields = CHANNEL_FIELDS[name] ?: emptyList()
    val values = remember { mutableStateMapOf<String, String>().apply { putAll(channelValues(name, c)) } }
    fun split(): Pair<Map<String, String>, Map<String, String>> {
        val secretKeys = fields.filter { it.secret }.map { it.key }.toSet()
        return values.filterKeys { it !in secretKeys } to values.filterKeys { it in secretKeys }.filterValues { it.isNotBlank() }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(CHANNEL_NAMES[name] ?: name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                fields.forEach { f ->
                    val saved = f.secret && secretSaved(name, f.key, c)
                    OutlinedTextField(values[f.key] ?: "", { values[f.key] = it.take(2000) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        label = { Text(if (saved) "${f.label} (empty: keep)" else f.label) },
                        visualTransformation = if (f.secret) PasswordVisualTransformation() else VisualTransformation.None)
                    if (name == "email" && f.key == "port") Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        EMAIL_SECURITY.forEach { s ->
                            FilterChip(selected = values["security"] == s, onClick = { values["security"] = s }, label = { Text(s) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Lab.accent.copy(alpha = 0.2f), selectedLabelColor = Lab.title))
                        }
                    }
                }
                TextButton(onClick = { val (cfg, sec) = split(); onTest(cfg, sec) }) { Text("Send a test") }
            }
        },
        confirmButton = { TextButton(onClick = { val (cfg, sec) = split(); onSave(cfg, sec) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Whether the server says this secret is saved (it never sends the secret itself). */
fun secretSaved(name: String, key: String, c: Channel): Boolean = when ("$name.$key") {
    "email.password" -> c.password
    "webhook.auth" -> c.auth
    "gotify.token" -> c.token
    "webhook.url", "discord.url" -> c.set
    else -> false
}

/**
 * Which news goes where: one row per event, a chip per channel that is set (app and ntfy always);
 * a tap saves the whole matrix ([notify.events]).
 */
@Composable
fun MatrixPanel(ch: Channels, onChange: (Map<String, List<String>>) -> Unit) {
    val shown = ch.order.filter { it == "app" || it == "ntfy" || ch.channels[it]?.set == true }
    Panel("Which news goes where", if (ch.explicit) "As ticked here" else "Not chosen yet") {
        Text(if (ch.explicit) "As ticked here." else "Not chosen yet: the bad news goes everywhere that is set; updates and sign-ins nowhere until ticked.",
            fontSize = 12.sp, color = Lab.muted, modifier = Modifier.padding(bottom = 6.dp))
        ch.events.forEachIndexed { i, ev ->
            val id = ev.getOrNull(0) ?: return@forEachIndexed
            val now = ch.matrix[id] ?: emptyList()
            if (i > 0) HorizontalDivider(color = Lab.line)
            Text(ev.getOrNull(1) ?: id, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 6.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                shown.forEach { c ->
                    FilterChip(selected = c in now, onClick = { onChange(toggled(ch.matrix, id, c)) }, label = { Text(CHANNEL_NAMES[c] ?: c) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Lab.accent.copy(alpha = 0.2f), selectedLabelColor = Lab.title))
                }
            }
        }
    }
}
