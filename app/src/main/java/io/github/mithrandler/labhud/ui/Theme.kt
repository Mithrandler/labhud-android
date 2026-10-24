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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
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
    onPrimary = Hud.onAccent,
    primaryContainer = FallbackDark.primaryContainer,
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
    onPrimary = FallbackDark.onPrimary,
    primaryContainer = FallbackDark.primaryContainer,
    background = FallbackDark.background,
    onBackground = FallbackDark.onBackground,
    surface = FallbackDark.surface,
    onSurface = FallbackDark.onSurface,
    surfaceVariant = FallbackDark.surfaceVariant,
    onSurfaceVariant = FallbackDark.onSurfaceVariant,
    surfaceContainerLowest = FallbackDark.surfaceContainerLowest,
    surfaceContainerLow = FallbackDark.surfaceContainerLow,
    surfaceContainer = FallbackDark.surfaceContainer,
    surfaceContainerHigh = FallbackDark.surfaceContainerHigh,
    surfaceContainerHighest = FallbackDark.surfaceContainerHighest,
    outline = FallbackDark.outline,
    outlineVariant = FallbackDark.outlineVariant,
    primary = Hud.accent, onPrimaryContainer = Hud.title, secondary = Hud.title,
)

private val fallbackLight = lightColorScheme(
    primary = FallbackLight.primary,
    onPrimary = FallbackLight.onPrimary,
    primaryContainer = FallbackLight.primaryContainer,
    onPrimaryContainer = FallbackLight.onPrimaryContainer,
    secondary = FallbackLight.secondary,
    background = FallbackLight.background,
    onBackground = FallbackLight.onBackground,
    surface = FallbackLight.surface,
    onSurface = FallbackLight.onSurface,
    surfaceVariant = FallbackLight.surfaceVariant,
    onSurfaceVariant = FallbackLight.onSurfaceVariant,
    surfaceContainerLowest = FallbackLight.surfaceContainerLowest,
    surfaceContainerLow = FallbackLight.surfaceContainerLow,
    surfaceContainer = FallbackLight.surfaceContainer,
    surfaceContainerHigh = FallbackLight.surfaceContainerHigh,
    surfaceContainerHighest = FallbackLight.surfaceContainerHighest,
    outline = FallbackLight.outline,
    outlineVariant = FallbackLight.outlineVariant,
)

/**
 * The HUD look as the board sets it: the default (oled, blue) is [hudScheme] and [hudPalette] as they
 * were; grey lifts the ground, cards and edges as the wall's "Dark grey"; the accent recolours the
 * accent, the titles and the primary button.
 */
