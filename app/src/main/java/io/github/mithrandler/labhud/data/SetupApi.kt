package io.github.mithrandler.labhud.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/* ⚙'s parts the app reached only as "Soon": registries and image scans (api/registries.py), the notification channels and which news goes where (api/notifications.py), backups (api/backup.py) and the Danger zone (api/security.py). */

// --- registries ---

@Serializable
data class Registry(val host: String = "", val user: String = "", val token: Boolean = false, val key: String = "", val containers: Int = 0)

@Serializable
data class Scanner(val agent: String = "", val on: Boolean = false, val online: Boolean = false)

@Serializable
data class ScanRow(val agent: String = "", val name: String = "", val image: String = "", val critical: Int = 0, val high: Int = 0, val tool: String = "", val t: Long = 0)

@Serializable
data class Registries(
    val ok: Boolean = false,
    val error: String = "",
    val managed: Boolean = false,
    val registries: List<Registry> = emptyList(),
    @SerialName("hold_critical") val holdCritical: Boolean = false,
    val scanners: List<Scanner> = emptyList(),
    val scans: List<ScanRow> = emptyList(),
)

@Serializable
data class HoldAnswer(val ok: Boolean = false, val error: String = "", @SerialName("hold_critical") val holdCritical: Boolean = false)

suspend fun LabApi.registries(): Registries = LabJson.decodeFromString(postAnswer("/api/registries/list", "{}"))

suspend fun LabApi.saveRegistry(host: String, user: String, token: String?): ActionAnswer = LabJson.decodeFromString(
    postAnswer("/api/registries/save", buildJsonObject {
        put("host", host); put("user", user)
        if (token != null && token.isNotBlank()) put("token", token)
    }.toString()))

suspend fun LabApi.removeRegistry(host: String): ActionAnswer = LabJson.decodeFromString(
    postAnswer("/api/registries/remove", buildJsonObject { put("host", host) }.toString()))

suspend fun LabApi.holdCritical(on: Boolean): HoldAnswer = LabJson.decodeFromString(
    postAnswer("/api/registries/hold", buildJsonObject { put("on", on) }.toString()))

// --- notification channels ---

@Serializable
data class Channel(
    val set: Boolean = false,
    val problem: String? = null,
    val sent: Int? = null,
    @SerialName("last_sent") val lastSent: Long? = null,
    @SerialName("last_error") val lastError: String? = null,
    @SerialName("last_error_at") val lastErrorAt: Long? = null,
    /** email: the SMTP server; ntfy, webhook, discord: the address's host name */
    val host: String? = null,
    val port: Int? = null,
    val security: String? = null,
    val user: String? = null,
    @SerialName("from") val from: String? = null,
    /** email: the addresses */
    val to: List<String>? = null,
    /** email: an SMTP password is saved */
    val password: Boolean = false,
    /** webhook: an Authorization header is saved */
    val auth: Boolean = false,
    /** gotify: its address */
    val url: String? = null,
    /** gotify: an app token is saved */
    val token: Boolean = false,
)

@Serializable
data class Channels(
    val ok: Boolean = false,
    val error: String = "",
    val managed: Boolean = false,
    val channels: Map<String, Channel> = emptyMap(),
    val order: List<String> = emptyList(),
    /** [[event id, its text]], e.g. ["host_down", "A host down"] */
    val events: List<List<String>> = emptyList(),
    /** event id -> the channels that get it */
    val matrix: Map<String, List<String>> = emptyMap(),
    val explicit: Boolean = false,
    val always: List<String> = emptyList(),
)

@Serializable
data class EventsAnswer(val ok: Boolean = false, val error: String = "", val matrix: Map<String, List<String>> = emptyMap())

internal fun channelBody(channel: String, config: Map<String, String>, secrets: Map<String, String>): String =
    buildJsonObject {
        put("channel", channel)
        if (config.isNotEmpty()) {
            putJsonObject("config") {
                config.forEach { (k, v) ->
                    if (k == "port") v.toIntOrNull()?.let { put(k, it) }
                    else put(k, v)
                }
            }
        }
        if (secrets.isNotEmpty()) {
            putJsonObject("secrets") { secrets.forEach { (k, v) -> put(k, v) } }
        }
    }.toString()

