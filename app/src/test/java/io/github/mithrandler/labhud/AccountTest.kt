package io.github.mithrandler.labhud

import io.github.mithrandler.labhud.data.AdminSession
import io.github.mithrandler.labhud.data.LabJson
import io.github.mithrandler.labhud.data.Rights
import io.github.mithrandler.labhud.data.SessionsAnswer
import io.github.mithrandler.labhud.data.Info
import io.github.mithrandler.labhud.data.Agents
import io.github.mithrandler.labhud.ui.isRefusal
import io.github.mithrandler.labhud.ui.refusalText
import io.github.mithrandler.labhud.ui.settings.accountSummary
import io.github.mithrandler.labhud.ui.settings.sessionLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountTest {

    @Test
    fun `forbidden says what to do for the rights this phone has`() {
        assertEquals("This phone only looks. Sign in as the admin in Settings > Account.", refusalText("forbidden", Rights()))
        assertEquals("Locked. Unlock with the PIN, then try again.", refusalText("", Rights(admin = true, pin = true)))
        assertTrue(refusalText("forbidden", Rights(admin = true)).contains("Sign in again"))
        assertEquals("wrong PIN", refusalText("wrong PIN", Rights(admin = true, pin = true)))   // a real reason is kept
    }

    @Test
    fun `an empty or forbidden error is a refusal that reads the rights again`() {
        assertTrue(isRefusal(""))
        assertTrue(isRefusal("forbidden"))
        assertFalse(isRefusal("no agent"))
    }

    @Test
    fun `the user is read from info and a server without it still parses`() {
        assertEquals("boss", LabJson.decodeFromString<Info>("""{"api":1,"device_admin":true,"user":"boss"}""").user)
        assertEquals(null, LabJson.decodeFromString<Info>("""{"api":1,"user":null}""").user)
        assertEquals(null, LabJson.decodeFromString<Info>("""{"api":1}""").user)
    }

    @Test
    fun `the account line`() {
        assertEquals("Only looking", accountSummary(false, "boss"))
        assertEquals("Signed in as boss", accountSummary(true, "boss"))
        assertEquals("Signed in as the admin", accountSummary(true, ""))
    }

    @Test
    fun `a refused agents list is not an empty one`() {
        val a = LabJson.decodeFromString<Agents>("""{"ok":false,"error":"forbidden"}""")
        assertFalse(a.ok)
        assertTrue(a.agents.isEmpty())
        assertTrue(LabJson.decodeFromString<Agents>("""{"agents":[]}""").ok)   // an answer with no flag is a good one
    }

    @Test
    fun `sessions parse and read as lines`() {
        val a = LabJson.decodeFromString<SessionsAnswer>(
            """{"ok":true,"sessions":[{"id":"a1","made":100.0,"last":1000.0,"where":"192.168.1.5","what":"Firefox on Linux"}],"current":"phone:Pixel","phones":[]}""")
        assertEquals("phone:Pixel", a.current)
        assertEquals("Firefox on Linux · 192.168.1.5 · used 10 min ago", sessionLine(a.sessions[0], 1600))
        assertEquals("used just now", sessionLine(AdminSession(last = 1000.0), 1010))
        assertEquals("used 3 days ago", sessionLine(AdminSession(last = 0.0), 3 * 86_400L))
    }
}
