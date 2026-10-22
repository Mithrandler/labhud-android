package io.github.mithrandler.labhud

import io.github.mithrandler.labhud.data.LabJson
import io.github.mithrandler.labhud.data.LabState
import io.github.mithrandler.labhud.ui.ViewChoice
import io.github.mithrandler.labhud.ui.ViewMode
import io.github.mithrandler.labhud.ui.buttons
import io.github.mithrandler.labhud.ui.effectiveView
import io.github.mithrandler.labhud.ui.wallGlance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewModeTest {
    @Test
    fun `auto follows the board's view for the role and falls back to full`() {
        assertEquals(ViewMode.Glance, effectiveView(ViewChoice.Auto, "glance"))
        assertEquals(ViewMode.Actions, effectiveView(ViewChoice.Auto, "actions"))
        assertEquals(ViewMode.Full, effectiveView(ViewChoice.Auto, ""))
        assertEquals(ViewMode.Full, effectiveView(ViewChoice.Auto, "huge"))
        assertEquals(ViewMode.Full, effectiveView(ViewChoice.Auto, null))
    }

    @Test
    fun `the phone's own choice wins over the board`() {
        assertEquals(ViewMode.Full, effectiveView(ViewChoice.Full, "glance"))
        assertEquals(ViewMode.Glance, effectiveView(ViewChoice.Glance, "full"))
    }

    @Test
    fun `glance hides the buttons`() {
        assertFalse(ViewMode.Glance.buttons())
        assertTrue(ViewMode.Actions.buttons())
        assertTrue(ViewMode.Full.buttons())
    }

    @Test
    fun `the wall page is glance by its own switch or by the board's view`() {
        assertTrue(wallGlance(true, ""))
        assertTrue(wallGlance(false, "glance"))
        assertFalse(wallGlance(false, "actions"))
        assertFalse(wallGlance(false, null))
    }

    @Test
    fun `the state carries the view, an older server none`() {
        assertEquals("glance", LabJson.decodeFromString<LabState>("""{"role":"viewer","view":"glance"}""").view)
        assertEquals("", LabJson.decodeFromString<LabState>("""{"role":"viewer"}""").view)
    }
}
