package io.github.mithrandler.labhud.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import io.github.mithrandler.labhud.data.Action
import io.github.mithrandler.labhud.data.Card
import io.github.mithrandler.labhud.data.History
import io.github.mithrandler.labhud.data.LabRepository
import io.github.mithrandler.labhud.data.Section
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

private val RANGES = listOf("6h" to 6 * 3600, "24h" to 86400, "7d" to 7 * 86400)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CardScreen(repo: LabRepository, id: String, onBack: () -> Unit, onHistory: (String) -> Unit, onSignIn: () -> Unit) {
    val state by repo.state.collectAsState()
    val live = state?.byId?.get(id)
    var sections by remember { mutableStateOf<List<Section>>(emptyList()) }
    var full by remember { mutableStateOf<Card?>(null) }
    var logs by remember { mutableStateOf<Pair<String, String>?>(null) }
    val rights by repo.rights.collectAsState()
    val gate = rememberGate(repo)
    var history by remember { mutableStateOf<History?>(null) }
    var long by remember { mutableStateOf<History?>(null) }
    var range by remember { mutableStateOf(0) }
    var ask by remember { mutableStateOf<Action?>(null) }
    var askMaint by remember { mutableStateOf(false) }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // the panel and the charts: fetched when opened, then every 30 s while it stays open
    LaunchedEffect(id) {
        while (true) {
            val api = repo.api()
            if (api != null) {
                runCatching { api.card(id) }.onSuccess { sections = it.sections; full = it }
                if (live?.metrics?.any { it.trend } == true || live?.charts?.isNotEmpty() == true) runCatching { api.history(false) }.onSuccess { history = it }
            }
            delay(30_000)
        }
    }
    LaunchedEffect(range) {
        if (range > 0 && long == null) repo.api()?.let { api -> runCatching { api.history(true) }.onSuccess { long = it } }
    }

    fun send(name: String, label: String) = scope.launch { snack.showSnackbar(sendAction(repo, name, label)) }

    Scaffold(
        containerColor = Lab.bg,
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Lab.bg),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = { Text(live?.name ?: "", fontWeight = FontWeight.SemiBold) },
            )
        },
    ) { pad ->
        val c = live ?: run {
            Text("This card is not in the current config.", color = Lab.muted, modifier = Modifier.padding(pad).padding(24.dp))
            return@Scaffold
        }
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { StatusHeader(c) }
            if ((c.actions.isNotEmpty() || c.maintenanceButton) && !rights.admin) item {
                Surface(onClick = onSignIn, shape = CardShape, color = Lab.block, border = BorderStroke(1.dp, Lab.line)) {
                    Text("This phone only looks. Sign in as the admin for the buttons.", fontSize = 13.sp, color = Lab.sub,
                        modifier = Modifier.fillMaxWidth().padding(12.dp))
                }
            }
            val buttons = rights.admin && (c.actions.isNotEmpty() || c.maintenanceButton)
            if (c.resources().isNotEmpty()) item {
                OutlinedButton(onClick = { onHistory(c.id) }) { Text("Resources over time") }
            }
            if (buttons || c.url.isNotEmpty() || c.links.isNotEmpty()) item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (rights.admin) c.actions.forEach { a -> ActionButton(a) { gate.press { ask = a } } }
                    if (rights.admin && c.maintenanceButton) {
                        OutlinedButton(onClick = { gate.press { askMaint = true } }) { Text(if (c.maintenanceUntil != null) "Maintenance ✓" else "Maintenance") }
                    }
                    (listOfNotNull(c.url.takeIf { it.isNotEmpty() }?.let { "Web UI" to it }) + c.links.map { it.label to it.url }).forEach { (label, url) ->
                        OutlinedButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }) {
                            Text(label)
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.padding(0.dp))
                        }
                    }
                }
            }
            if (c.metrics.isNotEmpty()) item {
                Panel("Numbers") {
                    c.metrics.forEach { m ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(m.label, color = Lab.sub, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            Text(m.value, color = sevColor(m.sev), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
            val labels = c.metrics.filter { it.trend }.map { it.label }.toSet()
            val trends = c.metrics.filter { it.trend } + c.charts.filter { it.label !in labels }.map {
                io.github.mithrandler.labhud.data.Metric(key = it.key, label = it.label, value = "", trend = true, format = it.format)
            }
            val boxes = full?.containers.orEmpty()
            if (boxes.isNotEmpty()) item {
                Panel("Containers") {
                    boxes.forEach { b ->
                        ContainerRow(b, admin = rights.admin,
                            onAction = { a -> gate.press { ask = a } },
                            onLogs = {
                                gate.press {
                                    scope.launch {
                                        val r = runCatching { repo.api()?.logs(full?.agent.orEmpty(), b.name) }.getOrNull()
                                        logs = b.name to (if (r?.ok == true) r.output.ifBlank { "(nothing)" } else "✕ " + (r?.error?.ifBlank { null } ?: "refused"))
                                    }
                                }
                            })
                    }
                }
            }
            if (trends.isNotEmpty()) item {
                Panel("History") {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        RANGES.forEachIndexed { i, (label, _) ->
                            SegmentedButton(selected = range == i, onClick = { range = i },
                                shape = SegmentedButtonDefaults.itemShape(i, RANGES.size)) { Text(label) }
                        }
                    }
                    val h = if (range == 0) history else long
                    trends.forEach { m ->
                        val series = h?.series?.get(m.key).orEmpty()
                        val keep = if (h == null || h.step == 0) series.size else (RANGES[range].second / h.step).coerceAtMost(series.size)
                        Text(if (m.value.isEmpty()) m.label else "${m.label}  ${m.value}", fontSize = 13.sp, color = Lab.sub, modifier = Modifier.padding(top = 6.dp))
                        if (series.size >= 2) LineChart(series.takeLast(keep), color = Lab.accent)
                        else Text("No history yet", fontSize = 12.sp, color = Lab.muted)
                    }
                }
            }
            // labhud's own "numbers" section repeats the metrics, already shown above
            sections.filterNot { it.title.equals("numbers", true) && c.metrics.isNotEmpty() }.forEach { s ->
                item {
                    Panel(s.title) { s.rows.forEach { ListRow(it) } }
                }
            }
            if (sections.isEmpty() && c.rows.isNotEmpty()) item { Panel("Details") { c.rows.forEach { ListRow(it) } } }
        }
    }

    ask?.let { a -> ActionDialog(a, onSend = { name, label -> send(name, label) }, onDismiss = { ask = null }) }
    GateDialogs(gate, onSignIn)
    logs?.let { (name, text) ->
        AlertDialog(
            onDismissRequest = { logs = null },
            title = { Text(name) },
            text = {
                Text(text, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp,
                    modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()))
            },
            confirmButton = { TextButton(onClick = { logs = null }) { Text("Close") } },
        )
    }
    if (askMaint) {
        val options = listOf("30 minutes" to 30, "1 hour" to 60, "2 hours" to 120, "1 day" to 1440) +
            if (live?.maintenanceUntil != null) listOf("End now" to 0) else emptyList()
        AlertDialog(
            onDismissRequest = { askMaint = false },
            title = { Text("Maintenance") },
            text = { Text("Down on purpose: grey, no notification, no focus.") },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    options.forEach { (label, minutes) ->
                        TextButton(onClick = {
                            askMaint = false
                            scope.launch {
                                val r = repo.api()?.maintenance(id, minutes)
                                snack.showSnackbar(if (r?.ok == true) "Maintenance: ${label.lowercase()}" else "Maintenance: ${r?.error ?: "refused"}")
                            }
                        }) { Text(label) }
                    }
                }
            },
            dismissButton = { TextButton(onClick = { askMaint = false }) { Text("Cancel") } },
        )
    }
}

