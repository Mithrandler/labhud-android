package io.github.mithrandler.labhud.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

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
