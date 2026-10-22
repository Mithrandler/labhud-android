package io.github.mithrandler.labhud.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/* ⚙ > System > config.toml: whether LabHUD keeps the file itself ("taken over") or it is mounted from outside, taking it over, and the text
   of the file with its check and save (/api/setup/status, take-over, text, save-text). Secrets are not in this file (secrets.env). */

@Serializable
data class ConfigStatus(
    val ok: Boolean = true, val error: String = "", val managed: Boolean = false, val problem: String? = null, val mounted: String = "",
    @kotlinx.serialization.SerialName("can_take_over") val canTakeOver: Boolean = false,
)

@Serializable
data class ConfigText(val ok: Boolean = false, val error: String = "", val text: String = "", val path: String = "", val managed: Boolean = false, val writable: Boolean = false)

/** [problems] are the config's own complaints (one per line, as the wall shows them); empty when it is good. */
@Serializable
data class ConfigCheck(val ok: Boolean = false, val error: String = "", val problems: List<String> = emptyList(), val restart: Boolean = false)

suspend fun LabApi.configStatus(): ConfigStatus = LabJson.decodeFromString(postAnswer("/api/setup/status", "{}"))
suspend fun LabApi.configText(): ConfigText = LabJson.decodeFromString(postAnswer("/api/setup/text", "{}"))
suspend fun LabApi.takeOver(): ConfigCheck = LabJson.decodeFromString(postAnswer("/api/setup/take-over", "{}"))

/** [check] only asks whether the text is a good config; nothing is written. */
suspend fun LabApi.configSave(text: String, check: Boolean): ConfigCheck = LabJson.decodeFromString(
    postAnswer("/api/setup/save-text", buildJsonObject { put("text", text); if (check) put("check", true) }.toString()))

/** What the editor says after a check or a save: "" when there is nothing to say. */
fun configSaid(r: ConfigCheck, saved: Boolean): String = when {
    r.problems.isNotEmpty() -> r.problems.take(8).joinToString("\n") { "✕ $it" } + if (r.problems.size > 8) "\n… ${r.problems.size - 8} more" else ""
    !r.ok -> "✕ " + r.error.ifBlank { "refused" }
    saved -> "✓ Saved: LabHUD reads it now."
    else -> "✓ A good config."
}