/** A container: its dot, name and state, "update available", its buttons and (admin) LOGS. */
@Composable
private fun ContainerRow(b: io.github.mithrandler.labhud.data.Container, admin: Boolean, onAction: (Action) -> Unit, onLogs: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Box(Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape)
                .background(if (b.up) Lab.green else Lab.red))
            Spacer(Modifier.width(10.dp))
            Text(b.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(when { b.updating -> "updating…"; b.update -> "update available"; else -> b.status }, fontSize = 12.sp,
                color = if (b.update || b.updating) Lab.accent else if (b.up) Lab.muted else Lab.red, maxLines = 1)
        }
        if (admin) Row(Modifier.padding(start = 14.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            b.actions.forEach { a ->
                TextButton(onClick = { onAction(a) }, enabled = !b.updating) {
                    Text(niceLabel(a), fontSize = 12.sp, color = if (a.danger) Lab.red else Lab.accent)
                }
            }
            TextButton(onClick = onLogs) { Text("Logs", fontSize = 12.sp) }
        }
    }
}

@Composable
private fun StatusHeader(c: Card) {
    Surface(shape = CardShape, color = Lab.card, border = BorderStroke(1.dp, if (c.bad == true) Lab.red.copy(alpha = 0.7f) else Lab.line)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (c.status != null) { StatusDot(c, 12); Spacer(Modifier.width(10.dp)) }
                Text(
                    when (c.status) {
                        "up" -> "Running"; "down" -> "DOWN"; "scheduled" -> "Off on schedule"; "on-demand" -> "Off · started on demand"
                        "maintenance" -> "Maintenance"; "unknown" -> "Not known yet"; else -> if (c.bad == true) "Problem" else "OK"
                    },
                    fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = if (c.bad == true) Lab.red else Lab.text,
                )
            }
            if (c.subtitle.isNotEmpty()) Text(c.subtitle, color = Lab.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            if (c.summary.isNotEmpty() && c.metrics.isEmpty()) Text(c.summary, color = Lab.sub, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
            if (c.note.isNotEmpty()) Text(c.note, color = if (c.noteKind == "error" || c.noteKind == "stale") Lab.warn else Lab.muted,
                fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            c.maintenanceUntil?.let {
                Text("Maintenance until " + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it * 1000)),
                    color = Lab.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
fun Panel(title: String, content: @Composable () -> Unit) {
    Surface(shape = CardShape, color = Lab.card, border = BorderStroke(1.dp, Lab.line)) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            if (title.isNotEmpty()) Text(title.uppercase(), style = Heading, modifier = Modifier.padding(bottom = 6.dp))
            content()
        }
    }
}
