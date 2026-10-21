package io.github.mithrandler.labhud.ui

import androidx.compose.ui.graphics.Color
import io.github.mithrandler.labhud.data.Card
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

/** The colour of an uptime slice: up, partly down, down, off on purpose, not known. */
fun uptimeColor(v: Double?): Color = when {
    v == null -> Lab.line
    v < 0 -> Lab.muted.copy(alpha = 0.45f)
    v >= 0.999 -> Lab.green
    v >= 0.9 -> Lab.warn
    else -> Lab.red
}
