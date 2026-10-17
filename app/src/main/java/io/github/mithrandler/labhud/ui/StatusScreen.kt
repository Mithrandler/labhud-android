package io.github.mithrandler.labhud.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.alpha
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
import io.github.mithrandler.labhud.data.GameServer
import io.github.mithrandler.labhud.data.LabRepository
import io.github.mithrandler.labhud.data.LabState
import io.github.mithrandler.labhud.data.Page
import io.github.mithrandler.labhud.data.Server
import io.github.mithrandler.labhud.data.Uptime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * Status: the wall's pages as chips, then each group as a grid of small cards (two to a row on a
 * phone, more on a tablet), each with its dot, name, two numbers and the last 24 hours of uptime.
 * The bell in the top bar holds the alerts and the problems.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusScreen(
    repo: LabRepository,
    onCard: (String) -> Unit,
    onGame: (String) -> Unit,
    onServers: () -> Unit,
    onWall: () -> Unit,
    onLock: (() -> Unit)?,
) {
    val state by repo.state.collectAsState()
    val conn by repo.conn.collectAsState()
    val server by repo.server.collectAsState()
    val info by repo.info.collectAsState()
    val liveAt by repo.liveAt.collectAsState()
    val servers by repo.store.servers.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var bell by remember { mutableStateOf(false) }
    var pageId by rememberSaveable { mutableStateOf<String?>(null) }
    var uptime by remember { mutableStateOf<Uptime?>(null) }

    LaunchedEffect(server?.id, info?.uptime) {
        if (info?.uptime != true) return@LaunchedEffect
        while (true) {
            repo.api()?.let { api -> runCatching { api.uptime(false) }.onSuccess { uptime = it } }
            delay(5 * 60_000)
        }
    }

    val st = state
    val count = (st?.alerts?.size ?: 0) + (st?.problems?.size ?: 0)
    Scaffold(
        containerColor = Lab.bg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Lab.bg),
                title = { ServerPicker(server, servers, onPick = { scope.launch { repo.store.select(it.id) } }, onManage = onServers) },
                actions = {
                    if (st != null) LiveMark(st, conn)
                    IconButton(onClick = { bell = true }) {
                        BadgedBox(badge = { if (count > 0) Badge(containerColor = Lab.red) { Text("$count") } }) {
                            Icon(LabIcons.bell, "Alerts")
                        }
                    }
                    IconButton(onClick = { menu = true }) { Icon(LabIcons.more, "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Wall mode") }, leadingIcon = { Icon(LabIcons.wall, null) },
                            onClick = { menu = false; onWall() })
                        if (onLock != null) DropdownMenuItem(text = { Text("Lock now") }, leadingIcon = { Icon(LabIcons.lock, null) },
                            onClick = { menu = false; onLock() })
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            ConnBanner(conn, liveAt, onRetry = { repo.retry() })
            if (st == null) {
                Text(if (conn is Conn.Offline) "Nothing kept from this server yet: the numbers come once it answers." else "Connecting…",
                    color = Lab.muted, modifier = Modifier.padding(24.dp))
                return@Column
            }
            val byId = st.byId
            val problems = st.problems.mapNotNull { byId[it] }
            if (problems.isNotEmpty()) ProblemsBar(problems) { bell = true }
            val pages = st.pages
            val start = pages.indexOfFirst { it.id == pageId }.coerceAtLeast(0)
            val pager = rememberPagerState(initialPage = start) { pages.size }
            val chips = rememberLazyListState()
            // the chip row follows a swipe; the remembered page follows the pager
            LaunchedEffect(pager.currentPage, pages.size) {
                pages.getOrNull(pager.currentPage)?.let { pageId = it.id }
                if (pages.size > 1) chips.animateScrollToItem(pager.currentPage.coerceAtMost(pages.size - 1))
            }
            if (pages.size > 1) LazyRow(
                state = chips,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(pages, key = { _, p -> p.id }) { i, p ->
                    val bad = p.groups.any { g -> g.cards.any { byId[it]?.bad == true } }
                    FilterChip(
                        selected = i == pager.currentPage, onClick = { scope.launch { pager.animateScrollToPage(i) } },
                        label = { Text(p.title.lowercase().replaceFirstChar(Char::uppercase)) },
                        trailingIcon = if (bad) ({ Box(Modifier.size(6.dp).clip(CircleShape).background(Lab.red)) }) else null,
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Lab.accent.copy(alpha = 0.2f), selectedLabelColor = Lab.title),
                    )
                }
            }
            PullToRefreshBox(isRefreshing = conn == Conn.Connecting, onRefresh = { repo.retry() }, modifier = Modifier.fillMaxSize()) {
                // swipe left and right between the wall's pages, as on the wall itself
                // offline: the last numbers stay, dimmed, so they are not taken for live ones
                HorizontalPager(pager, Modifier.fillMaxSize().alpha(if (conn is Conn.Offline) 0.5f else 1f), key = { pages.getOrNull(it)?.id ?: it }) { index ->
                    val page = pages.getOrNull(index)
                    LazyVerticalGrid(
                        GridCells.Adaptive(158.dp),
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (page == null) return@LazyVerticalGrid
                        if (page.games) games(page, onGame) else groups(page, byId, uptime, onCard)
                    }
                }
            }
        }
    }
    if (bell && st != null) BellSheet(st, onDismiss = { bell = false }, onCard = { bell = false; onCard(it) })
}

