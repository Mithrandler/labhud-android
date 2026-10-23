package io.github.mithrandler.labhud.ui.settings

import io.github.mithrandler.labhud.ui.LabScale
import android.content.ClipData
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mithrandler.labhud.data.ApiToken
import io.github.mithrandler.labhud.data.AuditRow
import io.github.mithrandler.labhud.data.LabRepository
import io.github.mithrandler.labhud.data.NewToken
import io.github.mithrandler.labhud.ui.GateDialogs
import io.github.mithrandler.labhud.ui.Lab
import io.github.mithrandler.labhud.ui.Panel
import io.github.mithrandler.labhud.ui.rememberGate
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.time.Instant
import java.util.Date

/**
 * API tokens and the audit log, as on the wall's ⚙ > System > API & audit. Tokens: the list, a new
 * one (shown once, in a dialog, to copy), Revoke. The log: newest first, all or only the refusals,
 * filtered by who, "Older" for the next page. Loaded behind the gate, like [PhonesSection].
 */
@Composable
fun ApiSection(repo: LabRepository, nav: SettingsNav) {
    val scope = rememberCoroutineScope()
    val gate = rememberGate(repo)
    var tokens by remember { mutableStateOf<List<ApiToken>?>(null) }
    var enabled by remember { mutableStateOf(true) }
    var rows by remember { mutableStateOf<List<AuditRow>>(emptyList()) }
    var more by remember { mutableStateOf(false) }
    var onlyRefused by remember { mutableStateOf(false) }
    var who by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var making by remember { mutableStateOf(false) }
    var made by remember { mutableStateOf<NewToken?>(null) }
    var revoking by remember { mutableStateOf<ApiToken?>(null) }

    fun loadAudit(append: Boolean = false) = scope.launch {
        val api = repo.api() ?: return@launch
        runCatching { api.audit(who, if (onlyRefused) false else null, if (append) rows.size else 0) }
            .onSuccess { p -> rows = if (append) rows + p.rows else p.rows; more = p.more }
            .onFailure { message = it.message ?: "The log was not read." }
    }
    fun load() = scope.launch {
        val api = repo.api() ?: return@launch
        message = ""
        runCatching { api.apiTokens() }.onSuccess { r ->
            if (r.ok) { tokens = r.tokens; enabled = r.enabled } else message = r.error.ifBlank { "Refused." }
        }.onFailure { message = it.message ?: "Not loaded." }
        loadAudit()
    }
    LaunchedEffect(Unit) { gate.press { load() } }

    SettingsPage(section("api")!!, nav) {
        if (tokens == null) item { Unlock { gate.press { load() } } }
        if (message.isNotEmpty()) item { Text(message, color = Lab.warn, fontSize = LabScale.textSm) }
        tokens?.let { list ->
            item {
                Panel("API tokens", if (!enabled) "Need a data folder" else if (list.isEmpty()) "None yet" else "${list.size} token(s)") {
                    Text("For scripts: Authorization: Bearer lh_…. read sees the state, act also presses the cards' buttons, admin also changes settings.",
                        fontSize = LabScale.textSm, color = Lab.muted, modifier = Modifier.padding(bottom = LabScale.space2))
                    if (!enabled) Text("Tokens need LABHUD_DATA (a folder LabHUD can write).", color = Lab.muted, fontSize = LabScale.textMd)
                    else if (list.isEmpty()) Text("No token yet.", color = Lab.muted, fontSize = LabScale.textMd)
                    list.forEach { t -> TokenRow(t) { revoking = t } }
                    if (enabled) Button(onClick = { making = true }, modifier = Modifier.padding(top = LabScale.space2)) { Text("Make a token") }
                }
            }
            item {
                Panel("Audit log", "Who did what, kept 90 days") {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(LabScale.space2)) {
                        listOf(false to "All", true to "Refused").forEach { (refused, label) ->
                            FilterChip(selected = onlyRefused == refused, onClick = { onlyRefused = refused; loadAudit() }, label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Lab.accent.copy(alpha = 0.2f), selectedLabelColor = Lab.title))
                        }
                    }
                    OutlinedTextField(who, { who = it.take(60) }, singleLine = true, label = { Text("Who (admin, phone, token…)") },
                        modifier = Modifier.fillMaxWidth().padding(vertical = LabScale.space1),
                        trailingIcon = { TextButton(onClick = { loadAudit() }) { Text("Filter") } })
                    if (rows.isEmpty()) Text("Nothing matches.", color = Lab.muted, fontSize = LabScale.textMd, modifier = Modifier.padding(top = LabScale.space1))
                    rows.forEachIndexed { i, r ->
                        if (i > 0) HorizontalDivider(color = Lab.line)
                        AuditLine(r)
                    }
                    if (more) TextButton(onClick = { loadAudit(append = true) }) { Text("Older") }
                    Text("Kept 90 days.", fontSize = LabScale.textXs, color = Lab.muted, modifier = Modifier.padding(top = LabScale.space1))
                }
            }
        }
    }

    if (making) MakeTokenDialog(onDismiss = { making = false }) { name, sc, days ->
        making = false
        gate.press {
            scope.launch {
                val r = runCatching { repo.api()?.makeToken(name, sc, days) }.getOrElse { NewToken(error = it.message ?: "Not made.") }
                if (r?.ok == true) { made = r; load() } else message = r?.error?.ifBlank { null } ?: "Not made."
            }
        }
    }
    made?.let { t -> ShowOnceDialog(t) { made = null } }
    revoking?.let { t ->
        AlertDialog(
            onDismissRequest = { revoking = null },
            title = { Text("Revoke ${t.name}?") },
            text = { Text("Whatever uses it stops working at once.") },
            confirmButton = {
                TextButton(onClick = {
                    revoking = null
                    gate.press {
                        scope.launch {
                            val r = runCatching { repo.api()?.revokeToken(t.id) }.getOrNull()
                            if (r?.ok == true) load() else message = r?.error?.ifBlank { null } ?: "Not revoked."
                        }
                    }
                }) { Text("Revoke", color = Lab.red) }
            },
            dismissButton = { TextButton(onClick = { revoking = null }) { Text("Cancel") } },
        )
    }
    GateDialogs(gate, nav.signIn)
}

