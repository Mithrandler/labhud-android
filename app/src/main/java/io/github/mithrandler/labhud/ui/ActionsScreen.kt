package io.github.mithrandler.labhud.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mithrandler.labhud.data.Action
import io.github.mithrandler.labhud.data.Card
import io.github.mithrandler.labhud.data.LabRepository
import kotlinx.coroutines.launch

/** Sends one action through labhud (signed there) and says how it went, for a snackbar. */
suspend fun sendAction(repo: LabRepository, name: String, label: String): String {
    val api = repo.api() ?: return "$label: no server paired"
    val r = api.action(name)
    api.actionLog(name, if (r.ok) "accepted" else "refused", 0)
    return if (r.ok) "$label: sent" else "$label: ${r.error.ifBlank { "refused" }}"
}

/** "Are you sure?" for an action: one Yes, or one button per choice (a VM's start/shutdown/reboot). */
@Composable
fun ActionDialog(a: Action, onSend: (name: String, label: String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(a.label) },
        text = { if (a.confirm.isNotEmpty()) Text(a.confirm) },
        confirmButton = {
            if (a.choices.isEmpty()) {
                TextButton(onClick = { onDismiss(); onSend(a.name, a.label) }) { Text("Yes", color = if (a.danger) Lab.red else Lab.accent) }
            } else Column(horizontalAlignment = Alignment.End) {
                a.choices.forEach { ch ->
                    TextButton(onClick = { onDismiss(); onSend(ch.action, ch.label) }) { Text(ch.label, color = if (ch.danger) Lab.red else Lab.accent) }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Every button labhud offers, in the order of the pages: a card per row, its buttons under it. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ActionsScreen(repo: LabRepository, onCard: (String) -> Unit) {
    val state by repo.state.collectAsState()
    val info by repo.info.collectAsState()
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var ask by remember { mutableStateOf<Action?>(null) }

    Scaffold(
        containerColor = Lab.bg,
        snackbarHost = { SnackbarHost(snack) },
        topBar = { TopAppBar(colors = TopAppBarDefaults.topAppBarColors(containerColor = Lab.bg), title = { Text("Actions") }) },
    ) { pad ->
        val st = state
        val byId = st?.byId.orEmpty()
        val seen = HashSet<String>()
        val sections: List<Pair<String, List<Card>>> = st?.pages.orEmpty().mapNotNull { p ->
            val cards = p.groups.flatMap { it.cards }.mapNotNull { byId[it] }.filter { it.actions.isNotEmpty() && seen.add(it.id) }
            if (cards.isEmpty()) null else p.title to cards
        }
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (st == null) {
                item { Text("Connecting…", color = Lab.muted) }
            } else if (sections.isEmpty()) {
                item {
                    Panel("") {
                        Text(
                            if (info?.actions == false) "This LabHUD does not take actions from phones. On the server they need " +
                                "signed actions: LABHUD_ACTION_SECRET, with the same secret on the agent that runs them."
                            else "No card on this LabHUD has buttons.",
                            color = Lab.sub, fontSize = 14.sp,
                        )
                    }
                }
            }
            sections.forEach { (title, cards) ->
                item(key = "h-$title") { Text(title.uppercase(), style = Heading, modifier = Modifier.padding(top = 6.dp)) }
                items(cards, key = { "a-" + it.id }) { c ->
                    Surface(onClick = { onCard(c.id) }, shape = CardShape, color = Lab.card, border = BorderStroke(1.dp, Lab.line)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (c.status != null) { StatusDot(c, 10); Spacer(Modifier.width(8.dp)) }
                                Text(c.name, fontWeight = FontWeight.SemiBold)
                            }
                            FlowRow(
                                Modifier.padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                c.actions.forEach { a ->
                                    Button(
                                        onClick = { ask = a },
                                        colors = if (a.danger) ButtonDefaults.buttonColors(containerColor = Lab.red, contentColor = Lab.text)
                                        else ButtonDefaults.buttonColors(),
                                    ) { Text(a.label) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    ask?.let { a ->
        ActionDialog(a, onSend = { name, label -> scope.launch { snack.showSnackbar(sendAction(repo, name, label)) } }, onDismiss = { ask = null })
    }
}
