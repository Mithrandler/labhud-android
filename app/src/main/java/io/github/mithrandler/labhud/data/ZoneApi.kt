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
    val pve: Boolean = false,
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

@Serializable
data class ZoneImage(
    val id: String = "", val tags: List<String> = emptyList(), val size: Long = 0, val created: Long = 0,
    val used: Int = 0, val dangling: Boolean = false,
    @SerialName("used_by") val usedBy: List<String> = emptyList(),
)

@Serializable
data class ZoneVolume(
    val name: String = "", val driver: String = "", val size: Long = -1, val used: Int = 0, val anonymous: Boolean = false,
    @SerialName("used_by") val usedBy: List<String> = emptyList(),
    /** Opened for browsing (read only) by the admin. */
    val browse: Boolean = false,
)

@Serializable
data class ZoneNetwork(
    val name: String = "", val driver: String = "", val scope: String = "", val used: Int = 0, val builtin: Boolean = false,
    @SerialName("used_by") val usedBy: List<String> = emptyList(),
)

@Serializable
data class ZoneLink(val name: String = "", val ipv4: String = "")

@Serializable
data class ZoneNetworkNow(val name: String = "", val driver: String = "", val scope: String = "", val internal: Boolean = false, val containers: List<ZoneLink> = emptyList())

@Serializable
data class ZoneEntry(val name: String = "", val dir: Boolean = false, val link: Boolean = false, val size: Long = 0, val mtime: Long = 0)

@Serializable
data class ZoneFile(val path: String = "", val size: Long = 0, val text: String = "", val binary: Boolean = false, val truncated: Boolean = false, val masked: Boolean = false)

@Serializable
data class ZoneStackSource(val repo: String = "", val branch: String = "main", val path: String = "")

@Serializable
data class ZoneStack(
    val name: String = "", val updated: Long = 0, val env: Boolean = false, val services: Int = 0, val running: Int = 0,
    val source: ZoneStackSource? = null, val hook: Boolean = false,
)

@Serializable
data class ZoneGuest(
    val id: String = "", val name: String = "", val type: String = "", val running: Boolean = false, val template: Boolean = false,
    val cpu: Double = 0.0, val mem: Double = 0.0, val uptime: Long = 0, val tags: List<String> = emptyList(),
    /** Ticked for this host: only then may it be opened and acted on. */
    val ticked: Boolean = false,
    /** The verbs it may be sent (start, shutdown, reboot). */
    val can: List<String> = emptyList(),
)

@Serializable
data class ZoneConfigLine(val key: String = "", val value: String = "")

@Serializable
data class ZoneSnapshot(val name: String = "", val description: String = "", val parent: String = "", val time: Long = 0, val vmstate: Boolean = false)

@Serializable
data class ZoneBackup(val storage: String = "", val volid: String = "", val size: Long = 0, val time: Long = 0, val format: String = "", val notes: String = "", val protected: Boolean = false)

@Serializable
data class ZoneTask(val type: String = "", val status: String = "", val user: String = "", val start: Long = 0, val end: Long = 0)

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
    val images: List<ZoneImage> = emptyList(),
    val volumes: List<ZoneVolume> = emptyList(),
    val networks: List<ZoneNetwork> = emptyList(),
    val network: ZoneNetworkNow? = null,
    val entries: List<ZoneEntry> = emptyList(),
    val path: String = "",
    val truncated: Boolean = false,
    val file: ZoneFile? = null,
    @SerialName("needs_access") val needsAccess: Boolean = false,
    val browse: Boolean = false,
    val enabled: Boolean = false,
    val stacks: List<ZoneStack> = emptyList(),
    val compose: String = "",
    val env: String = "",
    @SerialName("has_env") val hasEnv: Boolean = false,
    val versions: List<Long> = emptyList(),
    val mask: String = "********",
    val output: String = "",
    val node: String = "",
    val guests: List<ZoneGuest> = emptyList(),
    val config: List<ZoneConfigLine> = emptyList(),
    val snapshots: List<ZoneSnapshot> = emptyList(),
    val backups: List<ZoneBackup> = emptyList(),
    val tasks: List<ZoneTask> = emptyList(),
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
suspend fun LabApi.zoneImages(agent: String, fresh: Boolean = false) = zone("images", 60) { put("agent", agent); put("fresh", fresh) }
suspend fun LabApi.zoneVolumes(agent: String, fresh: Boolean = false) = zone("volumes", 60) { put("agent", agent); put("fresh", fresh) }
suspend fun LabApi.zoneNetworks(agent: String, fresh: Boolean = false) = zone("networks", 60) { put("agent", agent); put("fresh", fresh) }
suspend fun LabApi.zoneNetwork(agent: String, name: String) = zone("network", 35) { put("agent", agent); put("name", name) }
/** [kind] is image, volume or network; [confirm] is the name typed. */
suspend fun LabApi.zoneRm(agent: String, kind: String, name: String, confirm: String) =
    zone("rm", 70) { put("agent", agent); put("kind", kind); put("name", name); put("confirm", confirm) }
