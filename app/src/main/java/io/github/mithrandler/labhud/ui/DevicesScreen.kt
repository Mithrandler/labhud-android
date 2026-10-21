package io.github.mithrandler.labhud.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.setValue
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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

private val TYPE_ORDER = listOf(
    "proxmox" to "Proxmox",
    "nas" to "NAS & backup",
    "docker" to "Docker hosts",
    "vm" to "VMs & LXC",
    "pc" to "PCs & other",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(repo: LabRepository, onCard: (String) -> Unit) {
    val state by repo.state.collectAsStateWithLifecycle()
    val conn by repo.conn.collectAsStateWithLifecycle()
    val server by repo.server.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(Filter.All) }

    val devices = remember(state) { state?.cards.orEmpty().filter { it.type.isNotEmpty() }.distinctBy { it.id } }
    val filtered = remember(devices, filter) { devices.filter { it.matches(filter) } }
    // sorted once per change of the state, not on every redraw: the red ones first, then by name
    val grouped = remember(filtered) {
        TYPE_ORDER.map { (type, title) ->
            Triple(type, title, filtered.filter { it.type == type }.sortedWith(compareBy({ it.bad != true }, { it.name.lowercase() })))
        }.filter { it.third.isNotEmpty() }
    }
    val counts = remember(devices) { Filter.entries.associateWith { f -> devices.count { it.matches(f) } } }

    Scaffold(
        containerColor = Lab.bg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Lab.bg),
                title = { Text("Devices") },
            )
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
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state == null) {
                    item { Text("Connecting…", color = Lab.muted) }
                } else if (filtered.isEmpty()) {
                    item { Text("Nothing here.", color = Lab.muted) }
                } else {
                    grouped.forEach { (type, title, group) ->
                        item(key = "h-$type") {
                            SectionTitle(title, bad = group.any { it.bad == true })
                        }
                        items(group, key = { "d-" + it.id }) { c ->
                            DeviceRow(c, server) { onCard(c.id) }
                        }
                    }
                }
            }
        }
    }
}

/** A machine: its icon, name and line, its state in words, and a bar for each of its percentages. */
@Composable
private fun DeviceRow(c: Card, server: Server?, onClick: () -> Unit) {
    val sev = c.severity()
    Surface(
        onClick = onClick,
        shape = CardShape,
        color = Lab.card,
        border = BorderStroke(1.dp, if (c.bad == true) Lab.red.copy(alpha = 0.7f) else Lab.line),
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            if (sev.isNotEmpty()) Box(Modifier.width(3.dp).fillMaxHeight().background(barColor(sev)))
            Column(Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CardIcon(c, server)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val sub = c.subtitle.ifEmpty { c.summary }
                        if (sub.isNotEmpty()) {
                            Text(sub, fontSize = 12.sp, color = Lab.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    val word = statusWord(c)
                    if (word.isNotEmpty()) {
                        Text(word, fontSize = 12.sp, color = if (c.status == "down") Lab.red else Lab.muted,
                            modifier = Modifier.padding(start = 8.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    StatusDot(c, 10)
                }
                val bars = c.metrics.filter { it.pct != null }.take(3)
                if (bars.isNotEmpty() && c.status == "up") Column(Modifier.padding(top = 6.dp).alpha(if (c.frozen) 0.45f else 1f)) {
                    bars.forEach { MeterRow(it) }
                }
            }
        }
    }
}
