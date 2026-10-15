package io.github.mithrandler.labhud.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mithrandler.labhud.data.Card
import io.github.mithrandler.labhud.data.Conn
import io.github.mithrandler.labhud.data.Event
import io.github.mithrandler.labhud.data.History
import io.github.mithrandler.labhud.data.LabRepository
import io.github.mithrandler.labhud.data.LabState
import io.github.mithrandler.labhud.data.Server
import io.github.mithrandler.labhud.data.Uptime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * Home: the state in one line of the top bar, then pages side by side — Overview first (problems,
 * the hosts' resources, uptime, the latest events), then the wall's pages. Swipe or tap a tab.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repo: LabRepository,
    onCard: (String) -> Unit,
    onHost: (String) -> Unit,
    onToday: () -> Unit,
    onServers: () -> Unit,
    onWall: () -> Unit,
    onLock: (() -> Unit)?,
) {
    val state by repo.state.collectAsState()
    val conn by repo.conn.collectAsState()
    val server by repo.server.collectAsState()
    val info by repo.info.collectAsState()
    val servers by repo.store.servers.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf<History?>(null) }
    var events by remember { mutableStateOf<List<Event>>(emptyList()) }
    var week by rememberSaveable { mutableStateOf(false) }
    var uptime by remember { mutableStateOf<Uptime?>(null) }

    // the numbers behind the tiles and the latest events: once a minute while Home is open
    LaunchedEffect(server?.id) {
        while (true) {
            repo.api()?.let { api ->
                runCatching { api.history(false) }.onSuccess { history = it }
                runCatching { api.events().events }.onSuccess { events = it }
            }
            delay(60_000)
        }
    }
    LaunchedEffect(server?.id, week, info?.uptime) {
        if (info?.uptime != true) return@LaunchedEffect
        while (true) {
            repo.api()?.let { api -> runCatching { api.uptime(week) }.onSuccess { uptime = it } }
            delay(5 * 60_000)
        }
    }

    val st = state
    val pages = st?.pages.orEmpty().filter { !it.games }
    val pager = rememberPagerState { pages.size + 1 }

    Scaffold(
        containerColor = Lab.bg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Lab.bg),
                title = { ServerPicker(server, servers, onPick = { scope.launch { repo.store.select(it.id) } }, onManage = onServers) },
                actions = {
                    if (st != null) LiveMark(st, conn)
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Wall mode") }, leadingIcon = { Icon(Icons.Default.Fullscreen, null) },
                            onClick = { menu = false; onWall() })
                        if (onLock != null) DropdownMenuItem(text = { Text("Lock now") }, leadingIcon = { Icon(Icons.Default.Lock, null) },
                            onClick = { menu = false; onLock() })
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            ConnBanner(conn, onRetry = { repo.retry() })
            if (st == null) {
                Text(if (conn is Conn.Offline) "" else "Connecting…", color = Lab.muted, modifier = Modifier.padding(24.dp))
                return@Column
            }
            val byId = st.byId
            val problems = st.problems.mapNotNull { byId[it] }
            if (problems.isNotEmpty() && pager.currentPage != 0) ProblemsBar(problems) { scope.launch { pager.animateScrollToPage(0) } }
            PrimaryScrollableTabRow(
                selectedTabIndex = pager.currentPage.coerceAtMost(pages.size), containerColor = Lab.bg, edgePadding = 8.dp,
                divider = { HorizontalDivider(color = Lab.line) },
            ) {
                Tab(selected = pager.currentPage == 0, onClick = { scope.launch { pager.animateScrollToPage(0) } },
                    text = { TabLabel("OVERVIEW", problems.isNotEmpty()) })
                pages.forEachIndexed { i, p ->
                    val bad = p.groups.any { g -> g.cards.any { byId[it]?.bad == true } }
                    Tab(selected = pager.currentPage == i + 1, onClick = { scope.launch { pager.animateScrollToPage(i + 1) } },
                        text = { TabLabel(p.title, bad) })
                }
            }
            PullToRefreshBox(
                isRefreshing = conn == Conn.Connecting,
                onRefresh = { repo.retry() },
                modifier = Modifier.fillMaxSize(),
            ) {
                HorizontalPager(pager, Modifier.fillMaxSize(), key = { if (it == 0) "overview" else pages.getOrNull(it - 1)?.id ?: it }) { index ->
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (index == 0) overview(st, problems, history, uptime, week, { week = it }, events, onCard, onHost, onToday)
                        else pages.getOrNull(index - 1)?.let { page(it, byId, onCard) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabLabel(title: String, bad: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        if (bad) {
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(6.dp).clip(CircleShape).background(Lab.red))
        }
    }
}

private fun LazyListScope.page(p: io.github.mithrandler.labhud.data.Page, byId: Map<String, Card>, onCard: (String) -> Unit) {
    p.groups.forEach { g ->
        val cards = g.cards.mapNotNull { byId[it] }
        if (cards.isEmpty()) return@forEach
        item(key = "g-${g.id}") { Text(g.title.uppercase(), style = Heading, modifier = Modifier.padding(top = 6.dp)) }
        val guests = cards.filter { it.kind == "guest" }
        cards.filter { it.kind != "guest" }.forEach { c ->
            item(key = "c-${g.id}-${c.id}") { CardTile(c) { onCard(c.id) } }
        }
        guests.chunked(2).forEachIndexed { i, pair ->
            item(key = "gr-${g.id}-$i") { GuestRow(pair, onCard) }
        }
    }
}

private fun LazyListScope.overview(
    st: LabState, problems: List<Card>, history: History?, uptime: Uptime?, week: Boolean, setWeek: (Boolean) -> Unit,
    events: List<Event>, onCard: (String) -> Unit, onHost: (String) -> Unit, onToday: () -> Unit,
) {
    items(st.alerts, key = { "alert-" + it.id }) { a ->
        Surface(shape = CardShape, color = Lab.warn.copy(alpha = 0.12f), border = BorderStroke(1.dp, Lab.warn.copy(alpha = 0.5f))) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, null, tint = Lab.warn, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(a.text, fontSize = 14.sp)
            }
        }
    }
    if (problems.isNotEmpty()) {
        item { Text("PROBLEMS", style = Heading, color = Lab.red) }
        items(problems, key = { "p-" + it.id }) { c -> CardTile(c) { onCard(c.id) } }
    }

    // one tile per card that keeps resource history (hosts, the NAS, the router…), in page order
    val seen = HashSet<String>()
    val hosts = st.pages.flatMap { p -> p.groups.flatMap { it.cards } }.mapNotNull { st.byId[it] }
        .filter { c -> c.resources().any { m -> m.value != "—" || history?.series?.get(m.key)?.any { it != null } == true } && seen.add(c.id) }
    if (hosts.isNotEmpty()) {
        item { Text("RESOURCES", style = Heading, modifier = Modifier.padding(top = 6.dp)) }
        items(hosts, key = { "h-" + it.id }) { c -> HostTile(c, history) { onHost(c.id) } }
    }

    if (uptime != null && uptime.cards.isNotEmpty()) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("UPTIME", style = Heading, modifier = Modifier.weight(1f))
                listOf(false to "24h", true to "7d").forEach { (w, label) ->
                    Text(label, fontSize = 12.sp, fontWeight = if (week == w) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (week == w) Lab.title else Lab.muted,
                        modifier = Modifier.clip(CircleShape).clickable { setWeek(w) }.padding(horizontal = 10.dp, vertical = 4.dp))
                }
            }
        }
        item { UptimePanel(st, uptime, onCard) }
    }

    if (events.isNotEmpty()) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("RECENT", style = Heading, modifier = Modifier.weight(1f))
                TextButton(onClick = onToday) { Text("See all", fontSize = 12.sp) }
            }
        }
        item {
            val time = DateFormat.getTimeInstance(DateFormat.SHORT)
            Panel("") {
                events.take(5).forEach { e ->
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = e.card != null) { e.card?.let(onCard) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(if (e.bad) Lab.red else Lab.border))
                        Spacer(Modifier.width(10.dp))
                        Text(e.name, fontSize = 13.sp, color = if (e.bad) Lab.text else Lab.sub, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(10.dp))
                        Text(time.format(Date(e.t * 1000)), fontSize = 12.sp, color = Lab.muted)
                    }
                }
            }
        }
    }
}

