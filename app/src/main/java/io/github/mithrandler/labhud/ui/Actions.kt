package io.github.mithrandler.labhud.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mithrandler.labhud.data.Action
import io.github.mithrandler.labhud.data.LabRepository

/** Sends one action through labhud (signed there) and says how it went, for a snackbar. */
suspend fun sendAction(repo: LabRepository, name: String, label: String): String {
    val api = repo.api() ?: return "$label: no server paired"
    val r = api.action(name)
    api.actionLog(name, if (r.ok) "accepted" else "refused", 0)
    if (!r.ok && isRefusal(r.error)) repo.refreshRights()   // the rights this phone shows may be old
    return if (r.ok) "$label: sent" else "$label: " + refusalText(r.error, repo.rights.value)
}

/** "Are you sure?" for an action: one Yes, or one button per choice (a VM's start/shutdown/reboot). */
@Composable
fun ActionDialog(a: Action, onSend: (name: String, label: String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(niceLabel(a)) },
        text = { if (a.confirm.isNotEmpty()) Text(a.confirm) },
        confirmButton = {
            if (a.choices.isEmpty()) {
                TextButton(onClick = { onDismiss(); onSend(a.name, a.label) }) { Text("Yes", color = if (a.danger) Lab.red else Lab.accent) }
            } else Column(horizontalAlignment = Alignment.End) {
                a.choices.forEach { ch ->
                    TextButton(onClick = { onDismiss(); onSend(ch.action, ch.label) }) { Text(ch.label, color = if (ch.danger) Lab.red else Lab.accent) }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** What an action does, read from its name and label: the icon. */
enum class Kind(val icon: ImageVector) {
    Start(LabIcons.start), Wake(LabIcons.power), Restart(LabIcons.restart),
    Stop(LabIcons.stop), Power(LabIcons.power), Update(LabIcons.update),
    Report(LabIcons.report), Other(LabIcons.bolt),
}

fun kindOf(name: String, label: String): Kind {
    val s = "$name $label".lowercase()
    return when {
        "wol" in s || "wake" in s -> Kind.Wake
        "reboot" in s || "restart" in s -> Kind.Restart
        "shutdown" in s || "power off" in s || "poweroff" in s -> Kind.Power
        "stop" in s -> Kind.Stop
        "start" in s || "power on" in s -> Kind.Start
        "update" in s || "upgrade" in s -> Kind.Update
        "report" in s || "scan" in s || "test" in s -> Kind.Report
        else -> Kind.Other
    }
}

/**
 * The label under a button: an unconfigured action shows its bare name (reboot-pve), a wall label
 * is in capitals (SHUT DOWN); on the phone both read as words.
 */
fun niceLabel(a: Action): String {
    val l = a.label.ifBlank { a.name }
    if (l == a.name && '-' in l) return when (kindOf(a.name, a.label)) {
        Kind.Start -> "Start"; Kind.Wake -> "Wake"; Kind.Restart -> "Reboot"; Kind.Stop -> "Stop"
        Kind.Power -> "Shut down"; Kind.Update -> "Update"; Kind.Report -> "Report"
        Kind.Other -> l.replace('-', ' ').replaceFirstChar(Char::uppercase)
    }
    return if (l == l.uppercase()) l.lowercase().replaceFirstChar(Char::uppercase) else l
}

/** A round button with its icon and a small label under it; a dangerous one has a red edge. */
@Composable
fun ActionButton(a: Action, enabled: Boolean = true, onClick: () -> Unit) {
    val k = if (a.choices.isNotEmpty()) Kind.Power else kindOf(a.name, a.label)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 72.dp)) {
        FilledTonalIconButton(
            onClick = onClick, enabled = enabled,
            modifier = Modifier.size(44.dp).then(if (a.danger) Modifier.border(1.dp, Lab.red.copy(alpha = 0.6f), CircleShape) else Modifier),
            colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Lab.block,
                contentColor = if (a.danger) Lab.red else if (k == Kind.Wake || k == Kind.Start) Lab.green else Lab.accent),
        ) { Icon(k.icon, a.label) }
        Text(niceLabel(a), fontSize = 11.sp, color = Lab.sub, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
    }
}

