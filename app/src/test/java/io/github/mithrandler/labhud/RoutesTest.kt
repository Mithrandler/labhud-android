package io.github.mithrandler.labhud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** An id from the server never builds another route. */
class RoutesTest {
    @Test fun plainIdsStayAsTheyAre() {
        assertEquals("card/vm-pve-100", Routes.card("vm-pve-100"))
        assertEquals("host/Abc_1.2~x", Routes.host("Abc_1.2~x"))
    }

    @Test fun slashQuestionMarkHashAndPercentAreEncoded() {
        assertEquals("card/a%2Fb", Routes.card("a/b"))
        assertEquals("card/what%3F", Routes.card("what?"))
        assertEquals("game/a%23b", Routes.game("a#b"))
        assertEquals("agent/100%25", Routes.agent("100%"))
        assertEquals("settings/a%20b", Routes.settings("a b"))
    }

    @Test fun textIsEncodedAsUtf8() {
        assertEquals("card/%C8%99ir", Routes.card("șir"))
        assertEquals("card/%F0%9F%96%A5", Routes.card("🖥"))
    }

    @Test fun theAskedButtonRidesAlongEncoded() {
        assertEquals("card/pve?ask=restart%20%C2%B7%20web", Routes.card("pve", "restart · web"))
        assertFalse("?" in Routes.card("pve"))
    }

    @Test fun anIdCannotAddASegmentOrAQuery() {
        for (id in listOf("../x", "a/b/c", "a?b=c", "#frag", "")) {
            val r = Routes.card(id).removePrefix("card/")
            assertFalse(id, "/" in r || "?" in r || "#" in r)
        }
    }
}
