package io.github.mithrandler.labhud.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/* ⚙ > Integrations: the sources LabHUD can set up from a form (/api/setup/catalog, try, source-save, source-forget) and the scan of the
   network for known services (scan, scan-status). Values are never in the catalog, only whether a source is set. */

@Serializable
data class SourceField(
    val name: String = "", val label: String = "", val secret: Boolean = false, val hint: String = "", val optional: Boolean = false,
)

@Serializable
data class SourceItem(
    val name: String = "", val title: String = "", val about: String = "", val fields: List<SourceField> = emptyList(),
    val configured: Boolean = false,
    /** The sources whose settings turn this one on: it has no form of its own. */
    @SerialName("with") val alsoWith: List<String> = emptyList(),
)

@Serializable
data class SourceCatalog(val ok: Boolean = false, val error: String = "", val sources: List<SourceItem> = emptyList())

/** try, source-save and source-forget: [restart] says LabHUD restarts to read new keys. */
@Serializable
data class SourceAnswer(val ok: Boolean = false, val error: String = "", val keys: List<String> = emptyList(), val card: String? = null, val restart: Boolean = false)

@Serializable
data class ScanFound(val source: String = "", val url: String = "", val configured: Boolean = false)

@Serializable
data class ScanStatus(
    val ok: Boolean = false, val error: String = "", val running: Boolean = false, val cidr: String = "", val done: Int = 0, val total: Int = 0,
    val found: List<ScanFound> = emptyList(), val default: String = "",
)

private fun valuesOf(values: Map<String, String>) = kotlinx.serialization.json.JsonObject(values.filterValues { it.isNotBlank() }.mapValues { JsonPrimitive(it.value.trim()) })

suspend fun LabApi.sourceCatalog(): SourceCatalog = LabJson.decodeFromString(postAnswer("/api/setup/catalog", "{}"))

/** One try with these values; a field left empty is tried with what is already set on the server. */
suspend fun LabApi.sourceTry(name: String, values: Map<String, String>): SourceAnswer = LabJson.decodeFromString(
    postAnswer("/api/setup/try", buildJsonObject { put("name", name); put("values", valuesOf(values)) }.toString()))

suspend fun LabApi.sourceSave(name: String, values: Map<String, String>): SourceAnswer = LabJson.decodeFromString(
    postAnswer("/api/setup/source-save", buildJsonObject { put("name", name); put("values", valuesOf(values)) }.toString()))

suspend fun LabApi.sourceForget(name: String): SourceAnswer = LabJson.decodeFromString(
    postAnswer("/api/setup/source-forget", buildJsonObject { put("name", name) }.toString()))

suspend fun LabApi.scanStatus(): ScanStatus = LabJson.decodeFromString(postAnswer("/api/setup/scan-status", "{}"))

suspend fun LabApi.scanStart(cidr: String): ScanStatus = LabJson.decodeFromString(
    postAnswer("/api/setup/scan", buildJsonObject { put("cidr", cidr.trim()) }.toString()))

/** "3 of 254 · 2 found" while a scan runs, "254 addresses · 2 found" after one, "" before any. */
fun scanText(s: ScanStatus): String = when {
    s.running -> "${s.done} of ${s.total} · ${s.found.size} found"
    s.total > 0 -> "${s.cidr} · ${s.found.size} found"
    else -> ""
}

/** A form's values may be saved when every field that is not optional and not secret has text (a secret left empty keeps the one set). */
fun sourceFormOk(item: SourceItem, values: Map<String, String>): Boolean =
    item.fields.filter { !it.optional && !it.secret }.all { !values[it.name].isNullOrBlank() }
