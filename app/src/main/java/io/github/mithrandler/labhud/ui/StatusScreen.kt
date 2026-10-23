package io.github.mithrandler.labhud.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.sp
import io.github.mithrandler.labhud.data.AppSettings
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.mithrandler.labhud.data.rateText
import java.text.DateFormat
import java.util.Date


/** Built once: a DateFormat costs more to make than to use. Only ever called from the main thread. */
internal val SHORT_TIME: DateFormat = DateFormat.getTimeInstance(DateFormat.SHORT)

/**
 * Status: the wall's pages as chips, then each group under its title as a grid of [HudCard]s (two
 * cells to a row on a phone, more on a tablet; a card's `size` says how many it takes), each with
 * its icon, dot, bars, sparkline and the last 24 hours of uptime.
 * The bell in the top bar holds the alerts and the problems.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusScreen(
    repo: LabRepository,
    bar: BarNav,
    onGame: (String) -> Unit,
    onServers: () -> Unit,
) {
    val state by repo.state.collectAsStateWithLifecycle()
    val conn by repo.conn.collectAsStateWithLifecycle()
    val server by repo.server.collectAsStateWithLifecycle()
    val info by repo.info.collectAsStateWithLifecycle()
    val servers by repo.store.servers.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val onCard = bar.onCard
    var pageId by rememberSaveable { mutableStateOf<String?>(null) }
    // a page picked in the palette, on this screen or another
    val goPage by repo.pagePick.collectAsStateWithLifecycle()
    val prefs by repo.store.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val favs by (server?.id?.let { repo.store.favoriteCards(it) } ?: kotlinx.coroutines.flow.flowOf(emptySet())).collectAsStateWithLifecycle(initialValue = emptySet())
    val view = effectiveView(prefs.phoneView, state?.view)
    var uptime by remember { mutableStateOf<Uptime?>(null) }
    var sparks by remember { mutableStateOf<Map<String, List<Double?>>>(emptyMap()) }

    WhileStarted(server?.id, info?.uptime) {
        if (info?.uptime != true) return@WhileStarted
        while (true) {
            repo.api()?.let { api -> runCatching { api.uptime(false) }.onSuccess { uptime = it } }
            delay(5 * 60_000)
        }
    }
    // the cards' sparklines: the last half hour of every number with a trend, in one request a minute
    WhileStarted(server?.id, info?.trends?.isNotEmpty()) {
        if (info?.trends.isNullOrEmpty()) { sparks = emptyMap(); return@WhileStarted }
        while (true) {
            repo.api()?.let { api -> runCatching { api.sparks() }.onSuccess { sparks = it.series } }
            delay(60_000)
        }
    }

    val st = state
    Scaffold(
        containerColor = Lab.bg,
        topBar = {
            MainTopBar(repo, bar,
                title = { ServerPicker(server, servers, onPick = { scope.launch { repo.store.select(it.id) } }, onManage = onServers) })
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            ConnBanner(conn, repo.liveAt, onRetry = { repo.retry() })
            if (st == null) {
                Text(if (conn is Conn.Offline) "Nothing kept from this server yet: the numbers come once it answers." else "Connecting…",
                    color = Lab.muted, modifier = Modifier.padding(24.dp))
                return@Column
            }
            val byId = st.byId
            // 12/14 containers on a host whose agent has Docker
            val boxChips = remember(st.containers) {
                st.containers.groupBy { it.card }.mapValues { (_, l) -> "${l.count { it.up }}/${l.size} containers" }
            }
            // problems are in the bell (its badge counts them), not in a band over the cards
            val pages = st.pages
            val start = pages.indexOfFirst { it.id == pageId }.coerceAtLeast(0)
            val pager = rememberPagerState(initialPage = start) { pages.size }
            val chips = rememberLazyListState()
            // the chip row follows a swipe; the remembered page follows the pager
            LaunchedEffect(goPage) {
                val i = pages.indexOfFirst { it.id == goPage }
                if (i >= 0) pager.scrollToPage(i)
                repo.pagePick.value = null
            }
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
                        leadingIcon = if (p.icon.isNotEmpty()) ({ NamedIcon(p.icon, LabIcons.status, server, 16.dp) }) else null,
                        trailingIcon = if (bad) ({ Box(Modifier.size(6.dp).clip(CircleShape).background(Lab.red)) }) else null,
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Lab.accent.copy(alpha = 0.2f), selectedLabelColor = Lab.title),
                    )
                }
            }
            PullToRefreshBox(isRefreshing = conn == Conn.Connecting, onRefresh = { repo.retry() }, modifier = Modifier.fillMaxSize()) {
                // swipe left and right between the wall's pages, as on the wall itself
                // offline: the last numbers stay, dimmed, so they are not taken for live ones
                // a swipe is not the pager's own: the top half moves between the pages (General, Network...), the bottom half between
                // the screens of a page (the dots), so each hand has one place to go (see the gesture on the Column below)
                HorizontalPager(pager, Modifier.fillMaxSize().alpha(if (conn is Conn.Offline) 0.5f else 1f), userScrollEnabled = false,
                    key = { pages.getOrNull(it)?.id ?: it }) { index ->
                    val page = pages.getOrNull(index)
                    // the page's own columns and density, when the server sets them (⚙ > Dashboard)
                    val compact = page?.compact == true || view == ViewMode.Glance
                    val gap = if (compact) 6.dp else 10.dp
                    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
                        // equal cells: [cols] to a row (3 on a phone, up to 6 when wide), the same gap between them and to the edges
                        val gapDp = 12.dp
                        val tile = LocalTileH.current.value
                        val cols = (page?.columns?.takeIf { it > 0 } ?: (((maxWidth.value - 24f + 12f) / (104f + 12f)).toInt())).coerceIn(2, 6)
                        val slots = if (page == null) emptyList() else if (page.games) page.servers.map { Slot("game-" + it.id.ifEmpty { it.name }, game = it) } else
                            (if (index == 0) favs.mapNotNull { byId[it] }.sortedBy { it.name.lowercase() }.map { Slot("c-fav-${it.id}", it) } else emptyList()) +
                                page.groups.flatMap { g -> g.cards.mapNotNull { byId[it] }.map { Slot("c-${g.id}-${it.id}", it) } }
                        val barH = pageBarHeight().value
                        // the packing depends on what each cell takes, not on the numbers in it: a new number must not pack again
                        val layout = slots.map { Triple(it.key, it.span(cols), it.height(tile)) }
                        val screens = remember(layout, cols, maxHeight, barH) {
                            packGrid(slots, { it.span(cols) }, { it.height(tile) }, cols, 12f, maxHeight.value - 24f - barH)
                        }
                        val fresh = slots.associateBy { it.key }
                        var asked by rememberSaveable(page?.id) { mutableIntStateOf(0) }
                        val cur = asked.coerceIn(0, screens.size - 1)
                        Column(Modifier.fillMaxSize().pointerInput(index, screens.size, cur, pages.size) {
                            var top = true
                            var total = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { total = 0f; top = it.y < size.height / 2 },
                                onDragCancel = { total = 0f },
                                onDragEnd = {
                                    val step = swipeStep(total, size.width.toFloat())
                                    if (step != 0) {
                                        // a page with one screen has nothing below to swipe to: the swipe moves between pages there too
                                        if (top || screens.size == 1) scope.launch { pager.animateScrollToPage((pager.currentPage + step).coerceIn(0, pages.size - 1)) }
                                        else asked = (cur + step).coerceIn(0, screens.size - 1)
                                    }
                                },
                                onHorizontalDrag = { _, dx -> total += dx },
                            )
                        }) {
                            LazyVerticalGrid(
                                androidx.compose.foundation.lazy.grid.GridCells.Fixed(cols),
                                Modifier.weight(1f).fillMaxWidth(),
                                contentPadding = PaddingValues(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(gapDp),
                                verticalArrangement = Arrangement.spacedBy(gapDp),
                            ) {
                                if (page == null) return@LazyVerticalGrid
                                if (slots.isEmpty() && page.games) item(span = { GridItemSpan(maxLineSpan) }) { Text("No game servers known yet.", color = Lab.muted) }
                                screens[cur].forEach { packed ->
                                    val sl = fresh[packed.key] ?: packed   // the packing may be older than the numbers
                                    val c = sl.card
                                    if (c != null) item(key = sl.key, span = { GridItemSpan(sl.span(maxLineSpan)) }) {
                                        if (c.isBig()) HudCard(c, server, Modifier.height(sl.height(tile).dp), sparks = sparks, uptime = uptime?.cards?.get(c.id), percent = uptime?.percent?.get(c.id),
                                            coverage = uptime?.coverage?.get(c.id), compact = compact,
                                            chip = boxChips[c.id]) { onCard(c.id) }
                                        else MiniCard(c, server, chip = boxChips[c.id]) { onCard(c.id) }
                                    }
                                    val g = sl.game
                                    if (g != null) item(key = sl.key, span = { GridItemSpan(sl.span(maxLineSpan)) }) { GameTile(g, server) { if (g.id.isNotEmpty()) onGame(g.id) } }
                                }
                            }
                            PageBar(cur, screens.size, { asked = it.coerceIn(0, screens.size - 1) })
                        }
                    }
                }
            }
        }
    }
}

/** What a horizontal drag of [total] px on a screen [width] px wide asks for: 1 (next), -1 (previous) or 0 (too short to mean it). */
internal fun swipeStep(total: Float, width: Float): Int = when {
    total < -width * 0.15f -> 1
    total > width * 0.15f -> -1
    else -> 0
}

