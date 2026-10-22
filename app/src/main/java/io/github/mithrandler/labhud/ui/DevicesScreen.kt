package io.github.mithrandler.labhud.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mithrandler.labhud.data.Card
import io.github.mithrandler.labhud.data.LabRepository
import io.github.mithrandler.labhud.data.Server

/**
 * Devices: every machine on the wall (hosts and guests), by kind, with filters for what runs,
 * what is off and what is wrong.
 */
private enum class Filter(val label: String) {
    All("All"),
    Running("Running"),
    Stopped("Stopped"),
    Problems("Problems"),
}

private fun Card.matches(f: Filter): Boolean = when (f) {
    Filter.All -> true
    Filter.Running -> status == "up"
    Filter.Stopped -> status in listOf("down", "scheduled", "on-demand", "maintenance")
    Filter.Problems -> bad == true
}

/** The sections, each with the types it holds in order: the Proxmox hosts, then the VMs and LXC they run (indented). */
private val SECTIONS = listOf(
    "Proxmox" to listOf("proxmox", "vm"),
    "NAS & backup" to listOf("nas"),
    "Docker hosts" to listOf("docker"),
    "PCs & other" to listOf("pc"),
)

/** The red ones first, then by name. */
private val RED_FIRST = compareBy<Card>({ it.bad != true }, { it.name.lowercase() })

/** Each Proxmox host followed by the VMs and LXC that run on it (by their `node`); guests of no listed host come last. */
internal fun hostsWithGuests(cards: List<Card>): List<Card> {
    val hosts = cards.filter { it.type == "proxmox" }.sortedWith(RED_FIRST)
    val guests = cards.filter { it.type == "vm" }
    val nodes = hosts.map { it.node }.filter { it.isNotEmpty() }.toSet()
    return hosts.flatMap { h -> listOf(h) + if (h.node.isEmpty()) emptyList() else guests.filter { it.node == h.node }.sortedWith(RED_FIRST) } +
        guests.filter { it.node !in nodes }.sortedWith(RED_FIRST)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(repo: LabRepository, bar: BarNav) {
    val onCard = bar.onCard
    val state by repo.state.collectAsStateWithLifecycle()
    val conn by repo.conn.collectAsStateWithLifecycle()
    val server by repo.server.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(Filter.All) }
    var sectionPick by rememberSaveable { mutableStateOf("") }

    val devices = remember(state) { state?.cards.orEmpty().filter { it.type.isNotEmpty() }.distinctBy { it.id } }
    val filtered = remember(devices, filter) { devices.filter { it.matches(filter) } }
    // sorted once per change of the state, not on every redraw: the red ones first, then by name
    val grouped = remember(filtered) {
        SECTIONS.map { (title, types) ->
            // by type first (hosts before guests), the red ones first within a type
            Triple(title, title, if (types == listOf("proxmox", "vm")) hostsWithGuests(filtered) else types.flatMap { t ->
                filtered.filter { it.type == t }.sortedWith(compareBy({ it.bad != true }, { it.name.lowercase() }))
            })
        }.filter { it.third.isNotEmpty() }
    }
    val counts = remember(devices) { Filter.entries.associateWith { f -> devices.count { it.matches(f) } } }

    val barScroll = rememberLabBarScroll()
    Scaffold(
        modifier = Modifier.labBarScroll(barScroll),
        containerColor = Lab.bg,
        topBar = {
            MainTopBar(repo, bar, title = { Text("Devices") })
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            ConnBanner(conn, repo.liveAt, onRetry = { repo.retry() })
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Filter.entries.forEach { f ->
                    FilterChip(
                        selected = filter == f,
                        onClick = { filter = f },
                        label = { Text("${f.label} ${counts[f]}") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Lab.accent.copy(alpha = 0.2f),
                            selectedLabelColor = Lab.title,
                        ),
                    )
                }
            }
            if (state == null) Text("Connecting…", color = Lab.muted, modifier = Modifier.padding(16.dp))
            else if (filtered.isEmpty()) Text("Nothing here.", color = Lab.muted, modifier = Modifier.padding(16.dp))
            else {
                // one section at a time (chips when there are several), its machines a few to a page
                val picked = grouped.firstOrNull { it.first == sectionPick } ?: grouped.first()
                if (grouped.size > 1) Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    grouped.forEach { (type, title, group) ->
                        FilterChip(
                            selected = type == picked.first, onClick = { sectionPick = type },
                            label = { Text("$title ${group.size}") },
                            trailingIcon = if (group.any { it.bad == true }) ({ Box(Modifier.size(6.dp).clip(CircleShape).background(Lab.red)) }) else null,
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Lab.accent.copy(alpha = 0.2f), selectedLabelColor = Lab.title),
                        )
                    }
                }
                val cols = gridColumns()
                Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    key(picked.first) {
                        PagedGrid(picked.third, cols, rows = 4) { c -> DeviceCell(c, server, Modifier.weight(1f)) { onCard(c.id) } }
                    }
                }
            }
        }
    }
}

/** A machine as a small card: icon, its whole name, the dot, then CPU and RAM in a few words or its state. */
@Composable
private fun DeviceCell(c: Card, server: Server?, modifier: Modifier, onClick: () -> Unit) {
    val sev = c.severity()
    Surface(
        onClick = onClick, modifier = modifier, shape = CardShape, color = Lab.card,
        border = BorderStroke(1.dp, if (c.bad == true) Lab.red.copy(alpha = 0.7f) else if (sev.isNotEmpty()) barColor(sev) else Lab.line),
    ) {
        Column(Modifier.heightIn(min = 76.dp).padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CardIcon(c, server)
                Spacer(Modifier.width(8.dp))
                FitText(c.name, 13.sp, FontWeight.SemiBold, Modifier.weight(1f))
                Spacer(Modifier.width(6.dp))
                StatusDot(c, 9)
            }
            val bars = c.metrics.filter { it.pct != null }.ifEmpty { c.guest?.meters().orEmpty() }.take(3)
            val up = c.guest?.uptime?.let { "up " + upText(it) } ?: c.metrics.firstOrNull { it.short().equals("uptime", true) }?.let { "up " + it.value }
            val line = if (c.status == "up") (bars.map { it.short() + " " + Math.round(it.pct ?: 0.0) + "%" } + listOfNotNull(up)).joinToString(" · ") else ""
            val word = statusWord(c)
            val sub = line.ifEmpty { word.ifEmpty { c.subtitle.ifEmpty { c.summary } } }
            if (sub.isNotEmpty()) Text(sub, fontSize = 11.sp, color = if (c.status == "down") Lab.red else Lab.muted, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp).alpha(if (c.frozen) 0.45f else 1f))
        }
    }
}
