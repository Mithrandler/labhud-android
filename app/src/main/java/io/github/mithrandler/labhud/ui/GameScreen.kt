package io.github.mithrandler.labhud.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mithrandler.labhud.data.Action
import io.github.mithrandler.labhud.data.GameServer
import io.github.mithrandler.labhud.data.LabRepository
import kotlinx.coroutines.launch

/** One game server: its state, players, address and the start / stop / restart buttons. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GameScreen(repo: LabRepository, id: String, onBack: () -> Unit, onSignIn: () -> Unit) {
    val state by repo.state.collectAsStateWithLifecycle()
    val server by repo.server.collectAsStateWithLifecycle()
    val live = state?.pages?.flatMap { it.servers }?.firstOrNull { it.id == id }
    var detail by remember { mutableStateOf<GameServer?>(null) }
    var ask by remember { mutableStateOf<Action?>(null) }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val gate = rememberGate(repo)

    // the players' names come only from /api/v1/game/<id>: every 20 s while it is open
    PollWhileStarted(id, intervalMs = 20_000) {
        repo.api()?.let { api -> runCatching { api.game(id) }.onSuccess { detail = it } }
    }
    val g = live?.copy(who = detail?.who.orEmpty()) ?: detail

    Scaffold(
        containerColor = Lab.bg,
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Lab.bg),
                navigationIcon = { IconButton(onClick = onBack) { Icon(LabIcons.back, "Back") } },
                title = { Text(g?.name ?: "", fontWeight = FontWeight.SemiBold) },
            )
        },
    ) { pad ->
        if (g == null) {
            Text("Loading…", color = Lab.muted, modifier = Modifier.padding(pad).padding(24.dp))
            return@Scaffold
        }
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(LabScale.space4), verticalArrangement = Arrangement.spacedBy(LabScale.space3)) {
            item {
                Surface(shape = CardShape, color = Lab.card, border = BorderStroke(1.dp, Lab.line)) {
                    Column(Modifier.fillMaxWidth().padding(LabScale.space4)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(LabScale.dotTile).clip(CircleShape).background(if (g.running) Lab.green else Lab.border))
                            Spacer(Modifier.width(LabScale.space3))
                            NamedIcon(g.icon, LabIcons.games, server, LabScale.iconTile)
                            Spacer(Modifier.width(LabScale.space2))
                            Text(if (g.running) "Running" else g.state.ifEmpty { "Stopped" }.replaceFirstChar(Char::uppercase),
                                fontSize = LabScale.textLg, fontWeight = FontWeight.SemiBold)
                        }
                        Text(g.title, color = Lab.muted, fontSize = LabScale.textSm, modifier = Modifier.padding(top = LabScale.space1))
                        Text(g.line, color = Lab.sub, fontSize = LabScale.textMd, modifier = Modifier.padding(top = LabScale.space1))
                    }
                }
            }
            if (g.actions.isNotEmpty()) item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(LabScale.space3), verticalArrangement = Arrangement.spacedBy(LabScale.space2)) {
                    g.actions.forEach { a -> ActionButton(a) { gate.press { ask = a } } }
                }
            }
            item {
                Panel("Server") {
                    val rows = listOfNotNull(
                        g.address.takeIf { it.isNotEmpty() }?.let { "Address" to it },
                        g.players?.let { "Players" to (if (g.max != null) "$it / ${g.max}" else "$it") },
                        g.map?.let { "Map" to it },
                        g.version?.let { "Version" to it },
                        g.ramMb.takeIf { it > 0 }?.let { "RAM" to (if (g.ramMaxMb != null) "$it / ${g.ramMaxMb} MB" else "$it MB") },
                        g.uptime.takeIf { it > 0 }?.let { "Up for" to duration(it) },
                    )
                    if (rows.isEmpty()) Text("Nothing more is known.", color = Lab.muted, fontSize = LabScale.textMd)
                    rows.forEach { (k, v) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = LabScale.space1)) {
                            Text(k, color = Lab.sub, fontSize = LabScale.textMd, modifier = Modifier.weight(1f))
                            Text(v, fontSize = LabScale.textMd, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
            if (g.who.isNotEmpty()) item {
                Panel("Playing now") { g.who.forEach { Text(it, fontSize = LabScale.textMd, modifier = Modifier.padding(vertical = LabScale.space1)) } }
            }
        }
    }
    ask?.let { a ->
        ActionDialog(a, onSend = { name, label -> scope.launch { snack.showSnackbar(sendAction(repo, name, label)) } }, onDismiss = { ask = null })
    }
    GateDialogs(gate, onSignIn)
}

/** 3725 s -> "1 h 2 min", 200000 s -> "2 d 7 h". */
fun duration(seconds: Long): String {
    val d = seconds / 86400
    val h = seconds % 86400 / 3600
    val m = seconds % 3600 / 60
    return when {
        d > 0 -> "$d d $h h"
        h > 0 -> "$h h $m min"
        else -> "$m min"
    }
}
