package io.github.mithrandler.labhud.ui

import android.content.Intent
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import io.github.mithrandler.labhud.data.Metric
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mithrandler.labhud.data.Action
import io.github.mithrandler.labhud.data.Card
import io.github.mithrandler.labhud.data.History
import io.github.mithrandler.labhud.data.LabRepository
import io.github.mithrandler.labhud.data.Section
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

private val RANGES = listOf("6h" to 6 * 3600, "24h" to 86400, "7d" to 7 * 86400, "30d" to 30 * 86400, "1y" to 365 * 86400)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CardScreen(repo: LabRepository, id: String, onBack: () -> Unit, onHistory: (String) -> Unit, onSignIn: () -> Unit,
               /** A button picked in the command palette: it asks once the card is here, as if pressed. */
               askFirst: String = "",
               /** Another card (a VM's host, a host's VM): opens its screen. */
               onCard: (String) -> Unit = {}) {
    val state by repo.state.collectAsStateWithLifecycle()
    val live = state?.byId?.get(id)
    var sections by remember { mutableStateOf<List<Section>>(emptyList()) }
    var full by remember { mutableStateOf<Card?>(null) }
    var logs by remember { mutableStateOf<Pair<String, String>?>(null) }
    var liveLog by remember { mutableStateOf<Pair<String, String>?>(null) }
    val rights by repo.rights.collectAsStateWithLifecycle()
    val gate = rememberGate(repo)
    var history by remember { mutableStateOf<History?>(null) }
    var long by remember { mutableStateOf<History?>(null) }
    var year by remember { mutableStateOf<History?>(null) }
    var askStack by remember { mutableStateOf<Pair<String, List<Action>>?>(null) }
    var askAll by remember { mutableStateOf<List<Action>?>(null) }
    var details by remember { mutableStateOf<Pair<String, List<Pair<String, String>>>?>(null) }
    var notesText by remember { mutableStateOf<String?>(null) }
    var range by remember { mutableStateOf(0) }
    val server by repo.server.collectAsStateWithLifecycle()
    val prefs by repo.store.settings.collectAsStateWithLifecycle(initialValue = io.github.mithrandler.labhud.data.AppSettings())
    val showButtons = effectiveView(prefs.phoneView, state?.view).buttons()
    val favs by (server?.id?.let { repo.store.favoriteCards(it) } ?: kotlinx.coroutines.flow.flowOf(emptySet())).collectAsStateWithLifecycle(initialValue = emptySet())
    val info by repo.info.collectAsStateWithLifecycle()
    var week by remember { mutableStateOf(false) }
    var uptimeDay by remember { mutableStateOf<io.github.mithrandler.labhud.data.Uptime?>(null) }
    var uptimeWeek by remember { mutableStateOf<io.github.mithrandler.labhud.data.Uptime?>(null) }
    var ask by remember { mutableStateOf<Action?>(null) }
    var askMaint by remember { mutableStateOf(false) }
    var openBox by remember { mutableStateOf<io.github.mithrandler.labhud.data.Container?>(null) }
    var stackPick by rememberSaveable { mutableStateOf<String?>(null) }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // the series this card draws: only they are asked for, not the whole lab's history
    val keys = live?.let { c -> c.metrics.filter { it.trend }.map { it.key } + c.charts.map { it.key } }.orEmpty()
    // the panel and the charts: fetched when opened, then every 30 s while it stays open
    PollWhileStarted(id, keys, intervalMs = 30_000) {
        val api = repo.api() ?: return@PollWhileStarted
        runCatching { api.card(id) }.onSuccess { sections = it.sections; full = it }
        if (keys.isNotEmpty()) runCatching { api.history(false, keys) }.onSuccess { history = it }
    }
    // its uptime, with how much of the range labhud knows at all (coverage)
    LaunchedEffect(id, week, info?.uptime) {
        if (info?.uptime != true) return@LaunchedEffect
        repo.api()?.let { api -> runCatching { api.uptime(week) }.onSuccess { if (week) uptimeWeek = it else uptimeDay = it } }
    }
    LaunchedEffect(range, keys) {
        if (range in 1..2 && long == null) repo.api()?.let { api -> runCatching { api.history(true, keys) }.onSuccess { long = it } }
        if (range >= 3 && year == null) repo.api()?.let { api -> runCatching { api.historyYear(keys) }.onSuccess { year = it } }
    }

    var askedFirst by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(live != null, live?.containers?.size) {
        // a card's own button, or one of its containers' (the palette's "restart · name")
        val a = (live?.actions.orEmpty() + live?.containers.orEmpty().flatMap { it.actions }).firstOrNull { it.name == askFirst } ?: return@LaunchedEffect
        if (askedFirst) return@LaunchedEffect
        askedFirst = true
        gate.press { ask = a }
    }

    fun send(name: String, label: String) = scope.launch { snack.showSnackbar(sendAction(repo, name, label)) }

    Scaffold(
        containerColor = Lab.bg,
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Lab.bg),
                navigationIcon = { IconButton(onClick = onBack) { Icon(LabIcons.back, "Back") } },
                title = { Text(live?.name ?: "", fontWeight = FontWeight.SemiBold) },
                actions = {
                    val sid = server?.id
                    if (sid != null && live != null) IconButton(onClick = { scope.launch { repo.store.toggleFavoriteCard(sid, id) } }) {
                        Text(if (id in favs) "★" else "☆", fontSize = 22.sp, color = if (id in favs) Lab.title else Lab.muted)
                    }
                },
            )
        },
    ) { pad ->
        val c = live ?: run {
            Text("This card is not in the current config.", color = Lab.muted, modifier = Modifier.padding(pad).padding(24.dp))
            return@Scaffold
        }
        val up = if (week) uptimeWeek else uptimeDay
        val buttons = showButtons && rights.admin && (c.actions.isNotEmpty() || c.maintenanceButton)
        val all = state?.cards.orEmpty()
        // a host's VMs and LXCs, and a guest's host: the card's node links them
        val guests = if (c.kind == "guest" || c.node.isEmpty()) emptyList() else all.filter { it.kind == "guest" && it.node == c.node }.sortedBy { it.name.lowercase() }
        val host = if (c.kind != "guest" || c.node.isEmpty()) null else all.firstOrNull { it.kind != "guest" && it.node == c.node }
        val labels = c.metrics.filter { it.trend }.map { it.label }.toSet()
        val trends = c.metrics.filter { it.trend } + c.charts.filter { it.label !in labels }.map {
            io.github.mithrandler.labhud.data.Metric(key = it.key, label = it.label, value = "", trend = true, format = it.format)
        }
        val boxes = full?.containers.orEmpty()
        val stacks = if (boxes.isEmpty()) emptyList() else boxes.groupBy { it.project }.toSortedMap(compareBy { it.ifEmpty { "￿" } }).entries.toList()
        val marks = full?.bookmarks.orEmpty()
        val ups = full?.updates.orEmpty()
        val cols = gridColumns()
        // small facts of a machine fold into its Overview; every other section is a tab of its own
        val smallTitles = setOf("host", "resources")
        val secs = sections.filterNot { it.title.equals("numbers", true) && c.metrics.isNotEmpty() }
            .filterNot { it.title.equals("guests", true) && guests.isNotEmpty() }
            .filterNot { it.title.startsWith("docker containers", true) && stacks.isNotEmpty() }
        val tabs = buildList<CTab> {
            add(CTab("Overview", narrow = true) {
                item { StatusHeader(c, server) }
                val own = c.metrics.filter { it.value.isNotEmpty() && it.value != "—" }.ifEmpty { c.guest?.meters().orEmpty() }
                val (rings, others) = ringsAndChips(own)
                // the same rule and the same gauges as the card on Status
                val gauges = gaugesOf(c, own)
                if (gauges.size >= 2 || c.type == "proxmox" && gauges.isNotEmpty()) item {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        MachineGauges(gauges)
                        val facts = others.filter { c.guestCounts.isEmpty() || it.label.uppercase() !in c.guestCounts.keys.map { k -> k.uppercase() } }
                            .filter { c.gpus.isEmpty() || !(it.format == "celsius" && "gpu" in (it.label + it.key).lowercase()) } +
                            listOfNotNull(c.guest?.uptime?.let { Metric(label = "up", value = upText(it)) }) +
                            c.guestCounts.entries.sortedBy { it.key }.mapNotNull { (k, v) -> if (v.size == 2 && v[1] > 0) Metric(label = k.uppercase(), value = "${v[0]}/${v[1]}") else null }
                        if (facts.isNotEmpty()) FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                            verticalArrangement = Arrangement.spacedBy(6.dp)) { facts.forEach { Chip(it) } }
                    }
                } else if (c.metrics.isNotEmpty()) item {
                    Panel("") {
                        c.metrics.forEach { m ->
                            if (m.pct != null) MeterRow(m, Modifier.padding(vertical = 2.dp))
                            else Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Text(m.label, color = Lab.sub, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                Text(m.value, color = sevColor(m.sev), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
                // the rings already say CPU and RAM
                secs.filter { it.title.lowercase() in smallTitles }.forEach { s ->
                    val rows = s.rows.filterNot { r -> rings.isNotEmpty() && r.name.uppercase() in setOf("CPU", "RAM") }
                    if (rows.isNotEmpty()) item { Panel("") { rows.forEach { ListRow(it, server) } } }
                }
                if ((c.actions.isNotEmpty() || c.maintenanceButton) && !rights.admin) item {
                    Surface(onClick = onSignIn, shape = CardShape, color = Lab.block, border = BorderStroke(1.dp, Lab.line)) {
                        Text("This phone only looks. Sign in as the admin for the buttons.", fontSize = 13.sp, color = Lab.sub,
                            modifier = Modifier.fillMaxWidth().padding(12.dp))
                    }
                }
                val hasLinks = c.url.isNotEmpty() || c.links.isNotEmpty()
                if (buttons || hasLinks || host != null || c.resources().isNotEmpty()) item {
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (host != null) OutlinedButton(onClick = { onCard(host.id) }) { Text("Host · ${host.name}") }
                        if (c.resources().isNotEmpty()) OutlinedButton(onClick = { onHistory(c.id) }) { Text("Resources over time") }
                        if (showButtons && rights.admin) c.actions.forEach { a -> ActionButton(a) { gate.press { ask = a } } }
                        if (showButtons && rights.admin && c.maintenanceButton) {
                            OutlinedButton(onClick = { gate.press { askMaint = true } }) { Text(if (c.maintenanceUntil != null) "Maintenance ✓" else "Maintenance") }
                        }
                        (listOfNotNull(c.url.takeIf { it.isNotEmpty() }?.let { "Web UI" to it }) + c.links.map { it.label to it.url }).forEach { (label, url) ->
                            OutlinedButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }) {
                                Text(label)
                                Spacer(Modifier.width(6.dp))
                                Icon(LabIcons.open, null, Modifier.padding(0.dp))
                            }
                        }
                    }
                }
                if (up?.cards?.get(c.id) != null) item {
                    Panel("Uptime") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(uptimeText(up.percent[c.id], up.coverage[c.id]).ifEmpty { "—" }, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f))
                            listOf(false to "24 h", true to "7 d").forEach { (w, label) ->
                                TextButton(onClick = { week = w }) { Text(label, color = if (week == w) Lab.title else Lab.muted) }
                            }
                        }
                        if (week) UptimeDays(up.cards.getValue(c.id), Modifier.padding(top = 4.dp))
                        else UptimeBar(up.cards.getValue(c.id), Modifier.fillMaxWidth().padding(top = 4.dp).height(10.dp), slices = 48)
                        if ((up.coverage[c.id] ?: 100.0) < 99.95) Text("Grey: LabHUD itself was off then, so it does not know.",
                            fontSize = 12.sp, color = Lab.muted, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            })
            if (guests.isNotEmpty()) add(CTab("VM/LXC") {
                item(key = "guests") { PagedGrid(guests, cols, rows = 2) { g -> MiniCard(g, server, Modifier.weight(1f)) { onCard(g.id) } } }
            })
            if (stacks.isNotEmpty()) add(CTab("Containers") {
                // one stack at a time (chips when there are several), its containers a few to a page
                val picked = stacks.firstOrNull { it.key == stackPick } ?: stacks.first()
                val (project, members) = picked
                if (stacks.size > 1) item(key = "stacks") {
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        stacks.forEach { (name, list) ->
                            androidx.compose.material3.FilterChip(selected = name == project, onClick = { stackPick = name },
                                label = { Text((name.ifEmpty { "other" }) + " ${list.count { it.up }}/${list.size}") },
                                colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Lab.accent.copy(alpha = 0.2f), selectedLabelColor = Lab.title))
                        }
                    }
                }
                if (project.isNotEmpty()) item(key = "head") {
                    val restarts = members.filter { it.up }.map { m -> m.actions.firstOrNull { it.name.endsWith("-restart") } }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(project.uppercase(), style = Heading, modifier = Modifier.weight(1f))
                        Text("${members.count { it.up }}/${members.size} running", fontSize = 12.sp, color = Lab.muted)
                        if (rights.admin && restarts.isNotEmpty() && restarts.all { it != null }) {
                            TextButton(onClick = { gate.press { askStack = project to restarts.filterNotNull() } }) {
                                Text("Restart stack", fontSize = 12.sp, color = Lab.accent)
                            }
                        }
                    }
                }
                item(key = "boxes-$project") { PagedGrid(members, cols, rows = 3) { b -> ContainerCell(b, server, Modifier.weight(1f)) { openBox = b } } }
            })
            secs.filterNot { it.title.lowercase() in smallTitles }.forEach { s ->
                val name = s.title.replaceFirstChar { it.uppercase() }.let { if (it == "History") "Events" else it }
                add(CTab(name) {
                    if (s.title.lowercase() in setOf("storage", "backup", "datastores")) item(key = "sec-${s.title}") { PagedGrid(s.rows, cols, rows = 3) { r -> RowCell(r, Modifier.weight(1f)) } }
                    else item(key = "sec-${s.title}") { Panel("") { PagedList(s.rows, per = 6) { ListRow(it, server) } } }
                })
            }
            if (sections.isEmpty() && c.rows.isNotEmpty()) add(CTab("Details") { item { Panel("") { PagedList(c.rows, per = 6) { ListRow(it, server) } } } })
            if (full?.updates != null) add(CTab("Updates") {
                item {
                    Panel("") {
                        val ready = ups.filter { !it.updating }.mapNotNull { it.action }
                        if (rights.admin && ready.size > 1) {
                            OutlinedButton(onClick = { gate.press { askAll = ready } }) { Text("Update all ${ready.size}") }
                        }
                        if (ups.isEmpty()) Text("All containers up to date", fontSize = 13.sp, color = Lab.muted)
                        PagedList(ups, per = 4) { u ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(u.name, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    Text("${u.host} · ${u.image}", fontSize = 12.sp, color = Lab.muted, maxLines = 1)
                                    u.scan?.let { sc -> Text(sc.text(), fontSize = 12.sp, color = scanColor(sc)) }
                                }
                                when {
                                    u.updating -> Text("updating…", fontSize = 12.sp, color = Lab.accent)
                                    rights.admin && u.action != null -> TextButton(onClick = { gate.press { ask = u.action } }) { Text("Update", fontSize = 12.sp) }
                                }
                            }
                        }
                    }
                }
            })
            full?.notes?.let { text ->
                add(CTab("Notes") {
                    item {
                        Panel("") {
                            if (rights.admin) {
                                val now = notesText ?: text
                                androidx.compose.material3.OutlinedTextField(value = now, onValueChange = { notesText = it.take(4000) },
                                    modifier = Modifier.fillMaxWidth(), minLines = 4)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    TextButton(enabled = notesText != null && notesText != text, onClick = {
                                        gate.press {
                                            scope.launch {
                                                val r = runCatching { repo.api()?.saveNotes(notesText.orEmpty()) }.getOrNull()
                                                snack.showSnackbar(if (r?.ok == true) "Notes saved" else "Notes: ${r?.error ?: "refused"}")
                                                if (r?.ok == true) runCatching { repo.api()?.card(id) }.getOrNull()?.let { full = it; notesText = null }
                                            }
                                        }
                                    }) { Text("Save") }
                                }
                            } else {
                                Text(text.ifBlank { "—" }, fontSize = 15.sp, lineHeight = 20.sp)
                            }
                        }
                    }
                })
            }
            if (marks.isNotEmpty()) add(CTab("Links") {
                item {
                    Panel("") {
                        marks.groupBy { it.group }.forEach { (group, links) ->
                            if (marks.map { it.group }.toSet().size > 1) Text(group.uppercase(), style = Heading, modifier = Modifier.padding(top = 6.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                links.forEach { b ->
                                    OutlinedButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(b.url))) } }) { Text(b.name) }
                                }
                            }
                        }
                    }
                }
            })
            if (trends.isNotEmpty()) add(CTab("History") {
                item {
                    Panel("") {
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                            RANGES.forEachIndexed { i, (label, _) ->
                                SegmentedButton(selected = range == i, onClick = { range = i },
                                    shape = SegmentedButtonDefaults.itemShape(i, RANGES.size)) { Text(label) }
                            }
                        }
                        val h = when { range == 0 -> history; range <= 2 -> long; else -> year }
                        trends.forEach { m ->
                            val series = h?.series?.get(m.key).orEmpty()
                            val keep = if (h == null || h.step == 0) series.size else (RANGES[range].second / h.step).coerceAtMost(series.size)
                            Text(if (m.value.isEmpty()) m.label else "${m.label}  ${m.value}", fontSize = 13.sp, color = Lab.sub, modifier = Modifier.padding(top = 6.dp))
                            if (series.size >= 2) LineChart(series.takeLast(keep), color = Lab.accent)
                            else Text("No history yet", fontSize = 12.sp, color = Lab.muted)
                        }
                    }
                }
            })
        }
        val pager = rememberPagerState { tabs.size }
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (tabs.size > 1) androidx.compose.material3.ScrollableTabRow(
                selectedTabIndex = pager.currentPage.coerceAtMost(tabs.size - 1), containerColor = Lab.bg, edgePadding = 8.dp,
            ) {
                tabs.forEachIndexed { i, t ->
                    androidx.compose.material3.Tab(selected = pager.currentPage == i, onClick = { scope.launch { pager.scrollToPage(i) } },
                        text = { Text(t.name, maxLines = 1, color = if (pager.currentPage == i) Lab.title else Lab.muted) })
                }
            }
            HorizontalPager(pager, Modifier.fillMaxSize(), key = { tabs.getOrNull(it)?.name ?: it }) { i ->
                // the Overview is a column of facts: on a wide screen it stays 640 dp wide in the middle
                val wide = LocalConfiguration.current.screenWidthDp
                val side = if (tabs.getOrNull(i)?.narrow == true && wide > 672) ((wide - 640) / 2).dp else 16.dp
                LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = side, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) { tabs.getOrNull(i)?.body?.invoke(this) }
            }
        }
    }

    openBox?.let { b0 ->
        // the container as the card now has it (an update or restart changes it while the sheet is open)
        val b = full?.containers.orEmpty().firstOrNull { it.name == b0.name } ?: b0
        AlertDialog(
            onDismissRequest = { openBox = null },
            confirmButton = { TextButton(onClick = { openBox = null }) { Text("Close") } },
            text = {
                ContainerRow(b, admin = rights.admin, server = server,
                    onAction = { a -> openBox = null; gate.press { ask = a } },
                    onLogs = {
                        openBox = null
                        gate.press {
                            scope.launch {
                                val r = runCatching { repo.api()?.logs(full?.agent.orEmpty(), b.name) }.getOrNull()
                                logs = b.name to (if (r?.ok == true) r.output.ifBlank { "(nothing)" } else "✕ " + (r?.error?.ifBlank { null } ?: "refused"))
                            }
                        }
                    },
                    onLive = { openBox = null; gate.press { liveLog = full?.agent.orEmpty() to b.name } },
                    onDetails = {
                        openBox = null
                        gate.press {
                            scope.launch {
                                val r = runCatching { repo.api()?.inspect(full?.agent.orEmpty(), b.name) }.getOrNull()
                                details = b.name to (if (r?.ok == true) detailRows(r.output) else listOf("✕" to (r?.error?.ifBlank { null } ?: "refused")))
                            }
                        }
                    })
            },
        )
    }
    ask?.let { a -> ActionDialog(a, onSend = { name, label -> send(name, label) }, onDismiss = { ask = null }) }
    liveLog?.let { (agent, container) -> LiveLogDialog(agent, container, repo, gate) { liveLog = null } }
    GateDialogs(gate, onSignIn)
    askStack?.let { (project, acts) ->
        AlertDialog(
            onDismissRequest = { askStack = null },
            title = { Text("Restart $project?") },
            text = { Text("Its ${acts.size} running containers, each as its own RESTART would.") },
            confirmButton = { TextButton(onClick = { askStack = null; acts.forEach { send(it.name, it.label) } }) { Text("Restart") } },
            dismissButton = { TextButton(onClick = { askStack = null }) { Text("Cancel") } },
        )
    }
    askAll?.let { acts ->
        AlertDialog(
            onDismissRequest = { askAll = null },
            title = { Text("Update ${acts.size} containers?") },
            text = { Text("Each keeps its old container until the new one runs.") },
            confirmButton = { TextButton(onClick = { askAll = null; acts.forEach { send(it.name, it.label) } }) { Text("Update all") } },
            dismissButton = { TextButton(onClick = { askAll = null }) { Text("Cancel") } },
        )
    }
    details?.let { (name, rows) ->
        AlertDialog(
            onDismissRequest = { details = null },
            title = { Text(name) },
            text = {
                Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                    rows.forEach { (k, v) ->
                        Text(k, fontSize = 12.sp, color = Lab.muted, modifier = Modifier.padding(top = 6.dp))
                        Text(v, fontSize = 13.sp)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { details = null }) { Text("Close") } },
        )
    }
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