suspend fun LabApi.channels(): Channels = LabJson.decodeFromString(postAnswer("/api/notify/channels", "{}"))

suspend fun LabApi.saveChannel(channel: String, config: Map<String, String>, secrets: Map<String, String>): ActionAnswer =
    LabJson.decodeFromString(postAnswer("/api/notify/save", channelBody(channel, config, secrets)))

suspend fun LabApi.forgetChannel(channel: String): ActionAnswer = LabJson.decodeFromString(
    postAnswer("/api/notify/save", buildJsonObject { put("channel", channel); put("forget", true) }.toString()))

suspend fun LabApi.testChannel(channel: String, config: Map<String, String>, secrets: Map<String, String>): ActionAnswer =
    LabJson.decodeFromString(postAnswer("/api/notify/test", channelBody(channel, config, secrets)))

suspend fun LabApi.saveEvents(matrix: Map<String, List<String>>): EventsAnswer = LabJson.decodeFromString(
    postAnswer("/api/notify/events", buildJsonObject {
        putJsonObject("events") { matrix.forEach { (event, ch) -> putJsonArray(event) { ch.forEach { add(JsonPrimitive(it)) } } } }
    }.toString()))

// --- backups ---

@Serializable
data class BackupFile(val name: String = "", val kind: String = "", val size: Long = 0, val t: Long = 0, val encrypted: Boolean = false)

@Serializable
data class BackupSettings(val daily: Boolean = false, val keep: Int = 7, val at: Int = 3)

@Serializable
data class BackupLast(val t: Long = 0, val ok: Boolean = false, val kind: String = "", val name: String = "", val error: String = "")

@Serializable
data class Backups(
    val ok: Boolean = false,
    val error: String = "",
    val problem: String? = null,
    val encryption: Boolean = false,
    @SerialName("password_set") val passwordSet: Boolean = false,
    val settings: BackupSettings = BackupSettings(),
    val last: BackupLast? = null,
    val backups: List<BackupFile> = emptyList(),
)

@Serializable
data class BackupMade(val ok: Boolean = false, val error: String = "", val name: String = "", val encrypted: Boolean = false)

@Serializable
data class BackupData(val ok: Boolean = false, val error: String = "", val name: String = "", val size: Long = 0, val data: String = "")

@Serializable
data class RestoreAnswer(val ok: Boolean = false, val error: String = "", val before: String = "", val restart: Boolean = false)

suspend fun LabApi.backups(): Backups = LabJson.decodeFromString(postAnswer("/api/backup/list", "{}"))

suspend fun LabApi.makeBackup(secrets: Boolean, password: String): BackupMade = LabJson.decodeFromString(
    postAnswer("/api/backup/create", buildJsonObject { put("secrets", secrets); put("password", password) }.toString()))

suspend fun LabApi.saveBackupSettings(daily: Boolean, keep: Int, at: Int): ActionAnswer = LabJson.decodeFromString(
    postAnswer("/api/backup/settings", buildJsonObject { put("daily", daily); put("keep", keep); put("at", at) }.toString()))

suspend fun LabApi.downloadBackup(name: String): BackupData = LabJson.decodeFromString(
    postAnswer("/api/backup/download", buildJsonObject { put("name", name) }.toString()))

suspend fun LabApi.deleteBackup(name: String): ActionAnswer = LabJson.decodeFromString(
    postAnswer("/api/backup/delete", buildJsonObject { put("name", name) }.toString()))

suspend fun LabApi.restoreBackup(name: String, password: String): RestoreAnswer = LabJson.decodeFromString(
    postAnswer("/api/backup/restore", buildJsonObject { put("name", name); put("password", password); put("word", "RESTORE") }.toString()))

// --- danger zone ---

@Serializable
data class DangerAnswer(val ok: Boolean = false, val error: String = "", val agents: Int = 0, val phones: Int = 0)

suspend fun LabApi.newKeys(): DangerAnswer = LabJson.decodeFromString(
    postAnswer("/api/security/new-keys", buildJsonObject { put("confirm", "NEW KEYS") }.toString()))

suspend fun LabApi.removePhones(): DangerAnswer = LabJson.decodeFromString(
    postAnswer("/api/security/remove-phones", buildJsonObject { put("confirm", "REMOVE") }.toString()))
