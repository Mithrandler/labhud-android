package io.github.mithrandler.labhud

import io.github.mithrandler.labhud.data.Card
import io.github.mithrandler.labhud.data.Metric
import io.github.mithrandler.labhud.ui.span
import io.github.mithrandler.labhud.ui.severity
import io.github.mithrandler.labhud.ui.uptimeText
import org.junit.Assert.assertEquals
import org.junit.Test

/** Card sizing, severity colour, and the uptime label. */
class CardLookTest {
    /** How many grid cells a card takes from its size and kind, never more than the row has. */
    @Test fun spans() {
        assertEquals(1, Card().span(2))
        assertEquals(1, Card(size = "s").span(2))
        assertEquals(2, Card(size = "m").span(2))
        assertEquals(2, Card(size = "l").span(2))
        assertEquals(2, Card(size = "xl").span(2))
        assertEquals(2, Card(kind = "list").span(2))
        assertEquals(1, Card(kind = "list", size = "s").span(2))
        assertEquals(4, Card(size = "xl").span(4))
        assertEquals(2, Card(size = "l").span(4))
        assertEquals(1, Card(size = "l").span(1))
    }

    /** The card's worst severity: crit beats warn beats nothing. */
    @Test fun severities() {
        assertEquals("", Card().severity())
        assertEquals("crit", Card(bad = true).severity())
        assertEquals("warn", Card(metrics = listOf(Metric(sev = "warn"))).severity())
        assertEquals("crit", Card(metrics = listOf(Metric(sev = "warn"), Metric(sev = "crit"))).severity())
        assertEquals("", Card(bad = false, metrics = listOf(Metric(sev = ""))).severity())
    }

    /** Human-readable uptime percent with optional coverage suffix. */
    @Test fun uptimeWords() {
        assertEquals("", uptimeText(null, 50.0))
        assertEquals("100%", uptimeText(100.0, 100.0))
        assertEquals("100%", uptimeText(99.999, null))
        assertEquals("99.2%", uptimeText(99.2, 100.0))
        assertEquals("99.2% · 87% covered", uptimeText(99.2, 87.0))
        assertEquals("75.0% · 88% covered", uptimeText(75.0, 87.5))
    }
}
