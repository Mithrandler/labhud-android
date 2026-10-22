package io.github.mithrandler.labhud.wall

import io.github.mithrandler.labhud.data.LabState

/**
 * Wall mode's screen schedule: between [from] and [to] (minutes since midnight, the window may
 * cross midnight) the screen is dimmed to [dim] percent. A touch wakes it for [WAKE_MS], and so
 * does a red card, for as long as one is red.
 */
data class ScreenSchedule(
    val enabled: Boolean = false,
    val from: Int = 0,
    val to: Int = 7 * 60,
    val dim: Int = 5,
) {
    /** True when [minute] (0..1439) falls in the window; from == to means no window. */
    fun inWindow(minute: Int): Boolean = when {
        from == to -> false
        from < to -> minute in from until to
        else -> minute >= from || minute < to
    }

    /**
     * The window brightness to use now, as the 0..1 `screenBrightness` of the window, or -1f for
     * "the system's own" (outside the window, awake after a touch, or a red card).
     */
    fun brightness(minute: Int, touchedAt: Long, now: Long, red: Boolean): Float = when {
        !enabled || !inWindow(minute) || red || now - touchedAt < WAKE_MS -> SYSTEM
        else -> dim.coerceIn(0, 100) / 100f
    }

    companion object {
        const val WAKE_MS = 60_000L
        const val SYSTEM = -1f
        /** 0.01 is the lowest value a window accepts without turning the screen off. */
        const val MIN = 0.01f
    }
}

/** A red card in the state: what wakes the screen. */
fun anyRed(state: LabState?): Boolean = state?.cards?.any { it.status == "red" } == true

/** "HH:MM" for a minute of the day. */
fun minuteLabel(m: Int): String = "%02d:%02d".format(m / 60, m % 60)