/** One tab of a card's screen: its name and the items of its list. */
private class CTab(val name: String, val narrow: Boolean = false, val body: LazyListScope.() -> Unit)

/** A container as a small card: dot, icon, its whole name, then what it is doing and what it uses. A tap opens its buttons. */
@Composable
private fun ContainerCell(b: io.github.mithrandler.labhud.data.Container, server: io.github.mithrandler.labhud.data.Server?, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier, shape = CardShape, color = Lab.card,
        border = if (b.up) BorderStroke(1.dp, Lab.line) else BorderStroke(1.dp, Lab.red.copy(alpha = 0.7f))) {
        Column(Modifier.heightIn(min = 84.dp).padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.layout.Box(Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(if (b.up) Lab.green else Lab.red))
                Spacer(Modifier.width(6.dp))
                NamedIcon(b.icon, LabIcons.container, server, 16.dp)
                Spacer(Modifier.width(6.dp))
                FitText(b.name, 12.sp, FontWeight.SemiBold, Modifier.weight(1f))
            }
            Text(when { b.updating -> "updating…"; b.update -> "update available"; else -> b.status }, fontSize = 11.sp, maxLines = 1,
                color = if (b.update || b.updating) Lab.accent else if (b.up) Lab.muted else Lab.red, modifier = Modifier.padding(top = 4.dp))
            if (b.usage.isNotEmpty()) Text(b.usage, fontSize = 11.sp, color = Lab.muted, maxLines = 1)
        }
    }
}

