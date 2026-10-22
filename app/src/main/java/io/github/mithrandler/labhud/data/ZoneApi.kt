package io.github.mithrandler.labhud.data

import io.github.mithrandler.labhud.BuildConfig
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/* The zone Servers (api/servers.py): the hosts' Docker. Every call goes through labhud's own gate: HTTPS only, the admin's level, a session that ends after 15 minutes without a call. */

@Serializable
data class ZoneCount(val running: Int = 0, val total: Int = 0)

@Serializable
data class ZoneHost(
    val name: String = "",
    val online: Boolean = false,
    val version: String = "",
    val update: Boolean = false,
    val docker: Boolean = false,
    val containers: ZoneCount = ZoneCount(),
    val cpu: Double = 0.0,
    val mem: Double = 0.0,
)

@Serializable
data class ZoneContainer(
    val name: String = "",
    val state: String = "",
    val health: String = "",
    val image: String = "",
    val project: String = "",
    val cpu: Double = 0.0,
    val mem: Long = 0,
    @SerialName("mem_pct") val memPct: Double = 0.0,
    @SerialName("net_rx") val netRx: Long = 0,
    @SerialName("net_tx") val netTx: Long = 0,
    val restarts: Int = 0,
    val updating: Boolean = false,
    /** The verbs this container may be sent (empty when it is not ticked for them). */
    val can: List<String> = emptyList(),
    val shell: Boolean = false,
    val auto: Boolean = false,
    val hook: Boolean = false,
)

@Serializable
data class ZoneInspect(
    val image: String = "", val created: String = "", val started: String = "", val restart: String = "", val restarts: Int = 0,
    val ports: List<String> = emptyList(), val mounts: List<String> = emptyList(), val networks: List<String> = emptyList(),
    val env: List<String> = emptyList(), val labels: Map<String, String> = emptyMap(), val health: String = "", val command: String = "",
)

@Serializable
data class ZoneStats(
    val cpu: Double = 0.0, val mem: Double = 0.0,
    @SerialName("mem_limit") val memLimit: Double = 0.0,
    @SerialName("mem_pct") val memPct: Double = 0.0,
    @SerialName("net_rx") val netRx: Double = 0.0, @SerialName("net_tx") val netTx: Double = 0.0,
    @SerialName("block_read") val blockRead: Double = 0.0, @SerialName("block_write") val blockWrite: Double = 0.0,
    val pids: Double = 0.0,
)

/** One answer for every route: what a route does not send stays empty. [resume] is a session that ended: start it again with the PIN. */
@Serializable
data class ZoneAnswer(
    val ok: Boolean = false,
    val error: String = "",
    val resume: Boolean = false,
    @SerialName("retry_after") val retryAfter: Int = 0,
    val idle: Int = 0,
    val hosts: List<ZoneHost> = emptyList(),
    val online: Boolean = false,
    val containers: List<ZoneContainer> = emptyList(),
    val text: String = "",
    val inspect: ZoneInspect? = null,
    val stats: ZoneStats? = null,
    val id: String = "",
    val data: String = "",
    val next: Int = 0,
    val end: String? = null,
)

/** The words for a refusal: labhud's, or why a wait is asked. */
fun ZoneAnswer.refusal(): String = when {
    retryAfter > 0 -> "Too many refused calls: wait ${retryAfter} s."
    error.isNotBlank() -> error
    else -> "refused"
}

/** Servers speaks HTTPS only; a debug build may use plain HTTP (the emulator's demo server). */
fun zoneAllowed(url: String, debug: Boolean = BuildConfig.DEBUG): Boolean = url.startsWith("https://", ignoreCase = true) || debug

private suspend fun LabApi.zone(op: String, seconds: Long = 0, build: JsonObjectBuilder.() -> Unit = {}): ZoneAnswer {
    if (!zoneAllowed(baseUrl)) return ZoneAnswer(error = "Servers needs HTTPS: this server is added with http://.")
    return LabJson.decodeFromString(zoneAnswer("/api/servers/$op", buildJsonObject(build).toString(), seconds))
}

