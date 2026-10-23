package io.github.mithrandler.labhud.ui

import androidx.compose.ui.graphics.Color
import io.github.mithrandler.labhud.data.Card
import io.github.mithrandler.labhud.data.Guest
import io.github.mithrandler.labhud.data.Metric
import java.util.Locale

/**
 * What the phone calls a host's resources: CPU, RAM, GPU, temperatures and storage, the numbers
 * with a history. Throughput (rate) and shares that are not a machine's (DNS blocked, packet loss)
 * stay on their cards.
 */
private val RESOURCE = Regex("(?i)cpu|ram|mem|gpu|disk|storage|swap|used|temp|load|vram")

fun Metric.isResource(): Boolean = trend && when (format) {
    "celsius", "used_of" -> true
    "percent", "percent1", "" -> RESOURCE.containsMatchIn(label) || RESOURCE.containsMatchIn(key.substringAfterLast('.'))
    else -> false
}

fun Card.resources(): List<Metric> = metrics.filter { it.isResource() }

/** Shares on a fixed 0..100 scale (so 12% does not look like a mountain); temperatures on their own. */
fun Metric.isShare(): Boolean = format != "celsius"

fun Metric.color(): Color = if (format == "celsius") Lab.warn else Lab.accent

/** A history value in the metric's unit: used_of is kept as a percentage. */
fun Metric.show(v: Double?): String = when {
    v == null -> "—"
    format == "celsius" -> String.format(Locale.ROOT, "%.0f°", v)
    format == "percent1" -> String.format(Locale.ROOT, "%.1f%%", v)
    isShare() -> String.format(Locale.ROOT, "%.0f%%", v)
    else -> String.format(Locale.ROOT, "%.1f", v)
}

/** The short name on a tile: "CPU", "RAM", "GPU0"… with ° telling a temperature from a load. */
fun Metric.short(): String = label.ifEmpty { key.substringAfterLast('.') }

private fun kindOf(m: Metric): String? {
    val t = (m.label + " " + m.key).lowercase()
    return listOf("cpu", "gpu").firstOrNull { Regex("\\b$it").containsMatchIn(t) }
}

/** The worse of two severities: crit, then warn, then none. */
fun worseSev(a: String, b: String): String = listOf("crit", "warn").firstOrNull { it == a || it == b } ?: ""

/**
 * The rings of a card: every percentage, with the temperature of the same part (a CPU's or a GPU's, in
 * celsius) folded into it; the other numbers stay as chips.
 */
fun ringsAndChips(shown: List<Metric>): Pair<List<Pair<Metric, Metric?>>, List<Metric>> {
    val pcts = shown.filter { it.pct != null }
    val temps = shown.filter { it.pct == null && it.format == "celsius" }
    val used = mutableSetOf<Metric>()
    val rings = pcts.map { p ->
        val k = kindOf(p)
        val t = if (k == null) null else temps.firstOrNull { it !in used && kindOf(it) == k }
        if (t != null) used += t
        p to t
    }
    return rings to shown.filter { it.pct == null && it !in used }
}

/** The colour of an uptime slice: up, partly down, down, off on purpose, not known. */
fun uptimeColor(v: Double?): Color = when {
    v == null -> Lab.line
    v < 0 -> Lab.muted.copy(alpha = 0.45f)
    v >= 0.999 -> Lab.green
    v >= 0.9 -> Lab.warn
    else -> Lab.red
}

/**
 * The bars of a guest card: CPU, RAM, disk in percent. CPU and disk are orange from 80 and red from 92 (the server's default
 * marks); RAM is coloured only by [memSev], as on the wall (the % includes the page cache, so 95% is normal), and not at all
 * from a server that does not send it.
 */
fun Guest.meters(): List<Metric> = listOf("CPU" to cpu, "RAM" to mem, "Disk" to disk).mapNotNull { (label, v) ->
    v?.let {
        val sev = if (label == "RAM") memSev.orEmpty() else if (it >= 92) "crit" else if (it >= 80) "warn" else ""
        Metric(key = "guest.$label", label = label, value = "${Math.round(it)}%", pct = it, format = "percent", sev = sev)
    }
}

/** "7d", "5h", "12m": how long a guest has run, as short as the wall's. */
fun upText(seconds: Long): String = when {
    seconds >= 86400 -> "${seconds / 86400}d"
    seconds >= 3600 -> "${seconds / 3600}h"
    else -> "${seconds / 60}m"
}

/**
 * A week of uptime as one share a day, oldest first: the mean of the slices that were up or down (0..1),
 * -1.0 for a day labhud knew only as off on purpose, null for a day it knows nothing about.
 */
fun dayShares(values: List<Double?>, days: Int = 7): List<Double?> = (0 until days).map { d ->
    val from = values.size * d / days
    val to = values.size * (d + 1) / days
    val known = values.subList(from, to).filterNotNull()
    when {
        known.isEmpty() -> null
        known.all { it < 0 } -> -1.0
        else -> known.filter { it >= 0 }.average()
    }
}
