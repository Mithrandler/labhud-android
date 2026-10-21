package io.github.mithrandler.labhud.ui

import androidx.compose.foundation.background
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.Column
import io.github.mithrandler.labhud.data.Note
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mithrandler.labhud.data.Event
import io.github.mithrandler.labhud.data.LabRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DAY = SimpleDateFormat("EEEE d MMMM", Locale.ENGLISH)
private val STAMP = SimpleDateFormat("EEE d MMM, HH:mm", Locale.ENGLISH)

/**
 * Activity: the timeline (every event labhud kept, newest first, under a heading per day) and the
 * notifications labhud sent or would send (its outbox: the last 100, since it started).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreen(repo: LabRepository, onCard: (String) -> Unit) {
    var events by remember { mutableStateOf<List<Event>?>(null) }
    var notes by remember { mutableStateOf<List<Note>?>(null) }
    var error by remember { mutableStateOf("") }
    var tab by rememberSaveable { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        val api = repo.api()
        if (api == null) error = "No server paired." else {
            runCatching { api.events().events }.onSuccess { events = it }.onFailure { error = it.message ?: "Could not reach LabHUD." }
            runCatching { api.notes(0).notifications.reversed() }.onSuccess { notes = it }
        }
    }
    val time = SHORT_TIME
    Scaffold(
        containerColor = Lab.bg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Lab.bg),
                title = { Text("Activity") },
            )
        },
    ) { pad ->
      Column(Modifier.padding(pad).fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            listOf("Timeline", "Notifications").forEachIndexed { i, label ->
                SegmentedButton(selected = tab == i, onClick = { tab = i }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(label) }
            }
        }
        if (tab == 1) {
            NotesList(notes, error, onCard)
            return@Column
        }
        val list = events
        val days = remember(list) { list.orEmpty().groupBy { DAY.format(Date(it.t * 1000)) } }
        if (list == null) {
            Text(error.ifEmpty { "Loading…" }, color = Lab.muted, modifier = Modifier.padding(24.dp))
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
            if (list.isEmpty()) item { Text("Nothing happened yet.", color = Lab.muted) }
            days.forEach { (heading, rows) ->
                item(key = heading) {
                    val bad = rows.count { it.bad }
                    Text("${heading.uppercase()} · ${rows.size}" + if (bad > 0) " · $bad bad" else "", style = Heading,
                        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
                }
                itemsIndexed(rows, key = { i, _ -> "$heading#$i" }) { _, e ->
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
}

@Composable
private fun NotesList(notes: List<Note>?, error: String, onCard: (String) -> Unit) {
    val stamp = STAMP
    val list = notes ?: run {
        Text(error.ifEmpty { "Loading…" }, color = Lab.muted, modifier = Modifier.padding(24.dp))
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
        if (list.isEmpty()) item {
            Text("No notification since LabHUD started.", color = Lab.muted)
        }
        items(list, key = { it.id }) { n ->
            Row(
                Modifier.fillMaxWidth().clickable(enabled = n.card != null) { n.card?.let(onCard) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(Modifier.padding(top = 6.dp).size(8.dp).clip(CircleShape).background(if (n.bad) Lab.red else Lab.green))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(n.text, fontSize = 14.sp, color = if (n.bad) Lab.text else Lab.sub)
                    Text(stamp.format(Date(n.time * 1000)), fontSize = 12.sp, color = Lab.muted)
                }
            }
        }
    }
}
