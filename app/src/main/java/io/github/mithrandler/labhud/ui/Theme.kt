package io.github.mithrandler.labhud.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.mithrandler.labhud.data.BoardTheme
import io.github.mithrandler.labhud.data.ThemeMode

/** The colours the screens use, by role. [Hud] is one set of them; the other themes derive theirs from a Material scheme. */
class LabPalette(
    val bg: Color, val card: Color, val block: Color, val line: Color, val border: Color,
    val text: Color, val sub: Color, val muted: Color, val accent: Color, val title: Color, val onTitle: Color,
    val green: Color, val warn: Color, val red: Color, val redBg: Color, val redText: Color, val infoBg: Color,
)

private val hudPalette = LabPalette(
    Hud.bg, Hud.card, Hud.block, Hud.line, Hud.border, Hud.text, Hud.sub, Hud.muted, Hud.accent, Hud.title, Hud.onTitle,
    Hud.green, Hud.warn, Hud.red, Hud.redBg, Hud.redText, Hud.infoBg,
)

/** The palette now in force: one snapshot state, so a change of theme redraws whatever read [Lab]. */
private object Current {
    var palette by mutableStateOf(hudPalette)
    var hud by mutableStateOf(true)
}

/**
 * The colours of the app, by role (the names of the wall's tokens). They follow the chosen theme:
 * the HUD one is [Hud] (black, as the wall), the others come from the Material scheme (the wallpaper's
 * on Android 12+). Status colours stay green, orange and red in every theme.
 */
object Lab {
    /** The HUD theme (the wall's look) rather than a Material one: Material gets large bars and raised cards. */
    val hud: Boolean get() = Current.hud
    val bg: Color get() = Current.palette.bg
    val card: Color get() = Current.palette.card
    val block: Color get() = Current.palette.block
    val line: Color get() = Current.palette.line
    val border: Color get() = Current.palette.border
    val text: Color get() = Current.palette.text
    val sub: Color get() = Current.palette.sub
    val muted: Color get() = Current.palette.muted
    val accent: Color get() = Current.palette.accent
    val title: Color get() = Current.palette.title
    val onTitle: Color get() = Current.palette.onTitle
    val green: Color get() = Current.palette.green
    val warn: Color get() = Current.palette.warn
    val red: Color get() = Current.palette.red
    val redBg: Color get() = Current.palette.redBg
    val redText: Color get() = Current.palette.redText
    val infoBg: Color get() = Current.palette.infoBg
}

private val hudScheme = darkColorScheme(
    primary = Hud.accent,
    onPrimary = Color(0xFF001B3D),
    primaryContainer = Color(0xFF1B2B45),
    onPrimaryContainer = Hud.title,
    secondary = Hud.title,
    background = Hud.bg,
    onBackground = Hud.text,
    surface = Hud.bg,
    onSurface = Hud.text,
    surfaceVariant = Hud.card,
    onSurfaceVariant = Hud.sub,
    surfaceContainerLowest = Hud.bg,
    surfaceContainerLow = Hud.card,
    surfaceContainer = Hud.card,
    surfaceContainerHigh = Hud.block,
    surfaceContainerHighest = Hud.line,
    outline = Hud.border,
    outlineVariant = Hud.line,
    error = Hud.red,
    onError = Color.Black,
)

/** Dark and light without a wallpaper to take colours from (before Android 12): labhud's blue on neutral greys. */
private val fallbackDark = darkColorScheme(
    primary = Hud.accent, onPrimary = Color(0xFF00315F), primaryContainer = Color(0xFF1B2B45), onPrimaryContainer = Hud.title,
    secondary = Hud.title,
    background = Color(0xFF111317), onBackground = Color(0xFFE2E2E9), surface = Color(0xFF111317), onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF43474E), onSurfaceVariant = Color(0xFFC3C6CF),
    surfaceContainerLowest = Color(0xFF0C0E11), surfaceContainerLow = Color(0xFF191C20), surfaceContainer = Color(0xFF1D2024),
    surfaceContainerHigh = Color(0xFF282A2F), surfaceContainerHighest = Color(0xFF33353A),
    outline = Color(0xFF8D9099), outlineVariant = Color(0xFF43474E),
)

