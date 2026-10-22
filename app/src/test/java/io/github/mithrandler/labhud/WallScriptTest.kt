package io.github.mithrandler.labhud

import io.github.mithrandler.labhud.wall.UNLOCK_KEY
import io.github.mithrandler.labhud.wall.unlockScript
import io.github.mithrandler.labhud.wall.wallAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WallScriptTest {

    @Test
    fun `the key is the one lock js reads`() = assertEquals("labhud-unlock", UNLOCK_KEY)

    @Test
    fun `no ticket, no script`() {
        assertNull(unlockScript(null))
        assertNull(unlockScript(""))
    }

    @Test
    fun `the ticket goes into sessionStorage under that key`() {
        val s = unlockScript("abc123")!!
        assertTrue(s.contains("sessionStorage.setItem(\"labhud-unlock\", \"abc123\")"))
    }

    @Test
    fun `a ticket cannot break out of the script`() {
        val s = unlockScript("x\"); alert(1); (\"")!!
        assertTrue(s.contains("\\\""))             // the quote is escaped
        assertTrue(!s.contains("\"x\"); alert"))
    }

    @Test
    fun `the glance view is a query on the wall page`() {
        assertEquals("https://lab:8095/?glance=1", wallAddress("https://lab:8095", true))
        assertEquals("https://lab:8095/", wallAddress("https://lab:8095/", false))
    }
}
