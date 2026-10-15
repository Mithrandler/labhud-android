package io.github.mithrandler.labhud.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mithrandler.labhud.data.Event
import io.github.mithrandler.labhud.data.LabRepository
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The timeline: every event labhud kept, newest first, under a heading per day. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(repo: LabRepository, onCard: (String) -> Unit) {
    var events by remember { mutableStateOf<List<Event>?>(null) }
    var error by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val api = repo.api()
        if (api == null) error = "No server paired." else
            runCatching { api.events().events }.onSuccess { events = it }.onFailure { error = it.message ?: "Could not reach LabHUD." }
    }
    val day = SimpleDateFormat("EEEE d MMMM", Locale.ENGLISH)
    val time = DateFormat.getTimeInstance(DateFormat.SHORT)
    Scaffold(
        containerColor = Lab.bg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Lab.bg),
                title = { Text("Timeline") },
            )
        },
    ) { pad ->
        val list = events
        if (list == null) {
            Text(error.ifEmpty { "Loading…" }, color = Lab.muted, modifier = Modifier.padding(pad).padding(24.dp))
            return@Scaffold
        }
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
            if (list.isEmpty()) item { Text("Nothing happened yet.", color = Lab.muted) }
            list.groupBy { day.format(Date(it.t * 1000)) }.forEach { (heading, rows) ->
                item(key = heading) {
                    val bad = rows.count { it.bad }
                    Text("${heading.uppercase()} · ${rows.size}" + if (bad > 0) " · $bad bad" else "", style = Heading,
                        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
                }
                items(rows) { e ->
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = e.card != null) { e.card?.let(onCard) }.padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(if (e.bad) Lab.red else Lab.border))
                        Spacer(Modifier.width(12.dp))
                        Text(e.name, fontSize = 14.sp, color = if (e.bad) Lab.text else Lab.sub, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(12.dp))
                        Text(time.format(Date(e.t * 1000)), fontSize = 12.sp, color = Lab.muted)
                    }
                }
            }
        }
    }
}