private val fallbackLight = lightColorScheme(
    primary = Color(0xFF0B57D0), onPrimary = Color.White, primaryContainer = Color(0xFFD8E2FF), onPrimaryContainer = Color(0xFF001A41),
    secondary = Color(0xFF565F71),
    background = Color(0xFFF9F9FF), onBackground = Color(0xFF1A1C20), surface = Color(0xFFF9F9FF), onSurface = Color(0xFF1A1C20),
    surfaceVariant = Color(0xFFE0E2EC), onSurfaceVariant = Color(0xFF44474E),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF3F3FA), surfaceContainer = Color(0xFFEDEDF4),
    surfaceContainerHigh = Color(0xFFE7E8EE), surfaceContainerHighest = Color(0xFFE2E2E9),
    outline = Color(0xFF74777F), outlineVariant = Color(0xFFC4C6D0),
)

/**
 * The HUD look as the board sets it: the default (oled, blue) is [hudScheme] and [hudPalette] as they
 * were; grey lifts the ground, cards and edges as the wall's "Dark grey"; the accent recolours the
 * accent, the titles and the primary button.
 */
private fun hudOf(board: BoardTheme): Pair<ColorScheme, LabPalette> {
    if (board == BoardTheme()) return hudScheme to hudPalette
    val (accent, title) = board.accentArgb.let { Color(it.first) to Color(it.second) }
    val bg = if (board.grey) Color(0xFF16181D) else Hud.bg
    val card = if (board.grey) Color(0xFF1E2128) else Hud.card
    val block = if (board.grey) Color(0xFF272B33) else Hud.block
    val line = if (board.grey) Color(0xFF333844) else Hud.line
    val border = if (board.grey) Color(0xFF454C59) else Hud.border
    val palette = LabPalette(bg, card, block, line, border, Hud.text, Hud.sub, Hud.muted, accent, title, Hud.onTitle,
        Hud.green, Hud.warn, Hud.red, Hud.redBg, Hud.redText, Hud.infoBg)
    val scheme = hudScheme.copy(
        primary = accent, onPrimaryContainer = title, secondary = title,
        background = bg, surface = bg, surfaceVariant = card, surfaceContainerLowest = bg,
        surfaceContainerLow = card, surfaceContainer = card, surfaceContainerHigh = block,
        surfaceContainerHighest = line, outline = border, outlineVariant = line,
    )
    return scheme to palette
}

/** The roles from a Material scheme; the status colours are labhud's own, darker on a light ground. */
private fun paletteOf(cs: ColorScheme, dark: Boolean) = LabPalette(
    bg = cs.background, card = cs.surfaceContainerLow, block = cs.surfaceContainerHigh, line = cs.outlineVariant, border = cs.outline,
    text = cs.onSurface, sub = cs.onSurfaceVariant, muted = cs.onSurfaceVariant.copy(alpha = 0.75f),
    accent = cs.primary, title = cs.primary, onTitle = cs.onPrimary,
    green = if (dark) Hud.green else Color(0xFF1E8E3E), warn = if (dark) Hud.warn else Color(0xFFB45309),
    red = if (dark) Hud.red else Color(0xFFD93025),
    redBg = if (dark) Hud.redBg else Color(0xFFFDE7EA), redText = if (dark) Hud.redText else Color(0xFF7A1020),
    infoBg = cs.surfaceContainer,
)

private val type = Typography().let { t ->
    t.copy(
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp),
        labelSmall = t.labelSmall.copy(letterSpacing = 0.8.sp),
    )
}

/** Section headings, like the wall's group titles: small capitals in the accent. */
val Heading: TextStyle get() = TextStyle(fontSize = LabScale.textXs, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, color = Lab.title)

/** Whether [mode] is a dark theme now (System follows the phone). */
@Composable
fun isDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.Dark, ThemeMode.Hud -> true
    ThemeMode.Light -> false
    ThemeMode.System -> isSystemInDarkTheme()
}

@Composable
fun LabTheme(mode: ThemeMode = ThemeMode.Hud, board: BoardTheme = BoardTheme(), content: @Composable () -> Unit) {
    val dark = isDarkTheme(mode)
    val context = LocalContext.current
    val (scheme, palette) = remember(mode, dark, context, board) {
        if (mode == ThemeMode.Hud) return@remember hudOf(board)
        val cs = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            dark -> fallbackDark
            else -> fallbackLight
        }
        cs to paletteOf(cs, dark)
    }
    if (Current.palette !== palette) Current.palette = palette
    if (Current.hud != (mode == ThemeMode.Hud)) Current.hud = mode == ThemeMode.Hud
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

/** A meter's bar: the accent until its warn mark, then orange, red past crit. */
fun barColor(sev: String): Color = when (sev) {
    "crit" -> Lab.red
    "warn" -> Lab.warn
    else -> Lab.accent
}
