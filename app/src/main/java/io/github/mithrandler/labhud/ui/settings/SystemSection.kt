package io.github.mithrandler.labhud.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mithrandler.labhud.BuildConfig
import io.github.mithrandler.labhud.data.BackupData
import io.github.mithrandler.labhud.data.Backups
import io.github.mithrandler.labhud.data.LabRepository
import io.github.mithrandler.labhud.data.backups
import io.github.mithrandler.labhud.data.deleteBackup
import io.github.mithrandler.labhud.data.downloadBackup
import io.github.mithrandler.labhud.data.makeBackup
import io.github.mithrandler.labhud.data.newKeys
import io.github.mithrandler.labhud.data.removePhones
import io.github.mithrandler.labhud.data.restoreBackup
import io.github.mithrandler.labhud.data.saveBackupSettings
import io.github.mithrandler.labhud.ui.GateDialogs
import io.github.mithrandler.labhud.ui.Lab
import io.github.mithrandler.labhud.ui.Panel
import io.github.mithrandler.labhud.ui.rememberGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Base64

/**
 * System: what this LabHUD and this app are, the config file (on the wall), and for the admin,
 * behind the gate, Backup and restore ([BackupPanel]) and the Danger zone ([DangerPanel]).
 */
@Composable
fun SystemSection(repo: LabRepository, nav: SettingsNav) {
    val info by repo.info.collectAsState()
    val server by repo.server.collectAsState()
    val rights by repo.rights.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val gate = rememberGate(repo)
    var backups by remember { mutableStateOf<Backups?>(null) }
    var message by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf<BackupData?>(null) }

    fun load() = scope.launch {
        val api = repo.api() ?: return@launch
        runCatching { api.backups() }.onSuccess { b -> if (b.ok) backups = b else message = b.error.ifBlank { "Backups refused." } }
            .onFailure { message = it.message ?: "Not loaded." }
    }
    /** A call behind the gate: [done] of its answer, or why not, in the message line; the backups anew. */
    fun <T> act(call: suspend () -> T?, ok: (T) -> Boolean, error: (T) -> String, done: (T) -> String) = gate.press {
        scope.launch {
            val r = runCatching { call() }.getOrElse { message = "✕ " + (it.message ?: "not done"); return@launch } ?: return@launch
            message = if (ok(r)) done(r) else "✕ " + error(r).ifBlank { "refused" }
            if (ok(r)) load()
        }
    }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val d = saving ?: return@rememberLauncherForActivityResult
        saving = null
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            message = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(Base64.getDecoder().decode(d.data)) } ?: error("not writable")
                }
                "✓ ${d.name} saved (${backupSize(d.size)})."
            }.getOrElse { "✕ Not saved: " + (it.message ?: "") }
        }
    }
    LaunchedEffect(rights.admin) { if (rights.admin && repo.mayPress()) load() }

    SettingsPage(section("system")!!, nav) {
        heading("About")
        item {
            Panel("") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BrandMark(40.dp)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(info?.title ?: server?.name ?: "LabHUD", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Text("LabHUD " + (info?.version?.ifEmpty { null } ?: "?") + (if (info?.demo == true) " · demo" else ""),
                            fontSize = 12.sp, color = Lab.muted)
                        Text("This app ${BuildConfig.VERSION_NAME}", fontSize = 12.sp, color = Lab.muted)
                    }
                }
                Text("github.com/Mithrandler/labhud · github.com/Mithrandler/labhud-android", fontSize = 12.sp, color = Lab.muted,
                    modifier = Modifier.padding(top = 10.dp))
            }
        }
        heading("config.toml")
        item { WallOnly("⚙ > System", listOf("Take over the config file", "The editor and its keys")) }
        if (rights.admin) {
            heading("Backup and restore")
            if (backups == null) item { Unlock { gate.press { load() } } }
            if (message.isNotEmpty()) item { Text(message, color = if (message.startsWith("✓")) Lab.muted else Lab.warn, fontSize = 13.sp) }
            backups?.let { b ->
                item {
                    BackupPanel(b,
                        onMake = { secrets, pw -> act({ repo.api()?.makeBackup(secrets, pw) }, { it.ok }, { it.error }) { "✓ ${it.name} made" + if (it.encrypted) ", encrypted." else "." } },
                        onSettings = { daily, keep, at -> act({ repo.api()?.saveBackupSettings(daily, keep, at) }, { it.ok }, { it.error }) { "" } },
                        onSave = { f ->
                            gate.press {
                                scope.launch {
                                    val d = runCatching { repo.api()?.downloadBackup(f.name) }.getOrNull()
                                    if (d?.ok == true) { saving = d; save.launch(d.name) } else message = "✕ " + (d?.error?.ifBlank { null } ?: "not downloaded")
                                }
                            }
                        },
                        onDelete = { f -> act({ repo.api()?.deleteBackup(f.name) }, { it.ok }, { it.error }) { "${f.name} deleted." } },
                        onRestore = { f, pw -> act({ repo.api()?.restoreBackup(f.name, pw) }, { it.ok }, { it.error }) { "✓ Restored; LabHUD restarts. What was there is in ${it.before}." } })
                }
                heading("Danger zone")
                item {
                    DangerPanel(
                        onRemovePhones = { act({ repo.api()?.removePhones() }, { it.ok }, { it.error }) { "✓ ${it.phones} phone(s) removed: pair this one again." } },
                        onNewKeys = { act({ repo.api()?.newKeys() }, { it.ok }, { it.error }) { "✓ New keys: add the ${it.agents} agents and pair the ${it.phones} phones again." } })
                }
            }
        }
    }
    GateDialogs(gate, nav.signIn)
}