suspend fun LabApi.zoneSession() = zone("session")
suspend fun LabApi.zoneHosts() = zone("hosts")
suspend fun LabApi.zoneContainers(agent: String) = zone("containers") { put("agent", agent) }
suspend fun LabApi.zoneAction(agent: String, name: String, verb: String) = zone("action", 60) { put("agent", agent); put("name", name); put("verb", verb) }
suspend fun LabApi.zoneLogs(agent: String, name: String) = zone("logs", 35) { put("agent", agent); put("name", name) }
suspend fun LabApi.zoneInspect(agent: String, name: String) = zone("inspect", 35) { put("agent", agent); put("name", name) }
suspend fun LabApi.zoneStats(agent: String, name: String) = zone("stats", 35) { put("agent", agent); put("name", name) }
suspend fun LabApi.zoneOpen(agent: String, kind: String, name: String, cols: Int = 80, rows: Int = 24) =
    zone("stream-open") { put("agent", agent); put("kind", kind); put("name", name); put("cols", cols); put("rows", rows) }
suspend fun LabApi.zoneRead(id: String, after: Int) = zone("stream-read", 35) { put("id", id); put("after", after) }
suspend fun LabApi.zoneInput(id: String, base64: String) = zone("stream-input") { put("id", id); put("data", base64) }
suspend fun LabApi.zoneClose(id: String) { runCatching { zone("stream-close") { put("id", id) } } }

// --- what the screens make of it, pure ---

/** The state as it reads on a row: the health when it runs and has one, else the state. */
fun ZoneContainer.status(): String = if (state == "running" && health.isNotBlank()) health else state

/** 0 good, 1 attention (running but unhealthy or restarting), 2 down. */
fun ZoneContainer.tone(): Int = when {
    state != "running" -> 2
    health == "unhealthy" || updating -> 1
    else -> 0
}

/** The rows the filter keeps (name or image contains it, any case), the ones needing attention first. */
fun List<ZoneContainer>.filtered(query: String): List<ZoneContainer> {
    val q = query.trim().lowercase()
    return filter { q.isEmpty() || it.name.lowercase().contains(q) || it.image.lowercase().contains(q) }
        .sortedWith(compareBy({ -it.tone() }, { it.name.lowercase() }))
}

/** "3 / 14" for a host, "–" when it is offline. */
fun ZoneHost.line(): String = if (online) "${containers.running} / ${containers.total}" else "–"

/** 845 MB, 1.4 GB: base 1024, one decimal under 100. */
fun bytesText(n: Double): String {
    if (n <= 0) return "0"
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var v = n
    var i = 0
    while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
    val text = if (i == 0 || v >= 100) "%.0f".format(java.util.Locale.ROOT, v) else "%.1f".format(java.util.Locale.ROOT, v)
    return "$text ${units[i]}"
}

private val ANSI = Regex("\u001B\\[[0-9;?]*[ -/]*[@-~]|\u001B\\][^\u0007\u001B]*(\u0007|\u001B\\\\)")

/** A terminal's text for a plain screen: colour and cursor codes out, \r\n as \n, other control characters (but tab and newline) dropped. */
fun plainTerminal(text: String): String = ANSI.replace(text, "").replace("\r\n", "\n").replace("\r", "")
    .filter { it == '\n' || it == '\t' || it.code >= 32 && it.code != 127 }

/** The last [max] lines of [text]. */
fun tailLines(text: String, max: Int): String = text.lines().takeLast(max).joinToString("\n")

/** Decodes a stream's bytes as UTF-8 piece by piece: a character cut in two by a chunk waits for the rest. */
class Utf8Stream {
    private var pending = ByteArray(0)

    fun feed(bytes: ByteArray): String {
        val all = pending + bytes
        var cut = all.size
        var i = all.size - 1
        var back = 0
        while (i >= 0 && back < 3 && (all[i].toInt() and 0xC0) == 0x80) { i--; back++ }
        if (i >= 0 && (all[i].toInt() and 0x80) != 0) {
            val lead = all[i].toInt() and 0xFF
            val need = when { lead >= 0xF0 -> 4; lead >= 0xE0 -> 3; lead >= 0xC0 -> 2; else -> 1 }
            if (all.size - i < need) cut = i
        }
        pending = all.copyOfRange(cut, all.size)
        return String(all, 0, cut, Charsets.UTF_8)
    }
}