private fun LazyGridScope.groups(page: Page, byId: Map<String, Card>, uptime: Uptime?, onCard: (String) -> Unit) {
    page.groups.forEach { g ->
        val cards = g.cards.mapNotNull { byId[it] }
        if (cards.isEmpty()) return@forEach
        item(key = "g-${g.id}", span = { GridItemSpan(maxLineSpan) }) {
            Text(g.title.uppercase(), style = Heading, modifier = Modifier.padding(top = 8.dp))
        }
        cards.forEach { c ->
            if (c.kind == "list") item(key = "c-${g.id}-${c.id}", span = { GridItemSpan(maxLineSpan) }) { CardTile(c) { onCard(c.id) } }
            else item(key = "c-${g.id}-${c.id}") { SmallTile(c, uptime?.cards?.get(c.id)) { onCard(c.id) } }
        }
    }
}

private fun LazyGridScope.games(page: Page, onGame: (String) -> Unit) {
    if (page.servers.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Text("No game servers known yet.", color = Lab.muted) }
    items(page.servers, key = { "game-" + it.id.ifEmpty { it.name } }) { g -> GameTile(g) { if (g.id.isNotEmpty()) onGame(g.id) } }
}

/** A card in the grid: dot and name, two numbers (or its state in words), 24 hours of uptime. */
@Composable
fun SmallTile(c: Card, uptime: List<Double?>?, onClick: () -> Unit) {
    Surface(
        onClick = onClick, shape = CardShape, color = Lab.card,
        border = BorderStroke(1.dp, if (c.bad == true) Lab.red.copy(alpha = 0.7f) else Lab.line),
        modifier = Modifier.heightIn(min = 92.dp),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (c.status != null) { StatusDot(c, 9); Spacer(Modifier.width(8.dp)) }
                Text(c.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val fade = Modifier.alpha(if (c.frozen) 0.45f else 1f)
            val word = statusWord(c)
            val shown = c.metrics.filter { it.value.isNotEmpty() && it.value != "—" }.take(2)
            Column(fade.padding(top = 6.dp).heightIn(min = 34.dp)) {
                when {
                    word.isNotEmpty() && c.status != "up" -> Text(word, fontSize = 12.sp, color = if (c.status == "down") Lab.red else Lab.muted)
                    shown.isNotEmpty() -> shown.forEach { m ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(m.label, fontSize = 11.sp, color = Lab.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text(m.value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = sevColor(m.sev), maxLines = 1)
                        }
                    }
                    c.summary.isNotEmpty() -> Text(c.summary, fontSize = 12.sp, color = Lab.sub, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (!uptime.isNullOrEmpty()) UptimeBar(uptime, Modifier.fillMaxWidth().height(6.dp).padding(top = 2.dp), slices = 24)
        }
    }
}

@Composable
private fun GameTile(g: GameServer, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CardShape, color = Lab.card, border = BorderStroke(1.dp, Lab.line), modifier = Modifier.heightIn(min = 92.dp)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(if (g.running) Lab.green else Lab.border))
                Spacer(Modifier.width(8.dp))
                Text(g.name.ifEmpty { g.title }, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(g.title, fontSize = 12.sp, color = Lab.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
            Text(g.line, fontSize = 13.sp, color = if (g.running) Lab.text else Lab.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Slices of time, oldest on the left: each the worst of the 15-minute points in it. */
@Composable
fun UptimeBar(values: List<Double?>, modifier: Modifier, slices: Int = 48) {
    val n = slices
    val per = ((values.size + n - 1) / n).coerceAtLeast(1)
    val parts = values.chunked(per).map { b ->
        val known = b.filterNotNull()
        when {
            known.isEmpty() -> null
            known.all { it < 0 } -> -1.0
            else -> known.filter { it >= 0 }.min()
        }
    }
    Canvas(modifier) {
        if (parts.isEmpty()) return@Canvas
        val gap = 1.5.dp.toPx()
        val w = (size.width - gap * (n - 1)) / n
        val offset = n - parts.size   // a young history fills from the right
        parts.forEachIndexed { i, v ->
            drawRoundRect(uptimeColor(v), Offset((offset + i) * (w + gap), 0f), Size(w, size.height), CornerRadius(1.5.dp.toPx()))
        }
    }
}

/** The bell: labhud's alerts (the rules that fired, the config's own), then the red cards. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BellSheet(st: LabState, onDismiss: () -> Unit, onCard: (String) -> Unit) {
    val time = DateFormat.getTimeInstance(DateFormat.SHORT)
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Lab.card) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
            Text("ALERTS", style = Heading, modifier = Modifier.padding(bottom = 6.dp))
            if (st.alerts.isEmpty()) Text("No alert now.", color = Lab.muted, fontSize = 14.sp)
            st.alerts.forEach { a ->
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = a.card != null) { a.card?.let(onCard) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(LabIcons.warning, null, tint = Lab.warn, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(a.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    a.since?.let { Text(time.format(Date(it * 1000)), fontSize = 12.sp, color = Lab.muted) }
                }
            }
            val problems = st.problems.mapNotNull { st.byId[it] }
            Text("PROBLEMS", style = Heading, color = if (problems.isEmpty()) Lab.title else Lab.red, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
            if (problems.isEmpty()) Text("Everything is fine.", color = Lab.muted, fontSize = 14.sp)
            problems.forEach { c ->
                Row(Modifier.fillMaxWidth().clickable { onCard(c.id) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(c)
                    Spacer(Modifier.width(10.dp))
                    Text(c.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Text(statusWord(c).ifEmpty { c.note.ifEmpty { "problem" } }, fontSize = 12.sp, color = Lab.red, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp))
                }
            }
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
        Text(when (conn) { Conn.Live -> "Live $time"; is Conn.Offline -> "Offline · $time"; else -> time },
            fontSize = 12.sp, color = if (conn is Conn.Offline) Lab.warn else Lab.sub)
    }
}

/** Only while something is wrong: one thin line naming it, which opens the bell. */
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
            Icon(LabIcons.chevronRight, null, tint = Lab.red, modifier = Modifier.size(18.dp))
        }
    }
}

/**
 * While LabHUD cannot be reached: why (in words), since when the numbers are not live, and when
 * the next try is, with a button to try now. It stays up through the retries instead of flashing.
 */
@Composable
fun ConnBanner(conn: Conn, liveAt: Long, onRetry: () -> Unit) {
    if (conn !is Conn.Offline) return
    var now by remember { mutableStateOf(android.os.SystemClock.elapsedRealtime()) }
    LaunchedEffect(conn) {
        while (true) { now = android.os.SystemClock.elapsedRealtime(); delay(1_000) }
    }
    val color = if (conn.fatal) Lab.red else Lab.warn
    Surface(shape = CardShape, color = color.copy(alpha = 0.12f), border = BorderStroke(1.dp, color.copy(alpha = 0.6f)),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (conn.fatal) LabIcons.warning else LabIcons.offline, null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(if (conn.fatal) "Not connected" else "Offline", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = color)
                Text(conn.message, fontSize = 13.sp, color = Lab.sub)
                val parts = listOfNotNull(
                    liveAt.takeIf { it > 0 }?.let { "Last data " + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it)) },
                    when {
                        conn.fatal -> null
                        conn.trying -> "trying again…"
                        conn.retryAt > now -> "next try in ${(conn.retryAt - now + 999) / 1000} s"
                        else -> null
                    },
                )
                if (parts.isNotEmpty()) Text(parts.joinToString(" · "), fontSize = 12.sp, color = Lab.muted, modifier = Modifier.padding(top = 2.dp))
            }
            if (!conn.fatal) TextButton(onClick = onRetry, enabled = !conn.trying) { Text("Retry now") }
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
            if (servers.size > 1) Icon(LabIcons.chevronDown, null, tint = Lab.title)
        }
        DropdownMenu(expanded = open && servers.size > 1, onDismissRequest = { open = false }) {
            servers.forEach { s ->
                DropdownMenuItem(text = { Text(s.name) }, onClick = { open = false; onPick(s) },
                    leadingIcon = { if (s.id == server?.id) Icon(LabIcons.check, null) })
            }
            DropdownMenuItem(text = { Text("Manage servers…") }, onClick = { open = false; onManage() })
        }
    }
}
