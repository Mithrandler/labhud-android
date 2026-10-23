// Written by labhud's design/build.py from its design/tokens.json (538e05e1d8d5): edit there, not here.
package io.github.mithrandler.labhud.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The colours of the wall and ⚙: the HUD theme. The app's `Lab` (Theme.kt) is these or, in another theme, others. */
object Hud {
    /** the background: pure black, the wall is an OLED screen that stays on */
    val bg = Color(0xFF000000)
    /** a card */
    val card = Color(0xFF121214)
    /** a block inside a card, a field */
    val block = Color(0xFF1C1C1F)
    /** dividers */
    val line = Color(0xFF2A2A2E)
    /** card edges: lighter than the background, so they stand apart */
    val border = Color(0xFF3A3D44)
    /** text */
    val text = Color(0xFFFFFFFF)
    /** secondary text */
    val sub = Color(0xFFB4B8BF)
    /** hints, labels */
    val muted = Color(0xFF7D828A)
    /** links, the open tab, sparklines */
    val accent = Color(0xFF8AB4F8)
    /** group titles, primary buttons */
    val title = Color(0xFF7CC4FF)
    /** text on a primary button */
    val onTitle = Color(0xFF000000)
    /** up, done */
    val green = Color(0xFF34C759)
    /** a warning */
    val warn = Color(0xFFFF9F0A)
    /** down, a problem */
    val red = Color(0xFFFF453A)
    /** behind a problem's text */
    val redBg = Color(0xFF3A0D1C)
    /** a problem's text on red_bg */
    val redText = Color(0xFFFFD9E2)
    /** behind a form being filled in */
    val infoBg = Color(0xFF0E1824)
}

/** The sizes of the wall and ⚙: gaps, corners, type, a meter's bar. */
object LabScale {
    /** the smallest gap: inside a row */
    val space1 = 4.dp
    /** between rows, a card's padding on the wall */
    val space2 = 8.dp
    /** a card's padding on a big screen, between cards */
    val space3 = 12.dp
    /** between sections */
    val space4 = 16.dp
    /** a card's corners */
    val radiusCard = 10.dp
    /** a bar, a chip, a field inside a card */
    val radiusInner = 6.dp
    /** labels, section titles (upper case) */
    val textXs = 11.sp
    /** rows, secondary text */
    val textSm = 12.sp
    /** a card's name, values in rows */
    val textMd = 14.sp
    /** a big number */
    val textLg = 18.sp
    /** the one number a card is about */
    val textXl = 24.sp
    /** the height of a meter's bar */
    val bar = 6.dp
    /** a card's service icon */
    val iconCard = 20.dp
    /** the diameter of a gauge ring on a machine card (smaller when the card is short) */
    val ring = 104.dp
    /** the thickness of a gauge ring */
    val ringStroke = 8.dp
    /** the number inside a gauge ring */
    val textRing = 14.sp
    /** the one number on a small tile */
    val textTile = 22.sp
    /** a small tile's name */
    val textTileName = 13.sp
    /** the one gap: between tiles and around them, on the wall */
    val wallGap = 8.dp
    /** a wall tile's least height (the rows share the screen, never less than this) */
    val wallTileMin = 92.dp
    /** a wall tile's greatest height */
    val wallTileMax = 132.dp
    /** tiles per row on the wall, landscape */
    val wallColsLandscape = 4.dp
    /** tiles per row on the wall, portrait */
    val wallColsPortrait = 3.dp
    /** the smallest text on a wall tile: read from 1-2 m */
    val textWallMin = 14.sp
    /** a wall tile's name */
    val textWallName = 17.sp
    /** the one number on a wall tile */
    val textWallNum = 30.sp
}

