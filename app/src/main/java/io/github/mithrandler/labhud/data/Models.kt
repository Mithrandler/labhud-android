package io.github.mithrandler.labhud.data

import kotlinx.serialization.SerialName
import androidx.compose.runtime.getValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/** The newest release tags the server read ("" when not known). */
@Serializable
data class Latest(val server: String = "", val agent: String = "", val app: String = "")

/** Which of the agent and the app are behind their latest release. */
@Serializable
data class Outdated(val agent: Boolean = false, val app: Boolean = false)

/** The links in [About]. */
@Serializable
data class AboutLinks(val source: String = "", val app: String = "", val agent: String = "")

/** GET /api/brand/about: what the server is, and the update check (`enabled` false: switched off). */
@Serializable
data class About(
    val name: String = "LabHUD",
    val version: String = "",
    val links: AboutLinks = AboutLinks(),
    val enabled: Boolean = false,
    val latest: Latest = Latest(),
    val update: Boolean = false,
    val outdated: Outdated = Outdated(),
    val checked: Long = 0,
) {
    /** "Update available 0.1.3", or null when this app is not behind (or the check is off). */
    fun appUpdate(): String? = if (enabled && outdated.app && latest.app.isNotEmpty()) latest.app.removePrefix("v") else null

    /** The same for the server itself. */
    fun serverUpdate(): String? = if (enabled && update && latest.server.isNotEmpty()) latest.server.removePrefix("v") else null

    /** Where to get the new app: the app's release page. */
    fun appDownload(): String = links.app.trimEnd('/').let { if (it.isEmpty()) "" else "$it/releases/latest" }
}

/** API info from GET /api/v1/info. */
@Serializable
data class Info(
    val api: Int = 0,
    val version: String = "",
    val title: String = "LabHUD",
    val demo: Boolean = false,
    val actions: Boolean = false,
    @SerialName("maintenance_buttons") val maintenanceButtons: Boolean = false,
    val pairing: Boolean = false,
    val admin: Boolean = false,
    val trends: List<String> = emptyList(),
    /** GET /api/v1/uptime answers. */
    val uptime: Boolean = false,
    /** labhud 0.3: this phone is signed in as the admin; the display has a PIN; a live unlock ticket. */
    @SerialName("device_admin") val deviceAdmin: Boolean = false,
    val pin: Boolean = false,
    val unlocked: Boolean = false,
)

/** Answer of /api/v1/admin-login, admin-logout and unlock. */
@Serializable
data class AdminAnswer(
    val ok: Boolean = false,
    val error: String = "",
    @SerialName("device_admin") val deviceAdmin: Boolean = false,
    val pin: Boolean = false,
    val unlocked: Boolean = false,
    val ticket: String? = null,
    val idle: Int = 120,
    val wait: Double = 0.0,
)

/** Response from POST /api/v1/pair. */
@Serializable
data class PairResponse(
    val ok: Boolean = false,
    val token: String = "",
    val name: String = "",
    val error: String = "",
    val info: Info = Info(),
)

/** A card group within a page. */
@Serializable
data class Group(
    val id: String = "",
    val title: String = "",
    val cards: List<String> = emptyList(),
)

/** A page grouping cards. */
@Serializable
data class Page(
    val id: String = "",
    val title: String = "",
    val groups: List<Group> = emptyList(),
    val games: Boolean = false,
    /** A service-icon name (ServiceIcons), "" when the server names none. */
    val icon: String = "",
    /** A games page: its game servers, as the wall's tiles show them. */
    val servers: List<GameServer> = emptyList(),
    /** At most this many columns of cells (1 to 6), 0 when the server sets none (labhud 0.1.2). */
    val columns: Int = 0,
    /** "compact" or "normal"; "" when the server sets none: the app's own. */
    val density: String = "",
) {
    val compact: Boolean get() = density == "compact"
}

/** A game server on a games page (labhud 0.3), or from /api/v1/game/<id> with [who]. */
@Serializable
data class GameServer(
    val id: String = "",
    val name: String = "",
    val game: String = "",
    val icon: String = "",
    val state: String = "",
    val running: Boolean = false,
    val title: String = "",
    val line: String = "",
    val players: Int? = null,
    val max: Int? = null,
    val address: String = "",
    @SerialName("ram_mb") val ramMb: Long = 0,
    @SerialName("ram_max_mb") val ramMaxMb: Long? = null,
    val uptime: Long = 0,
    val map: String? = null,
    val version: String? = null,
    val actions: List<Action> = emptyList(),
    val who: List<String> = emptyList(),
)

