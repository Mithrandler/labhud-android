package io.github.mithrandler.labhud.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mithrandler.labhud.data.Card
import io.github.mithrandler.labhud.data.Conn
import io.github.mithrandler.labhud.data.LabRepository
import io.github.mithrandler.labhud.data.LabState
import io.github.mithrandler.labhud.data.Server
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    repo: LabRepository,
    onCard: (String) -> Unit,
    onServers: () -> Unit,
    onWall: () -> Unit,
    onLock: () -> Unit,
) {
    val state by repo.state.collectAsState()
    val conn by repo.conn.collectAsState()
    val server by repo.server.collectAsState()
    val servers by repo.store.servers.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var page by rememberSaveable { mutableStateOf(0) }
    var menu by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Lab.bg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Lab.bg),
                title = {
                    ServerPicker(server, servers, onPick = { scope.launch { repo.store.select(it.id) } }, onManage = onServers)
                },
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Wall mode") }, leadingIcon = { Icon(Icons.Default.Fullscreen, null) },
                            onClick = { menu = false; onWall() })
                        DropdownMenuItem(text = { Text("Lock now") }, leadingIcon = { Icon(Icons.Default.Lock, null) },
                            onClick = { menu = false; onLock() })
                    }
                },
            )
        },
    ) { pad ->
        PullToRefreshBox(
            isRefreshing = conn == Conn.Connecting && state != null,
            onRefresh = { repo.retry() },
            modifier = Modifier.padding(pad).fillMaxSize(),
        ) {
            val st = state
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { ConnBanner(conn, onRetry = { repo.retry() }) }
                if (st == null) {
                    item { Text(if (conn is Conn.Offline) "" else "Connecting…", color = Lab.muted, modifier = Modifier.padding(24.dp)) }
                    return@LazyColumn
                }
                item { Hero(st, conn) }
                items(st.alerts, key = { "alert-" + it.id }) { a ->
                    Surface(shape = CardShape, color = Lab.warn.copy(alpha = 0.12f), border = BorderStroke(1.dp, Lab.warn.copy(alpha = 0.5f))) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, null, tint = Lab.warn, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(a.text, fontSize = 14.sp)
                        }
                    }
                }
                val byId = st.byId
                val problems = st.problems.mapNotNull { byId[it] }
                if (problems.isNotEmpty()) {
                    item { Text("PROBLEMS", style = Heading, color = Lab.red, modifier = Modifier.padding(top = 6.dp)) }
                    items(problems, key = { "p-" + it.id }) { c -> CardTile(c) { onCard(c.id) } }
                }
                val pages = st.pages.filter { !it.games }
                if (pages.isNotEmpty()) {
                    val current = page.coerceIn(0, pages.size - 1)
                    stickyHeader {
                        PrimaryScrollableTabRow(
                            selectedTabIndex = current, containerColor = Lab.bg, edgePadding = 0.dp,
                            divider = { HorizontalDivider(color = Lab.line) },
                        ) {
                            pages.forEachIndexed { i, p ->
                                val bad = p.groups.any { g -> g.cards.any { byId[it]?.bad == true } }
                                Tab(selected = i == current, onClick = { page = i }, text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(p.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        if (bad) {
                                            Spacer(Modifier.width(6.dp))
                                            Box(Modifier.size(6.dp).clip(CircleShape).background(Lab.red))
                                        }
                                    }
                                })
                            }
                        }
                    }
                    pages[current].groups.forEach { g ->
                        val cards = g.cards.mapNotNull { byId[it] }
                        if (cards.isEmpty()) return@forEach
                        item(key = "g-${g.id}") { Text(g.title.uppercase(), style = Heading, modifier = Modifier.padding(top = 8.dp)) }
                        val guests = cards.filter { it.kind == "guest" }
                        cards.filter { it.kind != "guest" }.forEach { c ->
                            item(key = "c-${g.id}-${c.id}") { CardTile(c) { onCard(c.id) } }
                        }
                        guests.chunked(2).forEachIndexed { i, pair ->
                            item(key = "gr-${g.id}-$i") { GuestRow(pair, onCard) }
                        }
                    }
                }
            }
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
private fun Hero(st: LabState, conn: Conn) {
    val n = st.problems.size
    val color = if (n == 0) Lab.green else Lab.red
    Surface(shape = CardShape, color = color.copy(alpha = 0.10f), border = BorderStroke(1.dp, color.copy(alpha = 0.45f))) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Icon(if (n == 0) Icons.Default.Check else Icons.Default.Warning, null, tint = color)
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(if (n == 0) "All good" else if (n == 1) "1 problem" else "$n problems",
                    fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(st.now * 1000))
                Text(if (conn == Conn.Live) "Live · $time" else "As of $time", fontSize = 13.sp, color = Lab.sub)
            }
        }
    }
}

@Composable
private fun ConnBanner(conn: Conn, onRetry: () -> Unit) {
    if (conn !is Conn.Offline) return
    Surface(shape = CardShape, color = Lab.block, border = BorderStroke(1.dp, Lab.border)) {
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
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clip(CardShape).padding(end = 4.dp)) {
            TextButton(onClick = { open = true }) {
                Text(server?.name ?: "LabHUD", color = Lab.title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                if (servers.size > 1) Icon(Icons.Default.ArrowDropDown, null, tint = Lab.title)
            }
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