suspend fun LabApi.zonePull(agent: String, image: String) = zone("pull", 620) { put("agent", agent); put("image", image) }
suspend fun LabApi.zoneClean(agent: String, what: String, confirm: String) = zone("clean", 70) { put("agent", agent); put("what", what); put("confirm", confirm) }
suspend fun LabApi.zoneVolumeAccess(agent: String, volume: String, on: Boolean) = zone("volume-access", 35) { put("agent", agent); put("volume", volume); put("on", on) }
suspend fun LabApi.zoneVolumeLs(agent: String, volume: String, path: String) = zone("volume-ls", 45) { put("agent", agent); put("volume", volume); put("path", path) }
suspend fun LabApi.zoneVolumeRead(agent: String, volume: String, path: String) = zone("volume-read", 45) { put("agent", agent); put("volume", volume); put("path", path) }
suspend fun LabApi.zoneStacks(agent: String) = zone("stacks", 35) { put("agent", agent) }
suspend fun LabApi.zoneStackRead(agent: String, name: String, version: Long? = null) =
    zone("stack-read", 40) { put("agent", agent); put("name", name); if (version != null) put("version", version.toString()) }
suspend fun LabApi.zoneStackValidate(agent: String, name: String, compose: String, env: String) =
    zone("stack-validate", 130) { put("agent", agent); put("name", name); put("compose", compose); put("env", env) }
/** Writes the files after the host has checked them; it does not bring the stack up. */
suspend fun LabApi.zoneStackSave(agent: String, name: String, compose: String, env: String) =
    zone("stack-save", 130) { put("agent", agent); put("name", name); put("compose", compose); put("env", env) }
suspend fun LabApi.zoneStackRestore(agent: String, name: String, version: Long) =
    zone("stack-restore", 130) { put("agent", agent); put("name", name); put("version", version.toString()) }
/** [verb] is redeploy or restart. */
suspend fun LabApi.zoneStackAct(agent: String, name: String, verb: String) = zone("stack-act", 340) { put("agent", agent); put("name", name); put("verb", verb) }
suspend fun LabApi.zoneStackDown(agent: String, name: String, confirm: String) = zone("stack-down", 130) { put("agent", agent); put("name", name); put("confirm", confirm) }
suspend fun LabApi.zoneStackSource(agent: String, name: String, repo: String, branch: String, path: String) =
    zone("stack-source", 35) { put("agent", agent); put("name", name); put("repo", repo); put("branch", branch); put("path", path) }
suspend fun LabApi.zoneStackForget(agent: String, name: String) = zone("stack-source", 35) { put("agent", agent); put("name", name); put("clear", true) }
suspend fun LabApi.zoneStackHook(agent: String, name: String) = zone("stack-hook", 35) { put("agent", agent); put("name", name) }
suspend fun LabApi.zoneStackSync(agent: String, name: String, token: String) =
    zone("stack-sync", 340) { put("agent", agent); put("name", name); if (token.isNotEmpty()) put("token", token) }
suspend fun LabApi.zoneGuests(agent: String) = zone("guests", 35) { put("agent", agent) }
suspend fun LabApi.zoneGuestConfig(agent: String, id: String) = zone("guest-config", 45) { put("agent", agent); put("id", id) }
suspend fun LabApi.zoneGuestSnapshots(agent: String, id: String) = zone("guest-snapshots", 45) { put("agent", agent); put("id", id) }
suspend fun LabApi.zoneGuestBackups(agent: String, id: String) = zone("guest-backups", 45) { put("agent", agent); put("id", id) }
suspend fun LabApi.zoneGuestTasks(agent: String, id: String) = zone("guest-tasks", 45) { put("agent", agent); put("id", id) }
/** [verb] is start, shutdown or reboot. */
suspend fun LabApi.zoneGuestAct(agent: String, id: String, verb: String) = zone("guest-act", 60) { put("agent", agent); put("id", id); put("verb", verb) }
suspend fun LabApi.zoneSnapCreate(agent: String, id: String, name: String) = zone("snap-create", 340) { put("agent", agent); put("id", id); put("name", name) }
/** [confirm] is the guest's name. */
suspend fun LabApi.zoneSnapRollback(agent: String, id: String, name: String, confirm: String) =
    zone("snap-rollback", 340) { put("agent", agent); put("id", id); put("name", name); put("confirm", confirm) }
/** [confirm] is the snapshot's name. */
suspend fun LabApi.zoneSnapDelete(agent: String, id: String, name: String, confirm: String) =
    zone("snap-delete", 340) { put("agent", agent); put("id", id); put("name", name); put("confirm", confirm) }
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

