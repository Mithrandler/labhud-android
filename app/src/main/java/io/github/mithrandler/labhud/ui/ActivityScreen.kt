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
import io.github.mithrandler.labhud.data.noteAction
import io.github.mithrandler.labhud.data.allowed
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
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
fun ActivityScreen(repo: LabRepository, bar: BarNav) {
    val onCard = bar.onCard
    var events by remember { mutableStateOf<List<Event>?>(null) }
    var notes by remember { mutableStateOf<List<Note>?>(null) }
    var error by remember { mutableStateOf("") }
    var tab by rememberSaveable { mutableStateOf(0) }
    var cleared by remember { mutableStateOf(0L) }
    val server by repo.server.collectAsStateWithLifecycle()
    val state by repo.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LaunchedEffect(server?.id) { server?.let { cleared = repo.store.clearedNotes(it.id) } }
    val shown = remember(notes, cleared) { notes?.filter { it.time > cleared } }
    LaunchedEffect(Unit) {
        val api = repo.api()
        if (api == null) error = "No server paired." else {
            runCatching { api.events().events }.onSuccess { events = it }.onFailure { error = it.message ?: "Could not reach LabHUD." }
            runCatching { api.notes(0).notifications.reversed() }.onSuccess { notes = it }
        }
    }
    val time = SHORT_TIME
    val barScroll = rememberLabBarScroll()
    Scaffold(
        modifier = Modifier.labBarScroll(barScroll),
        containerColor = Lab.bg,
        topBar = {
            MainTopBar(
                repo, bar,
                title = { Text("Activity") },
                extra = {
                    if (tab == 2 && !shown.isNullOrEmpty()) TextButton(onClick = {
                        val id = server?.id ?: return@TextButton
                        val last = notes.orEmpty().maxOf { it.time }
                        cleared = last
                        scope.launch { repo.store.setClearedNotes(id, last) }
                    }) { Text("Clear") }
                },
            )
        },
    ) { pad ->
      Column(Modifier.padding(pad).fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            listOf("Alerts", "Timeline", "Notifications").forEachIndexed { i, label ->
                SegmentedButton(selected = tab == i, onClick = { tab = i }, shape = SegmentedButtonDefaults.itemShape(i, 3)) { Text(label) }
            }
        }
        if (tab == 0) {
            val st = state
            if (st == null) Text("Connecting…", color = Lab.muted, modifier = Modifier.padding(24.dp))
            else androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize()) {
                item { BellContent(st, onCard, Modifier.padding(16.dp)) }
            }
            return@Column
        }
        if (tab == 2) {
            NotesList(shown, error, onCard, repo)
            return@Column
        }
        val list = events
        val days = remember(list) { list.orEmpty().groupBy { DAY.format(Date(it.t * 1000)) } }
        if (list == null) {
            Text(error.ifEmpty { "Loading…" }, color = Lab.muted, modifier = Modifier.padding(24.dp))
            return@Column
        }
        if (list.isEmpty()) { Text("Nothing happened yet.", color = Lab.muted, modifier = Modifier.padding(16.dp)); return@Column }
        // the day's heading and its events as one list, a few to a page; a page that starts mid-day names the day again
        val lines = remember(days) {
            days.flatMap { (heading, rows) -> listOf<Any>(heading to rows) + rows.map { heading to it } }
        }
        PagedScreen(lines, per = 8) { line ->
            @Suppress("UNCHECKED_CAST")
            if (line is Pair<*, *> && line.second is List<*>) {
                val rows = line.second as List<Event>
                val bad = rows.count { it.bad }
                Text("${(line.first as String).uppercase()} · ${rows.size}" + if (bad > 0) " · $bad bad" else "", style = Heading,
                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
            } else {
                val e = (line as Pair<*, *>).second as Event
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = e.card != null) { e.card?.let(onCard) }.padding(vertical = 9.dp),
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

@Composable
private fun NotesList(notes: List<Note>?, error: String, onCard: (String) -> Unit, repo: LabRepository) {
    val stamp = STAMP
    val scope = rememberCoroutineScope()
    var done by remember { mutableStateOf(mapOf<Long, String>()) }
    val list = notes ?: run {
        Text(error.ifEmpty { "Loading…" }, color = Lab.muted, modifier = Modifier.padding(24.dp))
        return
    }
    if (list.isEmpty()) { Text("No notifications.", color = Lab.muted, modifier = Modifier.padding(16.dp)); return }
    PagedScreen(list, per = 4) { n ->
        run {
            Row(
                Modifier.fillMaxWidth().clickable(enabled = n.card != null) { n.card?.let(onCard) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(Modifier.padding(top = 6.dp).size(8.dp).clip(CircleShape).background(if (n.bad) Lab.red else Lab.green))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(n.text, fontSize = 14.sp, color = if (n.bad) Lab.text else Lab.sub)
                    Text(stamp.format(Date(n.time * 1000)), fontSize = 12.sp, color = Lab.muted)
                    val buttons = n.actions.filter { it.allowed() }
                    if (buttons.isNotEmpty()) {
                        val said = done[n.id]
                        if (said != null && said.isEmpty()) Text("Done.", fontSize = 12.sp, color = Lab.green)
                        else Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                            buttons.forEach { a ->
                                androidx.compose.material3.OutlinedButton(onClick = {
                                    scope.launch {
                                        val r = runCatching { repo.api()?.noteAction(a) }
                                        val ok = r.getOrNull()?.ok == true
                                        done = done + (n.id to if (ok) "" else (r.getOrNull()?.error?.takeIf { it.isNotBlank() }
                                            ?: r.exceptionOrNull()?.message ?: "Could not do that."))
                                    }
                                }) { Text(a.label) }
                            }
                        }
                        if (said != null && said.isNotEmpty()) Text(said, fontSize = 12.sp, color = Lab.red)
                    }
                }
            }
        }
    }
}