@Composable
private fun TokenRow(t: ApiToken, onRevoke: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = LabScale.space1), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t.name, fontWeight = FontWeight.Medium, fontSize = LabScale.textMd, color = if (t.expired) Lab.muted else Lab.text,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                ScopeTag(t.scope)
            }
            Text(listOf(
                "made " + stamp(t.made),
                if (t.last > 0) "used " + stamp(t.last) + (if (t.ip.isNotEmpty()) " from ${t.ip}" else "") else "never used",
                when { t.expired -> "expired"; t.expires > 0 -> "expires " + day(t.expires); else -> "no expiry" },
            ).joinToString(" · "), fontSize = LabScale.textSm, color = Lab.muted)
        }
        TextButton(onClick = onRevoke) { Text("Revoke", color = Lab.red) }
    }
}

@Composable
fun ScopeTag(scope: String) {
    val c = scopeColor(scope)
    Text(scope.uppercase(), fontSize = LabScale.textXs, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp, color = c,
        modifier = Modifier.padding(start = LabScale.space2).clip(RoundedCornerShape(LabScale.radiusInner)).background(c.copy(alpha = 0.18f))
            .padding(horizontal = LabScale.space2, vertical = LabScale.spaceHalf))
}

fun scopeColor(scope: String): Color = when (scope) {
    "admin" -> Lab.red
    "act" -> Lab.warn
    else -> Lab.sub
}

@Composable
private fun AuditLine(r: AuditRow) {
    Column(Modifier.fillMaxWidth().padding(vertical = LabScale.space2)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(r.action + if (r.target.isNotEmpty()) " · ${r.target}" else "", fontWeight = FontWeight.Medium, fontSize = LabScale.textSm,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(if (r.ok) "ok" else "refused", fontSize = LabScale.textSm, color = if (r.ok) Lab.green else Lab.red,
                fontWeight = if (r.ok) FontWeight.Normal else FontWeight.SemiBold)
        }
        Text(listOf(auditTime(r.ts), r.who, r.ip).filter { it.isNotEmpty() }.joinToString(" · "), fontSize = LabScale.textSm, color = Lab.muted,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MakeTokenDialog(onDismiss: () -> Unit, onMake: (String, String, Int?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var scope by remember { mutableStateOf("read") }
    var days by remember { mutableStateOf<Int?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("A new API token") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(LabScale.space2)) {
                OutlinedTextField(name, { name = it.take(40) }, singleLine = true, label = { Text("Name (e.g. home-assistant)") }, modifier = Modifier.fillMaxWidth())
                Text("May", fontSize = LabScale.textSm, color = Lab.muted)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(LabScale.space2)) {
                    listOf("read", "act", "admin").forEach { s ->
                        FilterChip(selected = scope == s, onClick = { scope = s }, label = { Text(s) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = scopeColor(s).copy(alpha = 0.2f), selectedLabelColor = scopeColor(s)))
                    }
                }
                Text("Expires", fontSize = LabScale.textSm, color = Lab.muted)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(LabScale.space2)) {
                    listOf(null to "never", 30 to "30 days", 90 to "90 days", 365 to "1 year").forEach { (d, label) ->
                        FilterChip(selected = days == d, onClick = { days = d }, label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Lab.accent.copy(alpha = 0.2f), selectedLabelColor = Lab.title))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onMake(name.trim(), scope, days) }, enabled = name.isNotBlank()) { Text("Make") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** The token, once: LabHUD keeps only its hash. Copied as sensitive (hidden from the clipboard preview). */
@Composable
private fun ShowOnceDialog(t: NewToken, onDone: () -> Unit) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = {},
        title = { Text("${t.name}: copy it now") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(LabScale.space2)) {
                Text("LabHUD keeps only its hash and will not show it again.", fontSize = LabScale.textSm, color = Lab.sub)
                SelectionContainer {
                    Text(t.token, fontFamily = FontFamily.Monospace, fontSize = LabScale.textSm,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(LabScale.radiusInner)).background(Lab.block).padding(LabScale.space2))
                }
                if (copied) Text("Copied.", fontSize = LabScale.textSm, color = Lab.green)
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text("Done") } },
        dismissButton = { TextButton(onClick = { copySecret(context, t.token); copied = true }) { Text("Copy") } },
    )
}

private fun copySecret(context: Context, text: String) {
    val clip = ClipData.newPlainText("LabHUD API token", text)
    if (Build.VERSION.SDK_INT >= 33) clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
}

/** A Unix time as the phone's short date. */
private fun day(t: Long): String = DateFormat.getDateInstance(DateFormat.SHORT).format(Date(t * 1000))

/** An entry's ISO time (UTC) as the phone's short date and time; as written if it does not parse. */
fun auditTime(ts: String): String = runCatching { stamp(Instant.parse(ts).epochSecond) }.getOrDefault(ts)
