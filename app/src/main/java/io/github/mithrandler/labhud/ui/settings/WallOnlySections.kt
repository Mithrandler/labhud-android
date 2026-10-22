package io.github.mithrandler.labhud.ui.settings

import androidx.compose.runtime.Composable
import io.github.mithrandler.labhud.data.LabRepository

/** Look is edited on the wall's ⚙ screen. */
@Suppress("UNUSED_PARAMETER")
@Composable
fun LookSection(repo: LabRepository, nav: SettingsNav) {
    SettingsPage(section("look")!!, nav) {
        item { WallOnly("⚙ > Look", listOf("Theme and colours", "Font and text size", "Density", "Night dimming and the calm screen")) }
    }
}

/** Integrations and the network scan are managed on the wall. */
@Suppress("UNUSED_PARAMETER")
@Composable
fun IntegrationsSection(repo: LabRepository, nav: SettingsNav) {
    SettingsPage(section("integrations")!!, nav) {
        item { WallOnly("⚙ > Integrations", listOf("Sources: Proxmox, Docker, Pi-hole and the rest", "Scanning the network for new ones")) }
    }
}
