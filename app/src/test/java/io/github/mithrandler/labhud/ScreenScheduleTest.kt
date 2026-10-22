package io.github.mithrandler.labhud

import io.github.mithrandler.labhud.data.Card
import io.github.mithrandler.labhud.data.LabState
import io.github.mithrandler.labhud.wall.ScreenSchedule
import io.github.mithrandler.labhud.wall.anyRed
import io.github.mithrandler.labhud.wall.minuteLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenScheduleTest {
    private val night = ScreenSchedule(enabled = true, from = 0, to = 7 * 60, dim = 5)

    @Test
    fun `the window may cross midnight`() {
        val s = ScreenSchedule(enabled = true, from = 22 * 60, to = 6 * 60)
        assertTrue(s.inWindow(23 * 60))
        assertTrue(s.inWindow(5 * 60 + 59))
        assertFalse(s.inWindow(6 * 60))
        assertFalse(s.inWindow(12 * 60))
        assertTrue(night.inWindow(0))
        assertFalse(night.inWindow(7 * 60))
    }

    @Test
    fun `equal ends mean no window`() {
        assertFalse(ScreenSchedule(enabled = true, from = 600, to = 600).inWindow(600))
    }

    @Test
    fun `dimmed inside the window, the system's brightness otherwise`() {
        val now = 1_000_000L
        assertEquals(0.05f, night.brightness(60, 0, now, false), 0.0001f)
        assertEquals(ScreenSchedule.SYSTEM, night.brightness(8 * 60, 0, now, false), 0f)
        assertEquals(ScreenSchedule.SYSTEM, night.copy(enabled = false).brightness(60, 0, now, false), 0f)
    }

    @Test
    fun `a touch wakes the screen for a minute and a red card for as long as it is red`() {
        val now = 1_000_000L
        assertEquals(ScreenSchedule.SYSTEM, night.brightness(60, now - 59_000, now, false), 0f)
        assertEquals(0.05f, night.brightness(60, now - 61_000, now, false), 0.0001f)
        assertEquals(ScreenSchedule.SYSTEM, night.brightness(60, 0, now, true), 0f)
    }

    @Test
    fun `red means a card whose status is red`() {
        assertFalse(anyRed(null))
        assertFalse(anyRed(LabState(cards = listOf(Card(id = "a", status = "orange"), Card(id = "b")))))
        assertTrue(anyRed(LabState(cards = listOf(Card(id = "a", status = "red")))))
    }

    @Test
    fun `minutes read as clock times`() {
        assertEquals("00:00", minuteLabel(0))
        assertEquals("07:05", minuteLabel(7 * 60 + 5))
    }
}
