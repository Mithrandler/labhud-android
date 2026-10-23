package io.github.mithrandler.labhud.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mithrandler.labhud.data.LabRepository

/** What a group's summary tile folds: its cards, the ones in trouble first; a tap opens the card. The tile's one level down, a whole screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupScreen(repo: LabRepository, id: String, onBack: () -> Unit, onCard: (String) -> Unit) {
    val state by repo.state.collectAsStateWithLifecycle()
    val group = state?.pages?.flatMap { it.groups }?.firstOrNull { it.id == id }
    val cards = group?.cards.orEmpty().mapNotNull { state?.byId?.get(it) }
        .sortedBy { if (isProblem(it)) 0 else if (it.severity() == "warn") 1 else 2 }
    Scaffold(
        containerColor = Lab.bg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Lab.bg),
                navigationIcon = { IconButton(onClick = onBack) { Icon(LabIcons.back, "Back") } },
                title = { Text(group?.title.orEmpty(), fontWeight = FontWeight.SemiBold) },
            )
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            items(cards, key = { it.id }) { c ->
                val head = c.headline()
                val word = statusWord(c)
                Row(Modifier.fillMaxWidth().clickable { onCard(c.id) }.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatusDot(c)
                    Text(c.name, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(word.ifEmpty { head?.let { "${it.value} ${it.short()}" } ?: c.summary.ifEmpty { c.rows.firstOrNull()?.let { r -> r.name.ifEmpty { r.value } }.orEmpty() } }, fontSize = 15.sp,
                        color = if (isProblem(c)) Lab.red else sevColor(head?.sev.orEmpty()), maxLines = 1)
                }
                HorizontalDivider(color = Lab.line)
            }
        }
    }
}
