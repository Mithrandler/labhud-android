package io.github.mithrandler.labhud.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mithrandler.labhud.data.Action
import io.github.mithrandler.labhud.data.Card
import io.github.mithrandler.labhud.data.LabRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Sends one action through labhud (signed there) and says how it went, for a snackbar. */
suspend fun sendAction(repo: LabRepository, name: String, label: String): String {
    val api = repo.api() ?: return "$label: no server paired"
    val r = api.action(name)
    api.actionLog(name, if (r.ok) "accepted" else "refused", 0)
    return if (r.ok) "$label: sent" else "$label: ${r.error.ifBlank { "refused" }}"
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

/** What an action does, read from its name and label: the icon, and the state that means "done". */
private enum class Kind(val icon: ImageVector, val want: Boolean?) {
    Start(Icons.Default.PlayArrow, true), Wake(Icons.Default.PowerSettingsNew, true), Restart(Icons.Default.RestartAlt, null),
    Stop(Icons.Default.Stop, false), Power(Icons.Default.PowerSettingsNew, false), Update(Icons.Default.SystemUpdateAlt, null),
    Report(Icons.Default.Assessment, null), Other(Icons.Default.Bolt, null),
}

private fun kindOf(name: String, label: String): Kind {
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

/** An action after it was sent: sending → accepted → done (the card reached the state it asked for) or failed. */
private data class Track(val label: String, val want: Boolean?, val started: Long, val phase: String, val error: String = "")

private const val TRACK_MS = 5 * 60_000L

/** Every button labhud offers, card by card, with the page as a filter; each press followed until done. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionsScreen(repo: LabRepository, onCard: (String) -> Unit) {
    val state by repo.state.collectAsState()
    val info by repo.info.collectAsState()
    val scope = rememberCoroutineScope()
    var ask by remember { mutableStateOf<Pair<Card, Action>?>(null) }
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    val tracks = remember { mutableStateMapOf<String, Track>() }

    // a sent action is done when its card shows the state it asked for, or after 5 minutes
    LaunchedEffect(state, tracks.size) {
        val byId = state?.byId.orEmpty()
        tracks.toMap().forEach { (id, t) ->
            if (t.phase != "waiting") return@forEach
            val up = byId[id]?.status == "up"
            when {
                t.want == null || up == t.want -> tracks[id] = t.copy(phase = "done")
                System.currentTimeMillis() - t.started > TRACK_MS -> tracks[id] = t.copy(phase = "failed", error = "no change seen")
            }
        }
    }
    // finished ones fade out after a while
    LaunchedEffect(tracks.values.count { it.phase == "done" || it.phase == "failed" }) {
        delay(8_000)
        tracks.toMap().forEach { (id, t) ->
            if ((t.phase == "done" || t.phase == "failed") && System.currentTimeMillis() - t.started > 8_000) tracks.remove(id)
        }
    }

    fun send(c: Card, name: String, label: String) {
        val want = kindOf(name, label).want
        tracks[c.id] = Track(niceLabel(Action(name, label)), want, System.currentTimeMillis(), "sending")
        scope.launch {
            val api = repo.api()
            val r = api?.action(name)
            api?.actionLog(name, if (r?.ok == true) "accepted" else "refused", 0)
            val nice = niceLabel(Action(name, label))
            tracks[c.id] = if (r?.ok == true) Track(nice, want, System.currentTimeMillis(), if (want == null) "done" else "waiting")
            else Track(nice, want, System.currentTimeMillis(), "failed", r?.error?.ifBlank { null } ?: "refused")
        }
    }

    Scaffold(
        containerColor = Lab.bg,
        topBar = { TopAppBar(colors = TopAppBarDefaults.topAppBarColors(containerColor = Lab.bg), title = { Text("Actions") }) },
    ) { pad ->
        val st = state
        val byId = st?.byId.orEmpty()
        val seen = HashSet<String>()
        val sections: List<Pair<String, List<Card>>> = st?.pages.orEmpty().mapNotNull { p ->
            val cards = p.groups.flatMap { it.cards }.mapNotNull { byId[it] }.filter { it.actions.isNotEmpty() && seen.add(it.id) }
            if (cards.isEmpty()) null else p.title to cards
        }
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (sections.size > 1) Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                (listOf<String?>(null) + sections.map { it.first }).forEach { t ->
                    FilterChip(selected = filter == t, onClick = { filter = t }, label = { Text(t?.let { it.lowercase().replaceFirstChar(Char::uppercase) } ?: "All") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Lab.accent.copy(alpha = 0.2f), selectedLabelColor = Lab.title))
                }
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (st == null) {
                    item { Text("Connecting…", color = Lab.muted) }
                } else if (sections.isEmpty()) {
                    item {
                        Panel("") {
                            Text(
                                if (info?.actions == false) "This LabHUD does not take actions. On the server they need " +
                                    "LABHUD_ACTION_URL and LABHUD_ACTION_SECRET, with the same secret on the agent that runs them."
                                else "No card on this LabHUD has buttons.",
                                color = Lab.sub, fontSize = 14.sp,
                            )
                        }
                    }
                }
                sections.filter { filter == null || it.first == filter }.forEach { (title, cards) ->
                    if (filter == null) item(key = "h-$title") { Text(title.uppercase(), style = Heading, modifier = Modifier.padding(top = 6.dp)) }
                    items(cards, key = { "a-" + it.id }) { c -> ActionCard(c, tracks[c.id], onOpen = { onCard(c.id) }, onPress = { a -> ask = c to a }) }
                }
            }
        }
    }
    ask?.let { (c, a) ->
        ActionDialog(a, onSend = { name, label -> send(c, name, label) }, onDismiss = { ask = null })
    }
}

@Composable
private fun ActionCard(c: Card, track: Track?, onOpen: () -> Unit, onPress: (Action) -> Unit) {
    Surface(onClick = onOpen, shape = CardShape, color = Lab.card,
        border = BorderStroke(1.dp, if (c.bad == true) Lab.red.copy(alpha = 0.7f) else Lab.line)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (c.status != null) { StatusDot(c, 10); Spacer(Modifier.width(8.dp)) }
                        Text(c.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    val sub = statusWord(c).ifEmpty { c.summary }
                    if (sub.isNotEmpty()) Text(sub, fontSize = 12.sp, color = Lab.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp))
                }
                Spacer(Modifier.width(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    c.actions.take(4).forEach { a -> ActionButton(a, enabled = track?.phase != "sending" && track?.phase != "waiting") { onPress(a) } }
                }
            }
            if (track != null) TrackLine(track)
        }
    }
}

@Composable
private fun TrackLine(t: Track) {
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            when (t.phase) {
                "done" -> Icon(Icons.Default.Check, null, tint = Lab.green, modifier = Modifier.size(16.dp))
                "failed" -> Icon(Icons.Default.Close, null, tint = Lab.red, modifier = Modifier.size(16.dp))
            }
            if (t.phase == "done" || t.phase == "failed") Spacer(Modifier.width(6.dp))
            Text(
                "${t.label}: " + when (t.phase) {
                    "sending" -> "sending…"; "waiting" -> if (t.want == true) "starting…" else "stopping…"
                    "done" -> "done"; else -> t.error
                },
                fontSize = 12.sp, color = when (t.phase) { "done" -> Lab.green; "failed" -> Lab.red; else -> Lab.sub },
            )
        }
        if (t.phase == "sending" || t.phase == "waiting") LinearProgressIndicator(Modifier.fillMaxWidth().height(3.dp).padding(top = 6.dp),
            color = Lab.accent, trackColor = Lab.line)
    }
}