/** The icons of the wall and ⚙: thin lines on a 24×24 grid, tinted by Icon() like any other. */
object LabIcons {
    val look: ImageVector by lazy { icon("look", "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0Z" to false, "M12 3a9 9 0 0 0 0 18c1 0 1.5-.8 1.5-1.6 0-1.2-1-1.6-1-2.6 0-.9.7-1.6 1.6-1.6H16a5 5 0 0 0 5-5c0-4-4-7.2-9-7.2z" to false, "M6.5 11a1 1 0 1 0 2 0a1 1 0 1 0 -2 0Z" to false, "M9 7a1 1 0 1 0 2 0a1 1 0 1 0 -2 0Z" to false, "M13.5 7a1 1 0 1 0 2 0a1 1 0 1 0 -2 0Z" to false) }
    val pages: ImageVector by lazy { icon("pages", "M4.5 4H8.5A1.5 1.5 0 0 1 10 5.5V9.5A1.5 1.5 0 0 1 8.5 11H4.5A1.5 1.5 0 0 1 3 9.5V5.5A1.5 1.5 0 0 1 4.5 4Z" to false, "M15.5 4H19.5A1.5 1.5 0 0 1 21 5.5V9.5A1.5 1.5 0 0 1 19.5 11H15.5A1.5 1.5 0 0 1 14 9.5V5.5A1.5 1.5 0 0 1 15.5 4Z" to false, "M4.5 14H8.5A1.5 1.5 0 0 1 10 15.5V18.5A1.5 1.5 0 0 1 8.5 20H4.5A1.5 1.5 0 0 1 3 18.5V15.5A1.5 1.5 0 0 1 4.5 14Z" to false, "M15.5 14H19.5A1.5 1.5 0 0 1 21 15.5V18.5A1.5 1.5 0 0 1 19.5 20H15.5A1.5 1.5 0 0 1 14 18.5V15.5A1.5 1.5 0 0 1 15.5 14Z" to false) }
    val bell: ImageVector by lazy { icon("bell", "M6 16V11a6 6 0 0 1 12 0v5l1.5 2h-15z" to false, "M10 20.5a2 2 0 0 0 4 0" to false) }
    val devices: ImageVector by lazy { icon("devices", "M4.5 4H19.5A1.5 1.5 0 0 1 21 5.5V8.5A1.5 1.5 0 0 1 19.5 10H4.5A1.5 1.5 0 0 1 3 8.5V5.5A1.5 1.5 0 0 1 4.5 4Z" to false, "M4.5 14H19.5A1.5 1.5 0 0 1 21 15.5V18.5A1.5 1.5 0 0 1 19.5 20H4.5A1.5 1.5 0 0 1 3 18.5V15.5A1.5 1.5 0 0 1 4.5 14Z" to false, "M7 7h.01M7 17h.01M11 7h6M11 17h6" to false) }
    val security: ImageVector by lazy { icon("security", "M12 3l8 3v6c0 4.5-3.4 8.3-8 9-4.6-.7-8-4.5-8-9V6z" to false, "M9 12l2 2 4-4" to false) }
    val sources: ImageVector by lazy { icon("sources", "M5 6a7 2.5 0 1 0 14 0a7 2.5 0 1 0 -14 0Z" to false, "M5 6v6c0 1.4 3.1 2.5 7 2.5s7-1.1 7-2.5V6M5 12v6c0 1.4 3.1 2.5 7 2.5s7-1.1 7-2.5v-6" to false) }
    val advanced: ImageVector by lazy { icon("advanced", "M8 6l-5 6 5 6M16 6l5 6-5 6M14 4l-4 16" to false) }
    val setup: ImageVector by lazy { icon("setup", "M4 7h10M18 7h2M4 17h4M12 17h8" to false, "M14 7a2 2 0 1 0 4 0a2 2 0 1 0 -4 0Z" to false, "M8 17a2 2 0 1 0 4 0a2 2 0 1 0 -4 0Z" to false) }
    val lock: ImageVector by lazy { icon("lock", "M7 11H17A2 2 0 0 1 19 13V19A2 2 0 0 1 17 21H7A2 2 0 0 1 5 19V13A2 2 0 0 1 7 11Z" to false, "M8 11V7.5a4 4 0 0 1 8 0V11" to false, "M10.7 16a1.3 1.3 0 1 0 2.6 0a1.3 1.3 0 1 0 -2.6 0Z" to true) }
    val person: ImageVector by lazy { icon("person", "M8.4 8.5a3.6 3.6 0 1 0 7.2 0a3.6 3.6 0 1 0 -7.2 0Z" to false, "M4.8 20a7.2 6.4 0 0 1 14.4 0" to false) }
    val status: ImageVector by lazy { icon("status", "M4.5 3H9.5A1.5 1.5 0 0 1 11 4.5V11.5A1.5 1.5 0 0 1 9.5 13H4.5A1.5 1.5 0 0 1 3 11.5V4.5A1.5 1.5 0 0 1 4.5 3Z" to false, "M14.5 3H19.5A1.5 1.5 0 0 1 21 4.5V7.5A1.5 1.5 0 0 1 19.5 9H14.5A1.5 1.5 0 0 1 13 7.5V4.5A1.5 1.5 0 0 1 14.5 3Z" to false, "M14.5 11H19.5A1.5 1.5 0 0 1 21 12.5V19.5A1.5 1.5 0 0 1 19.5 21H14.5A1.5 1.5 0 0 1 13 19.5V12.5A1.5 1.5 0 0 1 14.5 11Z" to false, "M4.5 15H9.5A1.5 1.5 0 0 1 11 16.5V19.5A1.5 1.5 0 0 1 9.5 21H4.5A1.5 1.5 0 0 1 3 19.5V16.5A1.5 1.5 0 0 1 4.5 15Z" to false) }
    val activity: ImageVector by lazy { icon("activity", "M3 12h4l3-7 4 14 3-7h4" to false) }
    val settings: ImageVector by lazy { icon("settings", "M9.87 5.12L10.44 2.93L13.56 2.93L14.13 5.12A7.2 7.2 0 0 1 15.36 5.63L17.31 4.49L19.51 6.69L18.37 8.64A7.2 7.2 0 0 1 18.88 9.87L21.07 10.44L21.07 13.56L18.88 14.13A7.2 7.2 0 0 1 18.37 15.36L19.51 17.31L17.31 19.51L15.36 18.37A7.2 7.2 0 0 1 14.13 18.88L13.56 21.07L10.44 21.07L9.87 18.88A7.2 7.2 0 0 1 8.64 18.37L6.69 19.51L4.49 17.31L5.63 15.36A7.2 7.2 0 0 1 5.12 14.13L2.93 13.56L2.93 10.44L5.12 9.87A7.2 7.2 0 0 1 5.63 8.64L4.49 6.69L6.69 4.49L8.64 5.63A7.2 7.2 0 0 1 9.87 5.12Z" to false, "M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0Z" to false) }
    val more: ImageVector by lazy { icon("more", "M10.8 5a1.2 1.2 0 1 0 2.4 0a1.2 1.2 0 1 0 -2.4 0Z" to true, "M10.8 12a1.2 1.2 0 1 0 2.4 0a1.2 1.2 0 1 0 -2.4 0Z" to true, "M10.8 19a1.2 1.2 0 1 0 2.4 0a1.2 1.2 0 1 0 -2.4 0Z" to true) }
    val wall: ImageVector by lazy { icon("wall", "M4 9V4h5M15 4h5v5M20 15v5h-5M9 20H4v-5" to false) }
    val home: ImageVector by lazy { icon("home", "M4 11l8-7 8 7" to false, "M6 9.5V20h12V9.5" to false, "M10 20v-5h4v5" to false) }
    val network: ImageVector by lazy { icon("network", "M10 5a2 2 0 1 0 4 0a2 2 0 1 0 -4 0Z" to false, "M3 19a2 2 0 1 0 4 0a2 2 0 1 0 -4 0Z" to false, "M17 19a2 2 0 1 0 4 0a2 2 0 1 0 -4 0Z" to false, "M12 7v5M12 12l-5.5 5.5M12 12l5.5 5.5" to false) }
    val media: ImageVector by lazy { icon("media", "M5 5H19A2 2 0 0 1 21 7V17A2 2 0 0 1 19 19H5A2 2 0 0 1 3 17V7A2 2 0 0 1 5 5Z" to false, "M10 9v6l5-3z" to false) }
    val games: ImageVector by lazy { icon("games", "M7 8h10a4 4 0 0 1 4 4v2a3 3 0 0 1-5.4 1.8L14.5 14h-5l-1.1 1.8A3 3 0 0 1 3 14v-2a4 4 0 0 1 4-4z" to false, "M7.5 10.5v3M6 12h3" to false, "M14.7 11a0.8 0.8 0 1 0 1.6 0a0.8 0.8 0 1 0 -1.6 0Z" to true, "M16.7 13a0.8 0.8 0 1 0 1.6 0a0.8 0.8 0 1 0 -1.6 0Z" to true) }
    val discover: ImageVector by lazy { icon("discover", "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0Z" to false, "M7 12a5 5 0 1 0 10 0a5 5 0 1 0 -10 0Z" to false, "M12 12l6-6" to false, "M11 12a1 1 0 1 0 2 0a1 1 0 1 0 -2 0Z" to true) }
    val warning: ImageVector by lazy { icon("warning", "M12 3.5l9.5 16.5h-19z" to false, "M12 10v4.5" to false, "M11 17.2a1 1 0 1 0 2 0a1 1 0 1 0 -2 0Z" to true) }
    val offline: ImageVector by lazy { icon("offline", "M8.5 6.4A6 6 0 0 1 17.9 10.6 3.6 3.6 0 0 1 19.6 17M16 18H7a4 4 0 0 1-1.6-7.7 6 6 0 0 1 .7-1.6" to false, "M3 3l18 18" to false) }
    val back: ImageVector by lazy { icon("back", "M20 12H5M11 6l-6 6 6 6" to false) }
    val chevronRight: ImageVector by lazy { icon("chevron_right", "M9 6l6 6-6 6" to false) }
    val chevronDown: ImageVector by lazy { icon("chevron_down", "M6 9l6 6 6-6" to false) }
    val check: ImageVector by lazy { icon("check", "M5 12.5l4.5 4.5L19 7.5" to false) }
    val checkCircle: ImageVector by lazy { icon("check_circle", "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0Z" to false, "M8 12.5l2.7 2.7L16 10" to false) }
    val edit: ImageVector by lazy { icon("edit", "M4 20l1-4.5L15.5 5a2.1 2.1 0 0 1 3 3L8 18.5zM13.5 7l3 3" to false) }
    val delete: ImageVector by lazy { icon("delete", "M4 7h16M9.5 7V4.5h5V7M6.5 7l1 13h9l1-13M10 11v5.5M14 11v5.5" to false) }
    val add: ImageVector by lazy { icon("add", "M12 5v14M5 12h14" to false) }
    val open: ImageVector by lazy { icon("open", "M14 4h6v6M20 4l-9 9M18 14v5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h5" to false) }
    val start: ImageVector by lazy { icon("start", "M8 5v14l11-7z" to false) }
    val power: ImageVector by lazy { icon("power", "M12 3v8" to false, "M7.05 6.05a7 7 0 1 0 9.9 0" to false) }
    val restart: ImageVector by lazy { icon("restart", "M5 12a7 7 0 1 0 2.05-4.95L5 9" to false, "M5 4.5V9h4.5" to false) }
    val stop: ImageVector by lazy { icon("stop", "M7.5 6H16.5A1.5 1.5 0 0 1 18 7.5V16.5A1.5 1.5 0 0 1 16.5 18H7.5A1.5 1.5 0 0 1 6 16.5V7.5A1.5 1.5 0 0 1 7.5 6Z" to false) }
    val update: ImageVector by lazy { icon("update", "M12 4v11M7 10.5l5 5 5-5M5 20h14" to false) }
    val report: ImageVector by lazy { icon("report", "M4 20h16M7 16v-5M12 16V7M17 16v-8" to false) }
    val bolt: ImageVector by lazy { icon("bolt", "M13 3L5 13.5h6L10 21l8-10.5h-6z" to false) }
    val search: ImageVector by lazy { icon("search", "M5 11a6 6 0 1 0 12 0a6 6 0 1 0 -12 0Z" to false, "M20 20l-4.5-4.5" to false) }
    val note: ImageVector by lazy { icon("note", "M6 3h9l4 4v14H6z" to false, "M15 3v4h4" to false, "M9 12h7M9 16h5" to false) }
    val link: ImageVector by lazy { icon("link", "M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1" to false, "M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1" to false) }
    val server: ImageVector by lazy { icon("server", "M6 4H18A2 2 0 0 1 20 6V9A2 2 0 0 1 18 11H6A2 2 0 0 1 4 9V6A2 2 0 0 1 6 4Z" to false, "M6 13H18A2 2 0 0 1 20 15V18A2 2 0 0 1 18 20H6A2 2 0 0 1 4 18V15A2 2 0 0 1 6 13Z" to false, "M7 7.5a1 1 0 1 0 2 0a1 1 0 1 0 -2 0Z" to true, "M7 16.5a1 1 0 1 0 2 0a1 1 0 1 0 -2 0Z" to true) }
    val nas: ImageVector by lazy { icon("nas", "M7 3H17A2 2 0 0 1 19 5V19A2 2 0 0 1 17 21H7A2 2 0 0 1 5 19V5A2 2 0 0 1 7 3Z" to false, "M9 7h6M9 11h6" to false, "M10.5 16.5a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0 -3 0Z" to false) }
    val vm: ImageVector by lazy { icon("vm", "M5 4H19A2 2 0 0 1 21 6V14A2 2 0 0 1 19 16H5A2 2 0 0 1 3 14V6A2 2 0 0 1 5 4Z" to false, "M8 20h8M12 16v4" to false, "M8 8l3 2.5-3 2.5" to false) }
    val pc: ImageVector by lazy { icon("pc", "M5 4H19A2 2 0 0 1 21 6V14A2 2 0 0 1 19 16H5A2 2 0 0 1 3 14V6A2 2 0 0 1 5 4Z" to false, "M8 20h8M12 16v4" to false) }
    val app: ImageVector by lazy { icon("app", "M5.5 4H9.5A1.5 1.5 0 0 1 11 5.5V9.5A1.5 1.5 0 0 1 9.5 11H5.5A1.5 1.5 0 0 1 4 9.5V5.5A1.5 1.5 0 0 1 5.5 4Z" to false, "M14.5 4H18.5A1.5 1.5 0 0 1 20 5.5V9.5A1.5 1.5 0 0 1 18.5 11H14.5A1.5 1.5 0 0 1 13 9.5V5.5A1.5 1.5 0 0 1 14.5 4Z" to false, "M5.5 13H9.5A1.5 1.5 0 0 1 11 14.5V18.5A1.5 1.5 0 0 1 9.5 20H5.5A1.5 1.5 0 0 1 4 18.5V14.5A1.5 1.5 0 0 1 5.5 13Z" to false, "M14.5 13H18.5A1.5 1.5 0 0 1 20 14.5V18.5A1.5 1.5 0 0 1 18.5 20H14.5A1.5 1.5 0 0 1 13 18.5V14.5A1.5 1.5 0 0 1 14.5 13Z" to false) }
    val container: ImageVector by lazy { icon("container", "M12 3l8 4.5v9L12 21l-8-4.5v-9z" to false, "M4 7.5l8 4.5 8-4.5M12 12v9" to false) }
    val plug: ImageVector by lazy { icon("plug", "M9 3v5M15 3v5" to false, "M6 8h12v3a6 6 0 0 1-12 0z" to false, "M12 17v4" to false) }
    val phone: ImageVector by lazy { icon("phone", "M9 2.5H15A2 2 0 0 1 17 4.5V19.5A2 2 0 0 1 15 21.5H9A2 2 0 0 1 7 19.5V4.5A2 2 0 0 1 9 2.5Z" to false, "M11 18.5h2" to false) }
    val key: ImageVector by lazy { icon("key", "M4 15a4 4 0 1 0 8 0a4 4 0 1 0 -8 0Z" to false, "M11 12l9-9M16 7l3 3M14 9l2 2" to false) }
    val servers: ImageVector by lazy { icon("servers", "M12 3l8 4.5v9L12 21l-8-4.5v-9z" to false, "M12 12l8-4.5M12 12v9M12 12L4 7.5" to false) }
    val info: ImageVector by lazy { icon("info", "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0Z" to false, "M12 11v5.5" to false, "M12 7.8h.01" to false) }
    val close: ImageVector by lazy { icon("close", "M6 6l12 12M18 6L6 18" to false) }
    val chevronUp: ImageVector by lazy { icon("chevron_up", "M6 15l6-6 6 6" to false) }

    private fun icon(name: String, vararg paths: Pair<String, Boolean>): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            for ((d, filled) in paths) addPath(
                pathData = addPathNodes(d),
                fill = if (filled) SolidColor(Color.Black) else null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }.build()
}
