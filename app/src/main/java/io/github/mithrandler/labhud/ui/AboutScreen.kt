package io.github.mithrandler.labhud.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mithrandler.labhud.BuildConfig
import io.github.mithrandler.labhud.data.About
import io.github.mithrandler.labhud.data.LabRepository
import io.github.mithrandler.labhud.data.versionText
import io.github.mithrandler.labhud.ui.settings.BrandMark
import kotlinx.coroutines.launch

/** What the update check says, in words: the check is off, something is newer, or nothing is. */
internal fun updateWords(about: About?, failed: Boolean): String = when {
    failed -> "Could not ask the server."
    about == null -> "Not checked yet."
    !about.enabled -> "The update check is switched off on the server ([update] check)."
    about.serverUpdate() != null || about.appUpdate() != null -> "A newer version is out."
    about.latest.server.isEmpty() && about.latest.app.isEmpty() -> "The server has not reached GitHub yet; it asks every 6 hours."
    else -> "You are up to date." + if (about.checked > 0) " The server last asked GitHub " + versionText(about.checked) + "." else ""
}

/** About: what runs (server and app), and a button that asks the server again whether a newer version is out. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(repo: LabRepository, onBack: () -> Unit) {
    val info by repo.info.collectAsStateWithLifecycle()
    val server by repo.server.collectAsStateWithLifecycle()
    val uri = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var about by remember { mutableStateOf<About?>(null) }
    var failed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(server?.id, tick) {
        busy = true
        runCatching { repo.api()?.about() }.onSuccess { about = it; failed = it == null }.onFailure { failed = true }
        busy = false
    }
    Scaffold(
        containerColor = Lab.bg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Lab.bg),
                navigationIcon = { IconButton(onClick = onBack) { Icon(LabIcons.back, "Back") } },
                title = { Text("About", fontWeight = FontWeight.SemiBold) },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandMark(40.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(info?.title ?: server?.name ?: "LabHUD", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                    Text("Dashboard for a homelab", fontSize = 12.sp, color = Lab.muted)
                }
            }
            Panel("Versions") {
                VersionRow("Server", "LabHUD " + (info?.version?.ifEmpty { null } ?: "?") + if (info?.demo == true) " · demo" else "", about?.serverUpdate())
                VersionRow("This app", BuildConfig.VERSION_NAME, about?.appUpdate())
            }
            about?.appUpdate()?.let { v ->
                val url = about?.appDownload().orEmpty()
                if (url.isNotEmpty()) OutlinedButton(onClick = { runCatching { uri.openUri(url) } }) {
                    Icon(LabIcons.open, null); Spacer(Modifier.width(8.dp)); Text("Download $v")
                }
            }
            OutlinedButton(enabled = !busy, onClick = { scope.launch { tick++ } }) {
                Icon(LabIcons.update, null); Spacer(Modifier.width(8.dp)); Text(if (busy) "Checking…" else "Check for updates")
            }
            Text(if (busy) "" else updateWords(about, failed), fontSize = 13.sp, color = if (about?.appUpdate() != null || about?.serverUpdate() != null) Lab.accent else Lab.sub)
            about?.links?.let { l ->
                Panel("Source") {
                    listOf(l.source, l.app, l.agent).filter { it.isNotEmpty() }.forEach { u ->
                        Text(u.removePrefix("https://"), fontSize = 13.sp, color = Lab.accent,
                            modifier = Modifier.fillMaxWidth().clickable { runCatching { uri.openUri(u) } }.padding(vertical = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun VersionRow(label: String, version: String, newer: String?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, color = Lab.muted, modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(version, fontSize = 14.sp, color = Lab.text)
            if (newer != null) Text("newer: $newer", fontSize = 12.sp, color = Lab.accent)
        }
    }
}
