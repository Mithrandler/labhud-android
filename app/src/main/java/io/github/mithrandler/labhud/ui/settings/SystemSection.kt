package io.github.mithrandler.labhud.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mithrandler.labhud.BuildConfig
import io.github.mithrandler.labhud.data.LabRepository
import io.github.mithrandler.labhud.ui.Lab
import io.github.mithrandler.labhud.ui.Panel

/** System: the config file (on the wall), what this LabHUD and this app are, what is coming. */
@Composable
fun SystemSection(repo: LabRepository, nav: SettingsNav) {
    val info by repo.info.collectAsState()
    val server by repo.server.collectAsState()
    SettingsPage(section("system")!!, nav) {
        heading("About")
        item {
            Panel("") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BrandMark(40.dp)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(info?.title ?: server?.name ?: "LabHUD", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Text("LabHUD " + (info?.version?.ifEmpty { null } ?: "?") + (if (info?.demo == true) " · demo" else ""),
                            fontSize = 12.sp, color = Lab.muted)
                        Text("This app ${BuildConfig.VERSION_NAME}", fontSize = 12.sp, color = Lab.muted)
                    }
                }
                Text("github.com/Mithrandler/labhud · github.com/Mithrandler/labhud-android", fontSize = 12.sp, color = Lab.muted,
                    modifier = Modifier.padding(top = 10.dp))
            }
        }
        heading("config.toml")
        item { WallOnly("⚙ > System", listOf("Take over the config file", "The editor and its keys")) }
        item { Soon(listOf("Backup and restore, with the secrets encrypted", "Danger zone: new keys, forget every phone")) }
    }
}
