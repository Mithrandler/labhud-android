package io.github.mithrandler.labhud.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

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
)

/** A single metric on a card. */
@Serializable
data class Metric(
    val key: String = "",
    val label: String = "",
    val value: String = "",
    val sev: String = "",
    val trend: Boolean = false,
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
)

fun JsonArray.toRow(): Row = Row(
    name = getOrNull(0)?.jsonPrimitive?.contentOrNull ?: "",
    value = getOrNull(1)?.jsonPrimitive?.contentOrNull ?: "",
    bad = getOrNull(2)?.jsonPrimitive?.booleanOrNull ?: false,
    quiet = getOrNull(3)?.jsonPrimitive?.booleanOrNull ?: false,
)

/** A section (panel) on a card. */
@Serializable
data class Section(
    val title: String = "",
    @SerialName("rows") val rawRows: List<JsonArray> = emptyList(),
) {
    val rows: List<Row> get() = rawRows.map { it.toRow() }
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
) {
    val rows: List<Row> get() = rawRows.map { it.toRow() }
}

/** An alert entry. */
@Serializable
data class Alert(
    val id: String = "",
    val text: String = "",
)

/** The full lab state document. */
@Serializable
data class LabState(
    val title: String = "LabHUD",
    val now: Long = 0,
    val pages: List<Page> = emptyList(),
    val cards: List<Card> = emptyList(),
    val problems: List<String> = emptyList(),
    val alerts: List<Alert> = emptyList(),
) {
    val byId: Map<String, Card> get() = cards.associateBy { it.id }
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