/** A section's row (name, value) as a small card: a storage and how full it is. */
@Composable
private fun RowCell(r: io.github.mithrandler.labhud.data.Row, modifier: Modifier) {
    Surface(modifier = modifier, shape = CardShape, color = Lab.card, border = BorderStroke(1.dp, Lab.line)) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            FitText(r.name, 12.sp, FontWeight.SemiBold)
            Text(r.value, fontSize = 11.sp, color = if (r.bad) Lab.red else Lab.muted, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** The agent's `inspect` (JSON) as label/value pairs; the environment by name only, as it comes. */
private fun detailRows(json: String): List<Pair<String, String>> = runCatching {
    val o = io.github.mithrandler.labhud.data.LabJson.parseToJsonElement(json) as kotlinx.serialization.json.JsonObject
    fun s(k: String) = (o[k] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
    fun l(k: String) = (o[k] as? kotlinx.serialization.json.JsonArray)?.joinToString(", ") { (it as kotlinx.serialization.json.JsonPrimitive).content }.orEmpty()
    val out = mutableListOf("image" to s("image"), "restart" to s("restart") + (s("restarts").takeIf { it != "0" && it.isNotEmpty() }?.let { " · $it restarts" } ?: ""),
        "ports" to l("ports").ifEmpty { "none" }, "networks" to l("networks"), "volumes" to l("mounts").replace(", ", "\n"),
        "environment" to (l("env").ifEmpty { "—" } + " (values not shown)"))
    (o["labels"] as? kotlinx.serialization.json.JsonObject)?.forEach { (k, v) -> out += k to (v as kotlinx.serialization.json.JsonPrimitive).content }
    out.filter { it.second.isNotEmpty() }
}.getOrElse { listOf("✕" to "an older agent: update it") }

/** Red with a critical CVE, amber with a high one, else muted. */
private fun scanColor(s: io.github.mithrandler.labhud.data.Scan) = when { s.critical > 0 -> Lab.red; s.high > 0 -> Lab.warn; else -> Lab.muted }

/** A container: its dot, name and state, "update available", its usage, latest news and CVEs, its buttons and (admin) LOGS, DETAILS. */
@Composable
private fun ContainerRow(b: io.github.mithrandler.labhud.data.Container, admin: Boolean, server: io.github.mithrandler.labhud.data.Server?, onAction: (Action) -> Unit, onLogs: () -> Unit,
                         onLive: () -> Unit = {}, onDetails: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Box(Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape)
                .background(if (b.up) Lab.green else Lab.red))
            Spacer(Modifier.width(10.dp))
            NamedIcon(b.icon, LabIcons.container, server, 18.dp)
            Spacer(Modifier.width(8.dp))
            Text(b.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(when { b.updating -> "updating…"; b.update -> "update available"; else -> b.status }, fontSize = 12.sp,
                color = if (b.update || b.updating) Lab.accent else if (b.up) Lab.muted else Lab.red, maxLines = 1)
        }
        listOf(b.usage, b.last).filter { it.isNotEmpty() }.forEach {
            Text(it, fontSize = 12.sp, color = Lab.muted, modifier = Modifier.padding(start = 18.dp))
        }
        b.scan?.let { sc -> Text(sc.text(), fontSize = 12.sp, color = scanColor(sc), modifier = Modifier.padding(start = 18.dp)) }
        if (admin) Row(Modifier.padding(start = 14.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            b.actions.forEach { a ->
                TextButton(onClick = { onAction(a) }, enabled = !b.updating) {
                    Text(niceLabel(a), fontSize = 12.sp, color = if (a.danger) Lab.red else Lab.accent)
                }
            }
            TextButton(onClick = onDetails) { Text("Details", fontSize = 12.sp) }
            TextButton(onClick = onLogs) { Text("Logs", fontSize = 12.sp) }
            if (b.up) TextButton(onClick = onLive) { Text("Live", fontSize = 12.sp) }
        }
    }
}

@Composable
private fun StatusHeader(c: Card, server: io.github.mithrandler.labhud.data.Server?) {
    Surface(shape = CardShape, color = Lab.card, border = BorderStroke(1.dp, if (c.bad == true) Lab.red.copy(alpha = 0.7f) else Lab.line)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CardIcon(c, server, 28.dp)
                Spacer(Modifier.width(12.dp))
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

/**
 * A card with a title and its content. With a [summary] it is folded to one line (title and summary)
 * and opens on a tap, so a section with many panels stays short; without one it is always open.
 */
@Composable
fun Panel(title: String, summary: String? = null, content: @Composable () -> Unit) {
    var open by rememberSaveable(title) { mutableStateOf(summary == null) }
    Surface(shape = CardShape, color = Lab.card, border = BorderStroke(1.dp, Lab.line)) {
        Column(Modifier.fillMaxWidth()) {
            if (summary != null) Row(
                Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = Lab.text)
                    if (summary.isNotEmpty()) Text(summary, fontSize = 12.sp, color = Lab.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(if (open) LabIcons.chevronDown else LabIcons.chevronRight, null, tint = Lab.muted, modifier = Modifier.size(20.dp))
            }
            if (open) Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 14.dp, top = if (summary == null) 14.dp else 0.dp)) {
                if (summary == null && title.isNotEmpty()) Text(title.uppercase(), style = Heading, modifier = Modifier.padding(bottom = 6.dp))
                content()
            }
        }
    }
}