/** One thing on a Status screen: a card (or a game server) with a key for its grid cell. */
private data class Slot(val key: String, val card: Card? = null, val game: GameServer? = null) {
    /** Cells it takes in a line of [cols]: a card by its kind, a game server one cell on a wide screen, else half a line or more. */
    fun span(cols: Int): Int = card?.tileSpan(cols) ?: if (cols >= 4) 1 else cols

    /** How tall it is, in dp: a cell, or two cells and the gap between them. */
    fun height(tile: Float): Float = (card?.tileRows() ?: 1).let { tile * it + 12f * (it - 1) }
}

@Composable
private fun GameTile(g: GameServer, server: Server?, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CardShape, color = Lab.card, border = BorderStroke(1.dp, Lab.line), modifier = Modifier.fillMaxWidth().height(LocalTileH.current)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(if (g.running) Lab.green else Lab.border))
                Spacer(Modifier.width(8.dp))
                NamedIcon(g.icon, LabIcons.games, server, 18.dp)
                Spacer(Modifier.width(6.dp))
                Text(g.name.ifEmpty { g.title }, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(g.title, fontSize = 12.sp, color = Lab.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
            Text(g.line, fontSize = 13.sp, color = if (g.running) Lab.text else Lab.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The bell: labhud's alerts (the rules that fired, the config's own), then the red cards. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BellSheet(st: LabState, onDismiss: () -> Unit, onCard: (String) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Lab.card) {
        BellContent(st, onCard, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 32.dp))
    }
}

