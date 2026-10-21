package io.github.mithrandler.labhud.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mithrandler.labhud.data.Registries
import io.github.mithrandler.labhud.data.Registry
import io.github.mithrandler.labhud.data.ScanRow
import io.github.mithrandler.labhud.ui.Lab
import io.github.mithrandler.labhud.ui.Panel

/**
 * ⚙ > Containers' registries with their own sign-in: host, user, whether a token is saved and how
 * many containers pull from it; Add, Edit (the token left empty keeps the saved one), Remove.
 */
@Composable
fun RegistriesPanel(r: Registries, onSave: (String, String, String) -> Unit, onRemove: (Registry) -> Unit) {
    var editing by remember { mutableStateOf<Registry?>(null) }
    var removing by remember { mutableStateOf<Registry?>(null) }
    Panel("Registries") {
        Text("Sign-ins for private images: the agents pull and check updates with them. The token is kept in secrets.env.",
            fontSize = 12.sp, color = Lab.muted, modifier = Modifier.padding(bottom = 6.dp))
        if (!r.managed) Text("Needs the config file taken over (on the wall: ⚙ > System > Take over).", fontSize = 13.sp, color = Lab.warn)
        if (r.registries.isEmpty()) Text("None: public images only.", color = Lab.muted, fontSize = 14.sp)
        r.registries.forEachIndexed { i, reg ->
            if (i > 0) HorizontalDivider(color = Lab.line)
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(reg.host, fontWeight = FontWeight.Medium, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(registryLine(reg), fontSize = 12.sp, color = if (reg.token) Lab.muted else Lab.warn)
                }
                TextButton(onClick = { editing = reg }) { Text("Edit") }
                TextButton(onClick = { removing = reg }) { Text("Remove", color = Lab.red) }
            }
        }
        if (r.managed) Button(onClick = { editing = Registry() }, modifier = Modifier.padding(top = 8.dp)) { Text("Add a registry") }
    }
    editing?.let { reg ->
        RegistryDialog(reg, onDismiss = { editing = null }) { host, user, token -> editing = null; onSave(host, user, token) }
    }
    removing?.let { reg ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove ${reg.host}?") },
            text = { Text("Its token is forgotten too; private images from it can no longer be pulled.") },
            confirmButton = { TextButton(onClick = { removing = null; onRemove(reg) }) { Text("Remove", color = Lab.red) } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } },
        )
    }
}

/** "user pyke · token saved · 3 containers" */
fun registryLine(r: Registry): String = listOf(
    "user ${r.user}",
    if (r.token) "token saved" else "no token",
    when (r.containers) { 0 -> "not used yet"; 1 -> "1 container"; else -> "${r.containers} containers" },
).joinToString(" · ")

@Composable
private fun RegistryDialog(reg: Registry, onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    val adding = reg.host.isEmpty()
    var host by remember { mutableStateOf(reg.host) }
    var user by remember { mutableStateOf(reg.user) }
    var token by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (adding) "A registry" else reg.host) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (adding) OutlinedTextField(host, { host = it.trim().take(253) }, singleLine = true, label = { Text("Host (e.g. ghcr.io)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(user, { user = it.take(128) }, singleLine = true, label = { Text("User") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(token, { token = it.trim().take(4000) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (reg.token) "Token (empty: keep the saved one)" else "Token or password") },
                    visualTransformation = PasswordVisualTransformation())
            }
        },
        confirmButton = { TextButton(onClick = { onSave(host, user.trim(), token) }, enabled = host.isNotBlank() && user.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * The image scans (trivy or grype on an agent), worst first; which agents can scan; and the switch
 * that holds back an update whose image brings new critical CVEs.
 */
@Composable
fun ScansPanel(r: Registries, onHold: (Boolean) -> Unit) {
    Panel("Vulnerabilities (CVE)") {
        Text(scannersLine(r), fontSize = 12.sp, color = Lab.muted, modifier = Modifier.padding(bottom = 6.dp))
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text("Hold back risky updates", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Text("An update on its own waits when the new image has more critical CVEs than the one running.", fontSize = 12.sp, color = Lab.muted)
            }
            Switch(checked = r.holdCritical, onCheckedChange = onHold)
        }
        if (r.scans.isEmpty()) Text("No scan yet.", color = Lab.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
        r.scans.forEach { s -> HorizontalDivider(color = Lab.line); ScanLine(s) }
    }
}

/** "Can scan: bigdocker, nas (not ticked)." — or how to get a scanner. */
fun scannersLine(r: Registries): String =
    if (r.scanners.isEmpty()) "No agent can scan: install trivy or grype next to one, then tick scan on its screen."
    else "Can scan: " + r.scanners.joinToString(", ") { it.agent + if (it.on) "" else " (not ticked)" } + "."

@Composable
private fun ScanLine(s: ScanRow) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(s.name, fontWeight = FontWeight.Medium, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOf(s.agent, s.image, s.tool, if (s.t > 0) stamp(s.t) else "").filter { it.isNotEmpty() }.joinToString(" · "),
                fontSize = 12.sp, color = Lab.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(cveCount(s), fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            color = when { s.critical > 0 -> Lab.red; s.high > 0 -> Lab.warn; else -> Lab.green })
    }
}

/** "2 critical · 5 high", or "clean". */
fun cveCount(s: ScanRow): String =
    if (s.critical == 0 && s.high == 0) "clean"
    else listOfNotNull(if (s.critical > 0) "${s.critical} critical" else null, if (s.high > 0) "${s.high} high" else null).joinToString(" · ")