/** One of a host's agent charts (labhud 0.3): a key with history, its label and format. */
@Serializable
data class ChartRef(
    val key: String = "",
    val label: String = "",
    val format: String = "",
)

/** A container on a card whose own agent has Docker (labhud 0.3, /api/v1/card only). */
@Serializable
data class Container(
    val name: String = "",
    /** The Docker image and the icon the server derived from it ("" when none). */
    val image: String = "",
    val icon: String = "",
    val up: Boolean = false,
    val status: String = "",
    val update: Boolean = false,
    val updating: Boolean = false,
    val actions: List<Action> = emptyList(),
    /** Its compose project, "3.1% CPU · 245M", its latest update or crash. */
    val project: String = "",
    val usage: String = "",
    val last: String = "",
    /** Its image's last scan (an agent with `scan`), else null. */
    val scan: Scan? = null,
)

/** An image scan's summary (cardstate.scan_summary): the critical and high CVEs, when (Unix time). */
@Serializable
data class Scan(val critical: Int = 0, val high: Int = 0, val t: Long = 0) {
    /** "CVEs: 2 critical · 5 high", or "no critical or high CVEs" (cardstate.scan_text). */
    fun text(): String =
        if (critical == 0 && high == 0) "no critical or high CVEs"
        else "CVEs: " + listOfNotNull(if (critical > 0) "$critical critical" else null, if (high > 0) "$high high" else null).joinToString(" · ")
}

/** A [[bookmark]] link. */
@Serializable
data class Bookmark(val group: String = "", val name: String = "", val url: String = "")

/** A container with a newer image, anywhere in the fleet (`list = "fleet.updates"`). */
@Serializable
data class FleetUpdate(
    val name: String = "",
    val host: String = "",
    val image: String = "",
    val updating: Boolean = false,
    val action: Action? = null,
    /** The running image's last scan, else null. */
    val scan: Scan? = null,
)

/** A single metric on a card. */
@Serializable
data class Metric(
    val key: String = "",
    val label: String = "",
    val value: String = "",
    val sev: String = "",
    val trend: Boolean = false,
    /** percent, celsius, bytes, count…; "" from an older server. */
    val format: String = "",
    /** A percentage's bar length (0..100); where it turns orange and red, only when a high value is bad. */
    val pct: Double? = null,
    val warn: Double? = null,
    val crit: Double? = null,
)

/** A choice within an action. */
@Serializable
data class Choice(
    val label: String = "",
    val action: String = "",
    val danger: Boolean = false,
)

/** An action button available on a card. */
@Serializable
data class Action(
    val name: String = "",
    val label: String = "",
    val confirm: String = "",
    val danger: Boolean = false,
    val choices: List<Choice> = emptyList(),
)

/** A hyperlink attached to a card. */
@Serializable
data class Link(
    val label: String = "",
    val url: String = "",
)

/** A parsed [name, value, bad, quiet] row. */
data class Row(
    val name: String,
    val value: String,
    val bad: Boolean,
    val quiet: Boolean = false,
    /** A service-icon name (labhud 0.1.x, the fifth element), "" when the row has none. */
    val icon: String = "",
)

fun JsonArray.toRow(): Row = Row(
    name = getOrNull(0)?.jsonPrimitive?.contentOrNull ?: "",
    value = getOrNull(1)?.jsonPrimitive?.contentOrNull ?: "",
    bad = getOrNull(2)?.jsonPrimitive?.booleanOrNull ?: false,
    quiet = getOrNull(3)?.jsonPrimitive?.booleanOrNull ?: false,
    icon = getOrNull(4)?.jsonPrimitive?.contentOrNull ?: "",
)

/** A section (panel) on a card. */
@Serializable
data class Section(
    val title: String = "",
    @SerialName("rows") val rawRows: List<JsonArray> = emptyList(),
) {
    val rows: List<Row> by lazy(LazyThreadSafetyMode.PUBLICATION) { rawRows.map { it.toRow() } }
}

