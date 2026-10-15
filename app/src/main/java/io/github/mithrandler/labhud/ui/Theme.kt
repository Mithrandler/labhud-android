package io.github.mithrandler.labhud.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** labhud's own palette (static/app.css): OLED black, quiet greys, status colours that never change. */
object Lab {
    val bg = Color(0xFF000000)
    val card = Color(0xFF121214)
    val block = Color(0xFF1C1C1F)
    val line = Color(0xFF2A2A2E)
    val border = Color(0xFF3A3D44)
    val text = Color(0xFFFFFFFF)
    val sub = Color(0xFFB4B8BF)
    val muted = Color(0xFF7D828A)
    val accent = Color(0xFF8AB4F8)
    val title = Color(0xFF7CC4FF)
    val green = Color(0xFF34C759)
    val warn = Color(0xFFFF9F0A)
    val red = Color(0xFFFF453A)
}

private val scheme = darkColorScheme(
    primary = Lab.accent,
    onPrimary = Color(0xFF001B3D),
    primaryContainer = Color(0xFF1B2B45),
    onPrimaryContainer = Lab.title,
    secondary = Lab.title,
    background = Lab.bg,
    onBackground = Lab.text,
    surface = Lab.bg,
    onSurface = Lab.text,
    surfaceVariant = Lab.card,
    onSurfaceVariant = Lab.sub,
    surfaceContainerLowest = Lab.bg,
    surfaceContainerLow = Lab.card,
    surfaceContainer = Lab.card,
    surfaceContainerHigh = Lab.block,
    surfaceContainerHighest = Lab.line,
    outline = Lab.border,
    outlineVariant = Lab.line,
    error = Lab.red,
    onError = Color.Black,
)

private val type = Typography().let { t ->
    t.copy(
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp),
        labelSmall = t.labelSmall.copy(letterSpacing = 0.8.sp),
    )
}

/** Section headings, like the wall's group titles: small capitals in the accent. */
val Heading = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, color = Lab.title)

@Composable
fun LabTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = type, content = content)
}

/** The colour of a status dot. */
fun statusColor(status: String?, bad: Boolean?): Color = when {
    bad == true || status == "down" -> Lab.red
    status == "up" -> Lab.green
    status == "scheduled" || status == "on-demand" || status == "maintenance" -> Lab.muted
    else -> Lab.border
}

fun sevColor(sev: String): Color = when (sev) {
    "crit" -> Lab.red
    "warn" -> Lab.warn
    else -> Lab.text
}
