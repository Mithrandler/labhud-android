package io.github.mithrandler.labhud.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mithrandler.labhud.BuildConfig
import io.github.mithrandler.labhud.data.LabRepository
import io.github.mithrandler.labhud.ui.settings.AccountPanel
import io.github.mithrandler.labhud.ui.settings.BrandMark
import io.github.mithrandler.labhud.ui.settings.GROUPS
import io.github.mithrandler.labhud.ui.settings.Section
import io.github.mithrandler.labhud.ui.settings.SignInDialog
import io.github.mithrandler.labhud.ui.settings.visibleSections
import kotlinx.coroutines.launch

/**
 * The settings list: the wall's four groups (DISPLAY, INFRASTRUCTURE, MONITORING, SYSTEM) and this
 * phone's own, one row per section with its icon; a row opens the section (route settings/{id}).
 * Not signed in as the admin, only what is not the admin's shows, under a Sign in panel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(repo: LabRepository, bar: BarNav, onOpen: (String) -> Unit, signInFirst: Boolean = false) {
    val rights by repo.rights.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var signingIn by remember { mutableStateOf(signInFirst) }
    var query by rememberSaveable { mutableStateOf("") }
    val shown = visibleSections(rights.admin, query)
    val favs by repo.store.favoriteSections.collectAsStateWithLifecycle(initialValue = emptySet())
    val star: (String) -> Unit = { id -> scope.launch { repo.store.toggleFavoriteSection(id) } }

    val barScroll = rememberLabBarScroll()
    Scaffold(
        modifier = Modifier.labBarScroll(barScroll),
        containerColor = Lab.bg,
        topBar = {
            MainTopBar(repo, bar, title = { Text("Settings") })
        },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                OutlinedTextField(query, { query = it.take(40) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(LabIcons.search, null) }, placeholder = { Text("Search settings") })
            }
            if (!rights.admin && query.isBlank()) item {
                AccountPanel(admin = false, pin = rights.pin, onSignIn = { signingIn = true }, onSignOut = { scope.launch { repo.signOut() } })
            }
            val starred = shown.filter { it.id in favs }
            if (query.isBlank() && starred.isNotEmpty()) {
                item(key = "g-fav") { Text("FAVORITES", style = Heading, modifier = Modifier.padding(top = 10.dp)) }
                item(key = "s-fav") { SectionGroup(starred, favs, onOpen, star) }
            }
            GROUPS.forEach { (group, title) ->
                val rows = shown.filter { it.group == group }
                if (rows.isEmpty()) return@forEach
                item(key = "g-$group") { Text(title, style = Heading, modifier = Modifier.padding(top = 10.dp)) }
                item(key = "s-$group") { SectionGroup(rows, favs, onOpen, star) }
            }
            if (shown.isEmpty()) item { Text("Nothing called \"$query\".", color = Lab.muted, fontSize = 14.sp) }
            item {
                Text("LabHUD ${BuildConfig.VERSION_NAME} · github.com/Mithrandler/labhud-android", fontSize = 12.sp, color = Lab.muted,
                    modifier = Modifier.padding(top = 16.dp))
            }
        }
    }

    if (signingIn) SignInDialog(repo, onDone = { signingIn = false })
}

/** One group's sections in one card, divided by thin lines, like a phone's own settings. */
@Composable
private fun SectionGroup(rows: List<Section>, favs: Set<String>, onOpen: (String) -> Unit, onStar: (String) -> Unit) {
    Surface(shape = CardShape, color = Lab.card, border = BorderStroke(1.dp, Lab.line)) {
        Column {
            rows.forEachIndexed { i, s ->
                if (i > 0) HorizontalDivider(color = Lab.line, modifier = Modifier.padding(start = 52.dp))
                Row(
                    Modifier.fillMaxWidth().clickable { onOpen(s.id) }.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(s.icon(), null, tint = Lab.title, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.title, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = Lab.text)
                        Text(s.hint, fontSize = 12.sp, color = Lab.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(if (s.id in favs) "★" else "☆", fontSize = 20.sp, color = if (s.id in favs) Lab.title else Lab.muted,
                        modifier = Modifier.clickable { onStar(s.id) }.padding(horizontal = 8.dp, vertical = 4.dp))
                    Icon(LabIcons.chevronRight, null, tint = Lab.muted, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
