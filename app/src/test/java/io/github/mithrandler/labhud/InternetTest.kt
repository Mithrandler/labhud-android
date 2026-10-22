package io.github.mithrandler.labhud

import io.github.mithrandler.labhud.data.LabJson
import io.github.mithrandler.labhud.data.LabState
import io.github.mithrandler.labhud.data.rateText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The internet's rates in the state, and how the top bar writes them (as the wall does). */
class InternetTest {
    @Test fun rates() {
        assertEquals("0", rateText(512.0))
        assertEquals("340K", rateText(340.0 * 1024))
        assertEquals("1.8M", rateText(1.8 * 1048576))
    }

    @Test fun decodes() {
        val st = LabJson.decodeFromString<LabState>("""{"internet":{"dn":2048.5,"up":10}}""")
        assertEquals(2048.5, st.internet!!.dn, 0.0)
        assertEquals(10.0, st.internet!!.up, 0.0)
    }

    @Test fun olderServer() {
        assertNull(LabJson.decodeFromString<LabState>("""{"title":"x"}""").internet)
    }
}