private fun hudOf(board: BoardTheme): Pair<ColorScheme, LabPalette> {
    if (board == BoardTheme()) return hudScheme to hudPalette
    if (board.ownAccent) return ownBaseOf(board.base)
    val (accent, title) = board.accentArgb.let { Color(it.first) to Color(it.second) }
    val bg = if (board.grey) HudGrey.bg else Hud.bg
    val card = if (board.grey) HudGrey.card else Hud.card
    val block = if (board.grey) HudGrey.block else Hud.block
    val line = if (board.grey) HudGrey.line else Hud.line
    val border = if (board.grey) HudGrey.border else Hud.border
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

/** Paper, Terminal and Glass: a whole palette each (tokens.json), with its own accent; the Material scheme is made from it. */
private fun ownBaseOf(base: String): Pair<ColorScheme, LabPalette> {
    val (p, onAccent) = when (base) {
        "paper" -> LabPalette(HudPaper.bg, HudPaper.card, HudPaper.block, HudPaper.line, HudPaper.border, HudPaper.text, HudPaper.sub, HudPaper.muted,
            HudPaper.accent, HudPaper.title, HudPaper.onTitle, HudPaper.green, HudPaper.warn, HudPaper.red, HudPaper.redBg, HudPaper.redText, HudPaper.infoBg) to HudPaper.onAccent
        "terminal" -> LabPalette(HudTerminal.bg, HudTerminal.card, HudTerminal.block, HudTerminal.line, HudTerminal.border, HudTerminal.text, HudTerminal.sub, HudTerminal.muted,
            HudTerminal.accent, HudTerminal.title, HudTerminal.onTitle, HudTerminal.green, HudTerminal.warn, HudTerminal.red, HudTerminal.redBg, HudTerminal.redText, HudTerminal.infoBg) to HudTerminal.onAccent
        else -> LabPalette(HudGlass.bg, HudGlass.card, HudGlass.block, HudGlass.line, HudGlass.border, HudGlass.text, HudGlass.sub, HudGlass.muted,
            HudGlass.accent, HudGlass.title, HudGlass.onTitle, HudGlass.green, HudGlass.warn, HudGlass.red, HudGlass.redBg, HudGlass.redText, HudGlass.infoBg) to HudGlass.onAccent
    }
    // the card colour of Glass is translucent: the Material containers take it over the ground, flat
    fun flat(c: Color) = c.compositeOver(p.bg)
    val common = { cs: ColorScheme -> cs.copy(
        primary = p.accent, onPrimary = onAccent, onPrimaryContainer = p.title, secondary = p.title,
        background = p.bg, onBackground = p.text, surface = p.bg, onSurface = p.text,
        surfaceVariant = flat(p.card), onSurfaceVariant = p.sub, surfaceContainerLowest = p.bg,
        surfaceContainerLow = flat(p.card), surfaceContainer = flat(p.card), surfaceContainerHigh = flat(p.block),
        surfaceContainerHighest = flat(p.line), outline = flat(p.border), outlineVariant = flat(p.line),
        secondaryContainer = p.accent.copy(alpha = 0.22f).compositeOver(p.bg), onSecondaryContainer = p.text,
        error = p.red,
    ) }
    return (if (base == "paper") common(lightColorScheme()) else common(hudScheme)) to p
}

/** The roles from a Material scheme; the status colours are labhud's own, darker on a light ground. */
private fun paletteOf(cs: ColorScheme, dark: Boolean) = LabPalette(
    bg = cs.background, card = cs.surfaceContainerLow, block = cs.surfaceContainerHigh, line = cs.outlineVariant, border = cs.outline,
    text = cs.onSurface, sub = cs.onSurfaceVariant, muted = cs.onSurfaceVariant.copy(alpha = 0.75f),
    accent = cs.primary, title = cs.primary, onTitle = cs.onPrimary,
    green = if (dark) Hud.green else LightStatus.green, warn = if (dark) Hud.warn else LightStatus.warn,
    red = if (dark) Hud.red else LightStatus.red,
    redBg = if (dark) Hud.redBg else LightStatus.redBg, redText = if (dark) Hud.redText else LightStatus.redText,
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
fun LabTheme(mode: ThemeMode = ThemeMode.Hud, board: BoardTheme = BoardTheme(), textScale: Int = 100, mono: Boolean = false, content: @Composable () -> Unit) {
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
    val density = LocalDensity.current
    val scaled = remember(density, textScale) { Density(density.density, density.fontScale * textScaleOf(textScale) / 100f) }
    CompositionLocalProvider(LocalDensity provides scaled) {
        MaterialTheme(colorScheme = scheme, typography = if (mono || (mode == ThemeMode.Hud && board.mono)) monoType else type, content = content)
    }
}

private val monoType = type.let { t ->
    fun TextStyle.m() = copy(fontFamily = FontFamily.Monospace)
    t.copy(displayLarge = t.displayLarge.m(), displayMedium = t.displayMedium.m(), displaySmall = t.displaySmall.m(),
        headlineLarge = t.headlineLarge.m(), headlineMedium = t.headlineMedium.m(), headlineSmall = t.headlineSmall.m(),
        titleLarge = t.titleLarge.m(), titleMedium = t.titleMedium.m(), titleSmall = t.titleSmall.m(),
        bodyLarge = t.bodyLarge.m(), bodyMedium = t.bodyMedium.m(), bodySmall = t.bodySmall.m(),
        labelLarge = t.labelLarge.m(), labelMedium = t.labelMedium.m(), labelSmall = t.labelSmall.m())
}

/** The text size as the app keeps it: from 85 to 130 percent, in steps of 5 (anything else is moved to the nearest). */
fun textScaleOf(percent: Int): Int = ((percent.coerceIn(85, 130) + 2) / 5) * 5

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