// --- images, volumes and networks, pure ---

/** The name the admin types to remove it: its first tag, else its id. */
fun ZoneImage.title(): String = tags.firstOrNull() ?: id

/** "used by web, web2" / "unused": what is shown beside it. */
fun usedText(used: Int, by: List<String>): String = when {
    used <= 0 -> "unused"
    by.isEmpty() -> "used by $used"
    used > by.size -> "used by " + by.joinToString(", ") + " +${used - by.size}"
    else -> "used by " + by.joinToString(", ")
}

/** Biggest first, then by name; what matches the filter (any case) in a tag, a name or the id. */
fun List<ZoneImage>.filteredImages(query: String): List<ZoneImage> {
    val q = query.trim().lowercase()
    return filter { q.isEmpty() || it.id.lowercase().contains(q) || it.tags.any { t -> t.lowercase().contains(q) } }.sortedWith(compareBy({ -it.size }, { it.title() }))
}

fun List<ZoneVolume>.filteredVolumes(query: String): List<ZoneVolume> {
    val q = query.trim().lowercase()
    return filter { q.isEmpty() || it.name.lowercase().contains(q) }.sortedWith(compareBy({ -it.size }, { it.name }))
}

fun List<ZoneNetwork>.filteredNetworks(query: String): List<ZoneNetwork> {
    val q = query.trim().lowercase()
    return filter { q.isEmpty() || it.name.lowercase().contains(q) }.sortedBy { it.name }
}

/** What a volume's size says: "–" when the engine does not know it. */
fun volumeSize(size: Long): String = if (size < 0) "–" else bytesText(size.toDouble())

/** The path as steps for a breadcrumb: "/a/b" -> [("/", "/"), ("a", "/a"), ("b", "/a/b")]. */
fun crumbs(path: String): List<Pair<String, String>> {
    val parts = path.split('/').filter { it.isNotEmpty() }
    return listOf("/" to "/") + parts.indices.map { i -> parts[i] to "/" + parts.take(i + 1).joinToString("/") }
}

/** The directory above [path]; the root stays the root. */
fun parentOf(path: String): String = path.trimEnd('/').substringBeforeLast('/', "").ifEmpty { "/" }

/** A child of [dir] named [name]. */
fun childOf(dir: String, name: String): String = if (dir == "/") "/$name" else "$dir/$name"

/** A stack's name as the host takes it: lowercase letters, digits, - and _, up to 64. */
fun stackNameOk(name: String): Boolean = Regex("[a-z0-9][a-z0-9_-]{0,63}").matches(name)

/** Unsaved text in either file: the Save and Discard buttons are on only then. */
fun stackDirty(compose: String, compose0: String, env: String, env0: String): Boolean = compose != compose0 || env != env0

/** The row's second line: what runs of it, and where its file comes from. */
fun ZoneStack.line(): String = (if (services > 0) "$running / $services running" else "stopped") +
    (if (source != null) " · git" + (if (hook) " · webhook" else "") else "")

/** A saved version's id is its unix time: shown as year-month-day hour:minute in the phone's zone (no locale). */
fun versionText(unix: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): String =
    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", java.util.Locale.ROOT).format(java.time.Instant.ofEpochSecond(unix).atZone(zone))

// --- VMs & LXC, pure ---

/** A snapshot's name as Proxmox takes it: a letter first, then letters, digits, - and _, up to 40. */
fun snapNameOk(name: String): Boolean = Regex("[A-Za-z][A-Za-z0-9_-]{0,39}").matches(name)

/** What the admin types to confirm: a rollback endangers the guest, so its name; a delete the snapshot, so its name. */
fun snapConfirmWord(verb: String, guest: String, snapshot: String): String = if (verb == "rollback") guest else snapshot

/** "9 d", "5 h", "12 min": how long a guest has been up. */
fun upText(seconds: Long): String = when {
    seconds >= 86400 -> "${seconds / 86400} d"
    seconds >= 3600 -> "${seconds / 3600} h"
    else -> "${seconds / 60} min"
}

fun ZoneGuest.kind(): String = if (template) "template" else if (type == "lxc") "LXC" else "VM"

/** The row's second line: kind, then what it uses while it runs. */
fun ZoneGuest.line(): String = kind() + if (running) " · ${cpu.toInt()}% · ${mem.toInt()}% · ${upText(uptime)}" else " · stopped"

/** Running ones first, then by number; templates last. */
fun List<ZoneGuest>.ordered(): List<ZoneGuest> = sortedWith(compareBy({ it.template }, { !it.running }, { it.id.toLongOrNull() ?: 0L }))

/** The part of a backup's volume id that tells them apart: "vm/101/2026-10-22T02:00:03Z", not the whole "storage:backup/...". */
fun ZoneBackup.title(): String = volid.substringAfter("backup/", volid)