/** A card in the lab state. */
@Serializable
data class Card(
    val id: String = "",
    val name: String = "",
    val page: String? = null,
    val group: String? = null,
    val kind: String = "card",
    val status: String? = null,
    val bad: Boolean? = null,
    val summary: String = "",
    val frozen: Boolean = false,
    val note: String = "",
    @SerialName("note_kind") val noteKind: String = "",
    val metrics: List<Metric> = emptyList(),
    val subtitle: String = "",
    val url: String = "",
    val links: List<Link> = emptyList(),
    @SerialName("as_of") val asOf: Long? = null,
    @SerialName("maintenance_until") val maintenanceUntil: Long? = null,
    val actions: List<Action> = emptyList(),
    @SerialName("maintenance_button") val maintenanceButton: Boolean = false,
    @SerialName("rows") val rawRows: List<JsonArray> = emptyList(),
    val sections: List<Section> = emptyList(),
    /** labhud 0.3: proxmox, nas, docker, vm, pc, or "" for a card that is not a machine. */
    val type: String = "",
    val charts: List<ChartRef> = emptyList(),
    val containers: List<Container> = emptyList(),
    val agent: String = "",
    /** A notes card's text, a bookmarks card's links, the fleet's updates. */
    val notes: String? = null,
    val bookmarks: List<Bookmark> = emptyList(),
    /** null: not the fleet's updates card. */
    val updates: List<FleetUpdate>? = null,
    /** The card's icon (a file /icons/svc/<icon>.png on the server, or one of LabIcons' names), "" when none fits. */
    val icon: String = "",
    /** Its place in the grid: s (1×1), m (2×1), l (2×2), xl (4×2); "" means s. */
    val size: String = "",
    /** "clock" or "calendar": a card the app draws on its own, without data (labhud 0.1.x); "" for every other. */
    val widget: String = "",
    /** The clock's time zone (an IANA name), "" for the phone's. */
    val timezone: String = "",
    /** An address the card shows inside itself, in a web view (nothing else is loaded from it); "" for none. */
    val frame: String = "",
    @SerialName("frame_height") val frameHeight: Int = 0,
) {
    val rows: List<Row> by lazy(LazyThreadSafetyMode.PUBLICATION) { rawRows.map { it.toRow() } }
}

/** An alert entry. */
@Serializable
data class Alert(
    val id: String = "",
    val text: String = "",
    /** From an [[alert]] rule: the card it is about and since when (Unix time). */
    val card: String? = null,
    val since: Long? = null,
)

/**
 * The board's theme (labhud 0.1.4, [display] base and accent): `base` is "oled" or "grey", `accent` a
 * name from [ACCENTS] or "#rrggbb". The app's HUD look follows it; the Material themes keep the phone's.
 */
@Serializable
data class BoardTheme(val base: String = "oled", val accent: String = "blue") {
    /** The accent and title colours as 0xAARRGGBB: a preset's pair, a custom colour for both, else blue's. */
    val accentArgb: Pair<Long, Long> get() = ACCENTS[accent] ?: customArgb(accent) ?: ACCENTS.getValue("blue")
    val grey: Boolean get() = base == "grey"

    companion object {
        private val ACCENTS = mapOf(
            "blue" to (0xFF8AB4F8L to 0xFF7CC4FFL), "green" to (0xFF39FF88L to 0xFF5CFFA0L),
            "orange" to (0xFFFFA94DL to 0xFFFFB86BL), "red" to (0xFFFF8A80L to 0xFFFF9E94L),
            "violet" to (0xFFB39DFFL to 0xFFC4B2FFL), "cyan" to (0xFF4CE7FFL to 0xFF7AEEFFL),
            "pink" to (0xFFFF7EB6L to 0xFFFF9AC6L), "yellow" to (0xFFFFE066L to 0xFFFFE98AL),
        )
        private val HEX = Regex("^#[0-9a-fA-F]{6}$")
        private fun customArgb(s: String): Pair<Long, Long>? =
            if (HEX.matches(s)) (0xFF000000L or s.substring(1).toLong(16)).let { it to it } else null
    }
}

/** The full lab state document. */
@Serializable
data class LabState(
    val title: String = "LabHUD",
    val now: Long = 0,
    val pages: List<Page> = emptyList(),
    val cards: List<Card> = emptyList(),
    val problems: List<String> = emptyList(),
    val alerts: List<Alert> = emptyList(),
    /** The board's look (labhud 0.1.4); the default is the wall's own. */
    val theme: BoardTheme = BoardTheme(),
    /** viewer / operator / admin: what this token or phone is (labhud 0.1.4); `pages` already holds only its pages. */
    val role: String = "",
    /** Only in GET /api/v1/state, not in the stream: see [Info]. */
    @SerialName("device_admin") val deviceAdmin: Boolean? = null,
    val pin: Boolean? = null,
) {
    val byId: Map<String, Card> by lazy(LazyThreadSafetyMode.PUBLICATION) { cards.associateBy { it.id } }
}