/** What the bell holds, also Activity's first tab: the alerts, then the cards in trouble. */
@Composable
fun BellContent(st: LabState, onCard: (String) -> Unit, modifier: Modifier = Modifier) {
    val time = SHORT_TIME
    run {
        Column(modifier.fillMaxWidth()) {
            Text("ALERTS", style = Heading, modifier = Modifier.padding(bottom = 6.dp))
            if (st.alerts.isEmpty()) Text("No alert now.", color = Lab.muted, fontSize = 14.sp)
            st.alerts.forEach { a ->
                Row(
                    // a card the server hides is not in the state: its alert is still shown, but there is nothing to open
                    Modifier.fillMaxWidth().clickable(enabled = a.card != null && a.card in st.byId) { a.card?.let(onCard) }.padding(vertical = 8.dp),
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

/**
 * The state in the top bar: a dot, and on a wide screen the time of the data ("Live" while the stream
 * is open). On a phone the dot alone: the words took the room of the title.
 */
@Composable
fun LiveMark(st: LabState, conn: Conn) {
    val color = when {
        conn != Conn.Live -> Lab.muted
        st.problems.isEmpty() -> Lab.green
        else -> Lab.red
    }
    val time = SHORT_TIME.format(Date(st.now * 1000))
    val text = when (conn) { Conn.Live -> "Live $time"; is Conn.Offline -> "Offline · $time"; else -> time }
    val wide = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 600
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp).semantics { contentDescription = text }) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        if (wide) {
            Spacer(Modifier.width(6.dp))
            Text(text, fontSize = 12.sp, color = if (conn is Conn.Offline) Lab.warn else Lab.sub)
        }
    }
}

/**
 * While LabHUD cannot be reached: why (in words), since when the numbers are not live, and when
 * the next try is, with a button to try now. It stays up through the retries instead of flashing.
 */
@Composable
fun ConnBanner(conn: Conn, liveAtFlow: kotlinx.coroutines.flow.StateFlow<Long>, onRetry: () -> Unit) {
    if (conn !is Conn.Offline) return
    // read here, not by the screen: the stream's every message would redraw the whole screen
    val liveAt by liveAtFlow.collectAsStateWithLifecycle()
    var now by remember { mutableStateOf(android.os.SystemClock.elapsedRealtime()) }
    PollWhileStarted(conn, intervalMs = 1_000) { now = android.os.SystemClock.elapsedRealtime() }
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
                    liveAt.takeIf { it > 0 }?.let { "Last data " + SHORT_TIME.format(Date(it)) },
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
fun ServerPicker(server: Server?, servers: List<Server>, onPick: (Server) -> Unit, onManage: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).clickable { open = true }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            // the app's own name, not the server's: the one a server's config cannot change
            Text("LabHUD", color = Lab.title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
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

/** The internet's download and upload in the top bar, green while something flows (as the wall's strip). */
@Composable
fun InternetMark(n: io.github.mithrandler.labhud.data.Internet) {
    val flowing = n.dn + n.up > 64 * 1024
    Text(
        "↓${rateText(n.dn)} ↑${rateText(n.up)}",
        color = if (flowing) Lab.green else Lab.muted,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier.padding(end = 2.dp).semantics { contentDescription = "Internet: download ${rateText(n.dn)}, upload ${rateText(n.up)}" },
    )
}
