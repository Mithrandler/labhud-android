package io.github.mithrandler.labhud.ui.settings

import io.github.mithrandler.labhud.ui.LabScale
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mithrandler.labhud.data.BackupFile
import io.github.mithrandler.labhud.data.Backups
import io.github.mithrandler.labhud.ui.Lab
import io.github.mithrandler.labhud.ui.Panel

/** 1536 -> "1.5 KiB", as the wall shows a backup's size. */
fun backupSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KiB".format(bytes / 1024.0)
    else -> "%.1f MiB".format(bytes / (1024.0 * 1024.0))
}

/** "Last: ✓ daily 21.10.26 03:00", or "Last: ✕ manual … · <error>". */
fun lastLine(b: Backups): String? = b.last?.let { l ->
    (if (l.ok) "Last: ✓ ${l.kind} " else "Last: ✕ ${l.kind} ") + stamp(l.t) + if (!l.ok && l.error.isNotEmpty()) " · ${l.error}" else ""
}

/**
 * ⚙ > System > Backup and restore (api/backup.py): one now (with the secrets only encrypted), the
 * daily one, the backups kept with Save to the phone, Delete and Restore. Uploading one back is done
 * on the wall.
 */
@Composable
fun BackupPanel(
    b: Backups,
    onMake: (Boolean, String) -> Unit,
    onSettings: (Boolean, Int, Int) -> Unit,
    onSave: (BackupFile) -> Unit,
    onDelete: (BackupFile) -> Unit,
    onRestore: (BackupFile, String) -> Unit,
) {
    var secrets by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<BackupFile?>(null) }
    var restoring by remember { mutableStateOf<BackupFile?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(LabScale.space3)) {
        Panel("Backup", b.problem ?: (b.last?.let { "Last: ${it.name}" } ?: "No backup yet")) {
            Text("config.toml and LabHUD's data in one file; the secrets (secrets.env, the HTTPS key) only encrypted, with a password.",
                fontSize = LabScale.textSm, color = Lab.muted, modifier = Modifier.padding(bottom = LabScale.space2))
            b.problem?.let { Text(it, fontSize = LabScale.textSm, color = Lab.warn) }
            if (b.problem == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = secrets, onCheckedChange = { secrets = it }, enabled = b.encryption)
                    Text(if (b.encryption) "With the secrets (encrypted)" else "With the secrets: needs the cryptography package", fontSize = LabScale.textMd)
                }
                if (secrets) OutlinedTextField(password, { password = it.take(256) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (b.passwordSet) "Password (empty: the saved one)" else "Password (at least 8 characters)") },
                    visualTransformation = PasswordVisualTransformation())
                Button(onClick = { onMake(secrets, if (secrets) password else ""); password = "" }, modifier = Modifier.padding(top = LabScale.space2),
                    enabled = !secrets || b.passwordSet || password.length >= 8) { Text("Back up now") }
                lastLine(b)?.let { Text(it, fontSize = LabScale.textSm, color = if (b.last?.ok == false) Lab.warn else Lab.muted, modifier = Modifier.padding(top = LabScale.space2)) }
            }
        }
        if (b.problem == null) {
            Panel("Daily", if (b.settings.daily) "Every day at ${"%02d".format(b.settings.at)}:00, keep ${b.settings.keep}" else "Off") {
                val st = b.settings
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = LabScale.space3)) {
                        Text("A backup every day at ${"%02d".format(st.at)}:00", fontWeight = FontWeight.Medium, fontSize = LabScale.textMd)
                        Text(if (b.passwordSet) "A backup password is saved: the secrets go in too, encrypted." else "No backup password saved: the secrets stay out.",
                            fontSize = LabScale.textSm, color = Lab.muted)
                    }
                    Switch(checked = st.daily, onCheckedChange = { onSettings(it, st.keep, st.at) })
                }
                Text("Keep the last", fontSize = LabScale.textSm, color = Lab.muted, modifier = Modifier.padding(top = LabScale.space2))
                Chips(listOf(3, 7, 14, 30), st.keep, { "$it" }) { onSettings(st.daily, it, st.at) }
                Text("At", fontSize = LabScale.textSm, color = Lab.muted, modifier = Modifier.padding(top = LabScale.space1))
                Chips(listOf(1, 3, 5, 12, 23), st.at, { "%02d:00".format(it) }) { onSettings(st.daily, st.keep, it) }
            }
            Panel("Kept", "${b.backups.size} file(s)") {
                if (b.backups.isEmpty()) Text("No backup yet.", color = Lab.muted, fontSize = LabScale.textMd)
                b.backups.sortedByDescending { it.t }.forEachIndexed { i, f ->
                    if (i > 0) HorizontalDivider(color = Lab.line)
                    Column(Modifier.fillMaxWidth().padding(vertical = LabScale.space1)) {
                        Text(f.name, fontSize = LabScale.textSm, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(listOf(f.kind, stamp(f.t), backupSize(f.size), if (f.encrypted) "encrypted" else "").filter { it.isNotEmpty() }.joinToString(" · "),
                            fontSize = LabScale.textSm, color = Lab.muted)
                        Row {
                            TextButton(onClick = { onSave(f) }) { Text("Save to the phone") }
                            TextButton(onClick = { restoring = f }) { Text("Restore", color = Lab.warn) }
                            TextButton(onClick = { deleting = f }) { Text("Delete", color = Lab.red) }
                        }
                    }
                }
                Text("They are on LabHUD's disk: save one elsewhere too. Uploading one back is done on the wall.",
                    fontSize = LabScale.textXs, color = Lab.muted, modifier = Modifier.padding(top = LabScale.space1))
            }
        }
    }
    deleting?.let { f ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete this backup?") },
            text = { Text(f.name) },
            confirmButton = { TextButton(onClick = { deleting = null; onDelete(f) }) { Text("Delete", color = Lab.red) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
    restoring?.let { f ->
        TypedWordDialog("Restore ${f.name}?",
            "Replaces config.toml and LabHUD's data with this backup (what is there now is backed up first), then LabHUD restarts.",
            "RESTORE", password = f.encrypted, onDismiss = { restoring = null }) { pw -> restoring = null; onRestore(f, pw) }
    }
}

@Composable
private fun <T> Chips(options: List<T>, now: T, label: (T) -> String, pick: (T) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(LabScale.space2)) {
        (if (now in options) options else options + now).forEach { o ->
            FilterChip(selected = o == now, onClick = { if (o != now) pick(o) }, label = { Text(label(o)) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Lab.accent.copy(alpha = 0.2f), selectedLabelColor = Lab.title))
        }
    }
}

/** ⚙ > System's Danger zone, as on the wall: each behind its typed word, none can be undone. */
@Composable
fun DangerPanel(onRemovePhones: () -> Unit, onNewKeys: () -> Unit) {
    var asking by remember { mutableStateOf<String?>(null) }
    Panel("Danger zone", "Each needs its word typed first") {
        Text("Each needs its word typed first. None of them can be undone.", fontSize = LabScale.textSm, color = Lab.muted, modifier = Modifier.padding(bottom = LabScale.space2))
        DangerRow("Remove every phone", "Every paired LabHUD app stops working until it is paired again, this one too.") { asking = "REMOVE" }
        HorizontalDivider(color = Lab.line)
        DangerRow("New keys", "New keys for the agents and the phones: each has to be added again, this phone too.") { asking = "NEW KEYS" }
    }
    when (asking) {
        "REMOVE" -> TypedWordDialog("Remove every phone?", "This phone is unpaired too.", "REMOVE", onDismiss = { asking = null }) { asking = null; onRemovePhones() }
        "NEW KEYS" -> TypedWordDialog("New keys?", "Every agent and phone has to be added again, this one too.", "NEW KEYS", onDismiss = { asking = null }) { asking = null; onNewKeys() }
    }
}

@Composable
private fun DangerRow(title: String, hint: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = LabScale.space2), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = LabScale.space3)) {
            Text(title, fontWeight = FontWeight.Medium, fontSize = LabScale.textMd)
            Text(hint, fontSize = LabScale.textSm, color = Lab.muted)
        }
        OutlinedButton(onClick = onClick) { Text(title.substringBefore(' '), color = Lab.red) }
    }
}

/** Asks for [word] typed exactly (and a password when [password]); [onDone] gets the password, or "". */
@Composable
fun TypedWordDialog(title: String, text: String, word: String, password: Boolean = false, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var typed by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(LabScale.space2)) {
                Text(text, fontSize = LabScale.textSm, color = Lab.sub)
                OutlinedTextField(typed, { typed = it.take(20) }, singleLine = true, label = { Text("Type $word") }, modifier = Modifier.fillMaxWidth())
                if (password) OutlinedTextField(pw, { pw = it.take(256) }, singleLine = true, label = { Text("Its password") },
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onDone(pw) }, enabled = typed == word && (!password || pw.isNotEmpty())) { Text(word.lowercase().replaceFirstChar { it.uppercase() }, color = Lab.red) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
