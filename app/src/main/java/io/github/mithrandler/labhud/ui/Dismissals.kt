package io.github.mithrandler.labhud.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mithrandler.labhud.data.Alert
import io.github.mithrandler.labhud.data.Card
import io.github.mithrandler.labhud.data.LabRepository
import io.github.mithrandler.labhud.data.LabState
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * What the bell shows once the person has sent some of it away. Dismissing is on this phone only: it hides an alert or a problem
 * from the bell and from its badge, it does not touch the server. A dismissed item that clears is forgotten, so it comes back
 * if it happens again; [hidden] counts the ones still hidden, and [restoreAll] brings them back at once.
 */
class BellItems(
    val alerts: List<Alert>,
    val problems: List<Card>,
    val hidden: Int,
    private val restore: () -> Unit,
    private val dismissKeys: (Collection<String>) -> Unit,
) {
    fun restoreAll() = restore()
    val count: Int get() = alerts.size + problems.size
    fun dismiss(alert: Alert) = dismissKeys(listOf(alertKey(alert)))
    fun dismiss(card: Card) = dismissKeys(listOf(problemKey(card)))
    fun dismissAll() = dismissKeys(alerts.map(::alertKey) + problems.map(::problemKey))
}

private fun alertKey(a: Alert) = "a:${a.id}:${a.since ?: 0}:${a.text.hashCode()}"
private fun problemKey(c: Card) = "p:${c.id}"

@Composable
fun rememberBell(repo: LabRepository, st: LabState?): BellItems {
    val server by repo.server.collectAsStateWithLifecycle()
    val id = server?.id
    val gone by remember(id) { if (id == null) flowOf(emptySet()) else repo.store.dismissed(id) }.collectAsStateWithLifecycle(initialValue = emptySet())
    val scope = rememberCoroutineScope()
    val alerts = st?.alerts.orEmpty().filter { alertKey(it) !in gone }
    val problems = st?.problems.orEmpty().mapNotNull { st?.byId?.get(it) }.filter { problemKey(it) !in gone }
    // what cleared is forgotten, so that it shows again if it comes back
    LaunchedEffect(id, st?.alerts, st?.problems) {
        if (id == null || st == null || gone.isEmpty()) return@LaunchedEffect
        val active = st.alerts.map(::alertKey).toSet() + st.problems.map { "p:$it" }
        val kept = gone.intersect(active)
        if (kept != gone) repo.store.setDismissed(id, kept)
    }
    val live = st?.alerts.orEmpty().map(::alertKey).toSet() + st?.problems.orEmpty().map { "p:$it" }
    return BellItems(alerts, problems, hidden = gone.intersect(live).size, restore = { if (id != null) scope.launch { repo.store.setDismissed(id, emptySet()) } }) { keys ->
        if (id != null) scope.launch { repo.store.setDismissed(id, gone + keys) }
    }
}