/** A single event in the timeline. */
@Serializable
data class Event(
    val t: Long = 0,
    val name: String = "",
    val bad: Boolean = false,
    val card: String? = null,
    val state: String? = null,
)

/** Wrapper for GET /api/v1/events. */
@Serializable
data class Events(
    val events: List<Event> = emptyList(),
)

/** Response from GET /api/v1/history. */
@Serializable
data class History(
    val step: Int = 0,
    val series: Map<String, List<Double?>> = emptyMap(),
)

/**
 * Response from GET /api/v1/uptime: per card, one value per [step] seconds, oldest first: the
 * share of the time it was up (0..1), -1 off on purpose, null not known. [percent] over the range.
 */
@Serializable
data class Uptime(
    val step: Int = 0,
    val cards: Map<String, List<Double?>> = emptyMap(),
    val percent: Map<String, Double> = emptyMap(),
    /** Per card, the share (0..100) of the range that is known at all; under 100 it is shown next to the percent. */
    val coverage: Map<String, Double> = emptyMap(),
)

/** Response from GET /api/v1/sparks: the last half hour of every number with a trend, one value per [step] seconds, oldest first. */
@Serializable
data class Sparks(
    val step: Int = 0,
    val series: Map<String, List<Double?>> = emptyMap(),
)

/** One message of /api/v1/notifications: what labhud sent, or would send, as a notification. */
@Serializable
data class Note(
    val id: Long = 0,
    val time: Long = 0,
    val title: String = "",
    val text: String = "",
    val bad: Boolean = false,
    val card: String? = null,
)

@Serializable
data class Notes(
    val via: String = "both",
    val notifications: List<Note> = emptyList(),
)

/** A paired phone, from /api/v1/devices. */
@Serializable
data class Phone(
    val name: String = "",
    val paired: Long = 0,
    val seen: Long = 0,
    val admin: Boolean = false,
    val push: Boolean = false,
)

@Serializable
data class Phones(val devices: List<Phone> = emptyList(), val pairing: Boolean = false)

/** An agent, from /api/agents/list (admin). */
@Serializable
data class AgentRow(
    val id: String = "",
    val name: String = "",
    val online: Boolean = false,
    val version: String = "",
    @SerialName("addr") val address: String = "",
    val found: List<String> = emptyList(),
    val caps: List<String> = emptyList(),
    /** What is ticked per capability (containers, guests, units, MACs…). */
    val targets: Map<String, List<String>> = emptyMap(),
    /** What the agent found and could be ticked, per capability. */
    val choices: Map<String, List<String>> = emptyMap(),
    /** A friendlier name for a target (a guest's name, a MAC's address), per capability. */
    val labels: Map<String, Map<String, String?>> = emptyMap(),
    /** "service" (it can update itself), "container" (its image does), "" (too old to say). */
    val install: String = "",
    /** The release it can update itself to, or "". */
    val update: String = "",
    /** "images" ticked under Docker, so the unused images can be cleaned. */
    val prune: Boolean = false,
    /** The containers labhud updates on its own, in the window. */
    val auto: List<String> = emptyList(),
    /** The compose stacks it keeps (the `stacks` capability), by name. */
    val stacks: Map<String, StackInfo> = emptyMap(),
)

@Serializable
data class Agents(
    val agents: List<AgentRow> = emptyList(),
    /** Every capability an agent can have, in labhud's order. */
    val caps: List<String> = emptyList(),
    /** The capabilities that have targets, with their verbs. */
    val verbs: Map<String, List<String>> = emptyMap(),
    /** The agent release labhud pins. */
    val release: String = "",
)

/** /api/setup/alerts (admin): the rules as labhud keeps them (sent back as they are), and where news goes. */
@Serializable
data class AlertSetup(
    val rules: List<JsonObject> = emptyList(),
    val notify: NotifyChoice = NotifyChoice(),
    val ntfy: NtfyState = NtfyState(),
    val managed: Boolean = false,
)

@Serializable
data class NotifyChoice(val via: String = "both", val quiet: List<Int>? = null)

@Serializable
data class NtfyState(val set: Boolean = false, val host: String? = null, val sent: Int = 0,
                     @SerialName("last_error") val lastError: String? = null)

/** POST /api/setup/logs: a container's last lines. */
@Serializable
data class LogsAnswer(val ok: Boolean = false, val output: String = "", val error: String = "")

/** A generic action endpoint answer. */
@Serializable
data class ActionAnswer(
    val ok: Boolean = false,
    val error: String = "",
)

val LabJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
    encodeDefaults = true
}
