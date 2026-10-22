package io.github.mithrandler.labhud.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** One [[alert]] rule as ⚙ > Alerts edits it: a card id or "*", what it watches, the mark (not for offline and failed) and the minutes. */
data class AlertRule(val on: String = "*", val what: String = "cpu", val above: Int? = 90, val minutes: Int = 10) {
    fun json(): JsonObject = buildJsonObject {
        put("on", on); put("what", what)
        if (above != null) put("above", above)
        put("minutes", minutes)
    }
}

val ALERT_DEFAULT_MARK = mapOf("cpu" to 90, "mem" to 90, "disk" to 90, "temp" to 85)

/** The rule as the server sent it; a line that is not a rule is dropped. */
fun alertRule(o: JsonObject): AlertRule? {
    val on = (o["on"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
    val what = (o["what"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
    return AlertRule(on, what, o["above"]?.jsonPrimitive?.intOrNull, o["minutes"]?.jsonPrimitive?.intOrNull ?: 0)
}

/** A rule after the "what" changed: no mark where none is taken, the usual mark where one is needed. */
fun AlertRule.watching(what: String, noMark: Collection<String>) =
    copy(what = what, above = if (what in noMark) null else above ?: ALERT_DEFAULT_MARK[what] ?: 90, minutes = minutes)

/** The highest mark a rule may carry: a temperature in °C, else a percentage. */
fun alertMarkMax(what: String) = if (what == "temp") 150 else 100

/** "23:00 → 07:00", or "off" when the two hours are the same. */
fun quietText(from: Int, to: Int) = if (from == to) "off" else "%02d:00 → %02d:00".format(from, to)