/** A host's resources at a glance: per number, its name, its value and the last hours as a line. */
@Composable
private fun HostTile(c: Card, history: History?, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CardShape, color = Lab.card,
        border = BorderStroke(1.dp, if (c.bad == true) Lab.red.copy(alpha = 0.7f) else Lab.line)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (c.status != null) { StatusDot(c); Spacer(Modifier.width(10.dp)) }
                Text(c.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Lab.muted, modifier = Modifier.size(18.dp))
            }
            val metrics = c.resources().take(6)
            metrics.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { m ->
                        val series = history?.series?.get(m.key).orEmpty()
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(m.short(), fontSize = 11.sp, color = Lab.muted, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                                Spacer(Modifier.width(6.dp))
                                Text(m.show(series.lastOrNull { it != null } ?: m.value.filter { it.isDigit() || it == '.' }.toDoubleOrNull()),
                                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = sevColor(m.sev))
                            }
                            // its own scale, but never narrower than 10 points: 21-22% must not look like a storm
                            val known = series.filterNotNull()
                            val mid = if (known.isEmpty()) 0.0 else (known.min() + known.max()) / 2
                            val half = if (known.isEmpty()) 5.0 else maxOf(5.0, (known.max() - known.min()) / 2 * 1.2)
                            Sparkline(series, m.color(), Modifier.fillMaxWidth().height(26.dp).padding(top = 4.dp), lo = mid - half, hi = mid + half)
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** Status-page bars: the cards that were down first, then the rest; 8 shown, the others on a tap. */
@Composable
private fun UptimePanel(st: LabState, uptime: Uptime, onCard: (String) -> Unit) {
    var all by remember { mutableStateOf(false) }
    val rows = uptime.cards.keys.mapNotNull { st.byId[it] }
        .sortedWith(compareBy({ uptime.percent[it.id] ?: 101.0 }, { it.name }))
    Panel("") {
        (if (all) rows else rows.take(8)).forEach { c ->
            Row(
                Modifier.fillMaxWidth().clickable { onCard(c.id) }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(c.name, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(96.dp))
                UptimeBar(uptime.cards[c.id].orEmpty(), Modifier.weight(1f).height(18.dp).padding(horizontal = 8.dp))
                val pct = uptime.percent[c.id]
                Text(pct?.let { if (it >= 99.995) "100%" else String.format(Locale.ROOT, "%.2f%%", it) } ?: "—",
                    fontSize = 12.sp, color = when { pct == null -> Lab.muted; pct >= 99.5 -> Lab.sub; pct >= 97 -> Lab.warn; else -> Lab.red },
                    modifier = Modifier.width(52.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
            }
        }
        if (rows.size > 8) TextButton(onClick = { all = !all }) { Text(if (all) "Fewer" else "All ${rows.size}", fontSize = 12.sp) }
    }
}

/** Slices of time, oldest on the left: each the worst of the 15-minute points in it. */
@Composable
private fun UptimeBar(values: List<Double?>, modifier: Modifier) {
    val n = 48
    val per = ((values.size + n - 1) / n).coerceAtLeast(1)
    val slices = values.chunked(per).map { b ->
        val known = b.filterNotNull()
        when {
            known.isEmpty() -> null
            known.all { it < 0 } -> -1.0
            else -> known.filter { it >= 0 }.min()
        }
    }
    Canvas(modifier) {
        if (slices.isEmpty()) return@Canvas
        val gap = 1.5.dp.toPx()
        val w = (size.width - gap * (n - 1)) / n
        val offset = n - slices.size   // a young history fills from the right
        slices.forEachIndexed { i, v ->
            drawRoundRect(uptimeColor(v), Offset((offset + i) * (w + gap), 0f), Size(w, size.height), CornerRadius(1.5.dp.toPx()))
        }
    }
}

/** The state in the top bar: a dot and the time of the data, "Live" while the stream is open. */
@Composable
private fun LiveMark(st: LabState, conn: Conn) {
    val color = when {
        conn != Conn.Live -> Lab.muted
        st.problems.isEmpty() -> Lab.green
        else -> Lab.red
    }
    val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(st.now * 1000))
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 2.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(if (conn == Conn.Live) "Live $time" else time, fontSize = 12.sp, color = Lab.sub)
    }
}

/** Only while something is wrong: one thin line naming it, which leads to the list. */
@Composable
private fun ProblemsBar(problems: List<Card>, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Lab.red.copy(alpha = 0.14f), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(3.dp).height(16.dp).background(Lab.red))
            Spacer(Modifier.width(10.dp))
            Text(
                (if (problems.size == 1) "1 problem" else "${problems.size} problems") + " · " + problems.joinToString(", ") { it.name },
                fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Lab.red, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun GuestRow(pair: List<Card>, onCard: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        pair.forEach { c -> GuestTile(c, Modifier.weight(1f)) { onCard(c.id) } }
        if (pair.size == 1) Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun ConnBanner(conn: Conn, onRetry: () -> Unit) {
    if (conn !is Conn.Offline) return
    Surface(shape = CardShape, color = Lab.block, border = BorderStroke(1.dp, Lab.border), modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(conn.message, fontSize = 13.sp, color = Lab.sub, modifier = Modifier.weight(1f))
            if (!conn.fatal) TextButton(onClick = onRetry) { Text("Retry") }
        }
    }
}

@Composable
private fun ServerPicker(server: Server?, servers: List<Server>, onPick: (Server) -> Unit, onManage: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Text(server?.name ?: "LabHUD", color = Lab.title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (servers.size > 1) Icon(Icons.Default.ArrowDropDown, null, tint = Lab.title)
        }
        DropdownMenu(expanded = open && servers.size > 1, onDismissRequest = { open = false }) {
            servers.forEach { s ->
                DropdownMenuItem(text = { Text(s.name) }, onClick = { open = false; onPick(s) },
                    leadingIcon = { if (s.id == server?.id) Icon(Icons.Default.Check, null) })
            }
            DropdownMenuItem(text = { Text("Manage servers…") }, onClick = { open = false; onManage() })
        }
    }
}
