package io.github.mithrandler.labhud.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mithrandler.labhud.data.Card
import io.github.mithrandler.labhud.data.LabRepository

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(repo: LabRepository, onCard: (String) -> Unit) {
    val state by repo.state.collectAsState()
    val conn by repo.conn.collectAsState()
    val liveAt by repo.liveAt.collectAsState()
    var filter by rememberSaveable { mutableStateOf(Filter.All) }

    val devices = state?.cards.orEmpty().filter { it.type.isNotEmpty() }.distinctBy { it.id }
    val filtered = devices.filter { it.matches(filter) }

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
            ConnBanner(conn, liveAt, onRetry = { repo.retry() })
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Filter.entries.forEach { f ->
                    FilterChip(
                        selected = filter == f,
                        onClick = { filter = f },
                        label = { Text("${f.label} ${devices.count { it.matches(f) }}") },
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
                    val typeOrder = listOf(
                        "proxmox" to "Proxmox",
                        "nas" to "NAS & backup",
                        "docker" to "Docker hosts",
                        "vm" to "VMs & LXC",
                        "pc" to "PCs & other",
                    )
                    typeOrder.forEach { (type, title) ->
                        val group = filtered.filter { it.type == type }
                            .sortedWith(compareBy({ it.bad != true }, { it.name.lowercase() }))
                        if (group.isNotEmpty()) {
                            item(key = "h-$type") {
                                Text(title.uppercase(), style = Heading, modifier = Modifier.padding(top = 8.dp))
                            }
                            items(group, key = { "d-" + it.id }) { c ->
                                DeviceRow(c) { onCard(c.id) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(c: Card, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CardShape,
        color = Lab.card,
        border = BorderStroke(1.dp, if (c.bad == true) Lab.red.copy(alpha = 0.7f) else Lab.line),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusDot(c, 10)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(c.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = c.summary.ifEmpty { c.subtitle }
                if (sub.isNotEmpty()) {
                    Text(sub, fontSize = 12.sp, color = Lab.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            val word = statusWord(c)
            if (word.isNotEmpty()) {
                Text(word, fontSize = 12.sp, color = if (c.status == "down") Lab.red else Lab.muted,
                    modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
