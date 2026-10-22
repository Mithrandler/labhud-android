package io.github.mithrandler.labhud.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.mithrandler.labhud.data.LabRepository
import io.github.mithrandler.labhud.ui.LabIcons

/**
 * The settings, as on the wall's ⚙: the same sections (Phones now lives in Account, as "Paired phones") (same ids, so a
 * labhud://settings/<id> link or a "see ⚙ > X" hint means the same place), plus THIS PHONE.
 *
 * A new section is one file in ui/settings/ with a `@Composable fun XSection(repo, nav)` and one
 * line in [SECTIONS]; the list, the search and the route `settings/{id}` pick it up by themselves.
 */
class Section(
    val id: String,
    val group: String,
    val title: String,
    /** One line under the title in the list. */
    val hint: String,
    val icon: () -> ImageVector,
    /** Hidden while this phone is not signed in as the admin. */
    val admin: Boolean = true,
    /** Extra words the search finds it by. */
    val keywords: String = "",
    val screen: @Composable (LabRepository, SettingsNav) -> Unit,
)

/** Where a section can go from inside: back to the list, sign in, an agent, a new server, the wall. */
class SettingsNav(
    val back: () -> Unit,
    val signIn: () -> Unit,
    val agent: (String) -> Unit,
    val addServer: () -> Unit,
    val wall: () -> Unit,
    val homeApp: (Boolean) -> Unit,
)

/** The groups in order: the wall's four, then this phone's own. */
val GROUPS = listOf(
    "account" to "ACCOUNT",
    "display" to "BOARD",
    "infra" to "INFRASTRUCTURE",
    "monitor" to "ALERTS",
    "system" to "SYSTEM",
    "phone" to "THIS PHONE",
)

val SECTIONS: List<Section> = listOf(
    Section("security", "account", "Account", "Who is signed in, PIN, sessions, paired phones", { LabIcons.security },
        admin = false, keywords = "admin sign in sign out pin lock totp oidc sso sessions security phones paired devices pair unpair remove tokens") { r, n -> SecuritySection(r, n) },

    Section("look", "display", "Look", "Theme, font, density, night and calm", { LabIcons.look },
        keywords = "theme colour font text size night calm density") { r, n -> LookSection(r, n) },
    Section("dashboard", "display", "Dashboard", "Pages, groups and cards, rotation", { LabIcons.pages },
        keywords = "pages rotation groups cards layout hide icon size discover new guests containers services approve dismiss") { r, n -> DashboardSection(r, n) },

    Section("hosts", "infra", "Hosts and agents", "Agents, what they may do, updates", { LabIcons.devices },
        keywords = "agents capabilities targets wol update host") { r, n -> HostsSection(r, n) },
    Section("containers", "infra", "Containers", "Update on their own, clean unused images", { LabIcons.container },
        keywords = "docker auto update prune images registries scan cve") { r, n -> ContainersSection(r, n) },
    Section("integrations", "infra", "Integrations", "Sources and the network scan", { LabIcons.sources },
        keywords = "sources scan network catalog proxmox") { r, n -> IntegrationsSection(r, n) },

    Section("alerts", "monitor", "Alerts", "Thresholds, rules, quiet hours", { LabIcons.warning },
        keywords = "rules thresholds warn crit quiet beep flash") { r, n -> AlertsSection(r, n) },
    Section("notifications", "monitor", "Notifications", "On this phone, and where LabHUD sends its news", { LabIcons.bell },
        admin = false, keywords = "ntfy push instant unifiedpush email webhook discord gotify") { r, n -> NotifySection(r, n) },

    Section("api", "system", "API and audit", "Tokens and the log of who did what", { LabIcons.key },
        keywords = "tokens audit log bearer scope who history") { r, n -> ApiSection(r, n) },
    Section("system", "system", "System", "config.toml, about, backup", { LabIcons.advanced },
        keywords = "config toml take over keys backup restore about version danger") { r, n -> SystemSection(r, n) },

    Section("servers", "phone", "Servers", "The LabHUDs this phone knows", { LabIcons.server },
        admin = false, keywords = "add pair rename remove certificate") { r, n -> ServersSection(r, n) },
    Section("theme", "phone", "Appearance", "System, dark, light or the HUD", { LabIcons.look },
        admin = false, keywords = "theme dark light colour wallpaper material hud") { r, n -> ThemeSection(r, n) },
    Section("lock", "phone", "App lock", "Phone PIN or fingerprint when it opens", { LabIcons.lock },
        admin = false, keywords = "biometric fingerprint pin pattern") { r, n -> LockSection(r, n) },
    Section("wall", "phone", "Wall mode", "The wall page on this screen, kept on", { LabIcons.wall },
        admin = false, keywords = "kiosk home screen boot tablet") { r, n -> WallSection(r, n) },
)

/** What the list shows: everything for the admin, otherwise only what is not the admin's. */
fun visibleSections(admin: Boolean, query: String = "", all: List<Section> = SECTIONS): List<Section> {
    val q = query.trim().lowercase()
    return all.filter { admin || !it.admin }
        .filter { q.isEmpty() || q in it.title.lowercase() || q in it.hint.lowercase() || q in it.keywords.lowercase() }
}

fun section(id: String): Section? = SECTIONS.firstOrNull { it.id == id }
