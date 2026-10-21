package io.github.mithrandler.labhud.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/* ⚙ for a phone signed in as the admin: a host's Docker (images, volumes, networks), compose stacks, live logs, and what labhud found and has no card for. The server is api/discover.py and server.py's setup routes. */

// --- the host's Docker (agent `inventory`, the cleanings) ---

@Serializable
data class ImageRow(val id: String = "", val tags: List<String> = emptyList(), val size: Long = 0, val created: Long = 0, val used: Int = 0, val dangling: Boolean = false)

@Serializable
data class VolumeRow(val name: String = "", val driver: String = "", val size: Long = -1, val used: Int = 0, val anonymous: Boolean = false)

@Serializable
data class NetworkRow(val name: String = "", val driver: String = "", val scope: String = "", val used: Int = 0, val builtin: Boolean = false)

@Serializable
data class InventoryTotals(val images: Long = 0, @SerialName("unused_images") val unusedImages: Long = 0, val volumes: Long = 0)

@Serializable
data class HostInventory(
    val ok: Boolean = false,
    val error: String = "",
    val images: List<ImageRow> = emptyList(),
    val volumes: List<VolumeRow> = emptyList(),
    val networks: List<NetworkRow> = emptyList(),
    val more: Map<String, Int> = emptyMap(),
    val totals: InventoryTotals = InventoryTotals(),
)

/** The kinds of cleaning, in the order ⚙ offers them: (what the server is sent, the button, what it does). */
val CLEANINGS = listOf(
    Triple("images", "Unused images", "Remove the images no container uses, unused for a day at least. An image a stopped container uses stays."),
    Triple("dangling", "Untagged layers", "Remove the image layers no tag points to."),
    Triple("networks", "Unused networks", "Remove the networks no container is on (a day old at least). The built-in ones stay."),
    Triple("volumes", "Anonymous volumes", "Remove the volumes with no name that no container uses. A volume with a name is never removed."),
)

suspend fun LabApi.inventory(agent: String, fresh: Boolean = false): HostInventory = LabJson.decodeFromString(
    postAnswer("/api/setup/inventory", buildJsonObject { put("agent", agent); put("fresh", fresh) }.toString()))

/** One cleaning ([what] from [CLEANINGS]): what it freed, in `output`. */
suspend fun LabApi.clean(agent: String, what: String): LogsAnswer = LabJson.decodeFromString(
    postAnswer("/api/setup/prune", buildJsonObject { put("agent", agent); put("what", what) }.toString()))

// --- compose stacks (the agent's `stacks` capability) ---

@Serializable
data class StackInfo(val updated: Long = 0, val env: Boolean = false, val services: Int = 0, val running: Int = 0)

@Serializable
data class StackRunAnswer(val ok: Boolean = false, val error: String = "", val output: String = "")

/** A compose file kept in a git repository: the server fetches [path] on [branch]; [token] (a private repo) is used once and never kept. */
data class GitSource(val repo: String, val branch: String = "main", val path: String = "", val token: String = "")

/** deploy, redeploy, restart, down or show. A deploy carries [compose] and [env]: null keeps the .env the host has, "" removes it.
 *  With [git] the server fetches the compose file from the repository instead (labhud 0.1.11+; [compose] is not sent). */
suspend fun LabApi.stackRun(agent: String, verb: String, name: String, compose: String? = null, env: String? = null, git: GitSource? = null): StackRunAnswer =
    LabJson.decodeFromString(slowAnswer("/api/setup/stack-run", stackRunBody(agent, verb, name, compose, env, git), 340))

/** The JSON of a stack-run request; a [git] source replaces [compose], and a token is sent only when there is one. */
fun stackRunBody(agent: String, verb: String, name: String, compose: String?, env: String?, git: GitSource?): String = buildJsonObject {
    put("agent", agent); put("verb", verb); put("name", name)
    if (git != null) put("git", buildJsonObject {
        put("repo", git.repo); put("branch", git.branch.ifBlank { "main" }); put("path", git.path)
        if (git.token.isNotEmpty()) put("token", git.token)
    }) else if (compose != null) put("compose", compose)
    if (env != null) put("env", env)
}.toString()

/** The compose file of a `show`, and the .env's names that follow it ("# .env: A, B"). */
fun splitShow(output: String): Pair<String, List<String>> {
    val at = output.lastIndexOf("\n# .env: ")
    if (at < 0) return output to emptyList()
    return output.substring(0, at) to output.substring(at + 9).trim().split(", ").filter { it.isNotEmpty() }
}

// --- live logs (the page and the app poll; the server holds each poll up to 20 s) ---

@Serializable
data class StreamOpened(val ok: Boolean = false, val error: String = "", val id: String = "")

@Serializable
data class StreamChunk(val ok: Boolean = false, val error: String = "", val data: String = "", val next: Int = 0, val end: String? = null)

suspend fun LabApi.openLiveLog(agent: String, container: String): StreamOpened = LabJson.decodeFromString(
    postAnswer("/api/setup/stream-open", buildJsonObject { put("agent", agent); put("kind", "logs"); put("target", container) }.toString()))

suspend fun LabApi.readStream(id: String, after: Int): StreamChunk = LabJson.decodeFromString(
    slowAnswer("/api/setup/stream-read", buildJsonObject { put("id", id); put("after", after) }.toString(), 35))

suspend fun LabApi.closeStream(id: String) {
    runCatching { postAnswer("/api/setup/stream-close", buildJsonObject { put("id", id) }.toString()) }
}

// --- discovered, to approve (api/discover.py) ---

@Serializable
data class Discovered(val key: String = "", val kind: String = "", val name: String = "", val detail: String = "")

@Serializable
data class DiscoverList(val ok: Boolean = false, val error: String = "", val items: List<Discovered> = emptyList(), val dismissed: Int = 0)

suspend fun LabApi.discovered(): DiscoverList = LabJson.decodeFromString(getText("/api/discover/list"))

suspend fun LabApi.discoverApprove(keys: List<String>): ActionAnswer = LabJson.decodeFromString(postAnswer("/api/discover/approve",
    buildJsonObject { put("keys", kotlinx.serialization.json.JsonArray(keys.map { JsonPrimitive(it) })) }.toString()))

suspend fun LabApi.discoverDismiss(keys: List<String>): ActionAnswer = LabJson.decodeFromString(postAnswer("/api/discover/dismiss",
    buildJsonObject { put("keys", kotlinx.serialization.json.JsonArray(keys.map { JsonPrimitive(it) })) }.toString()))

suspend fun LabApi.discoverRestore(): ActionAnswer = LabJson.decodeFromString(postAnswer("/api/discover/restore", "{}"))
