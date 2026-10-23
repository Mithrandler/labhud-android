package io.github.mithrandler.labhud

import io.github.mithrandler.labhud.data.Card
import io.github.mithrandler.labhud.data.LabState
import io.github.mithrandler.labhud.data.sharedWith
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class SharingTest {
    private fun state(vararg cards: Card) = LabState(cards = cards.toList())

    @Test fun `a card that did not change stays the same instance`() {
        val old = state(Card(id = "a", summary = "1"), Card(id = "b", summary = "2"))
        val next = state(Card(id = "a", summary = "1"), Card(id = "b", summary = "3")).sharedWith(old)
        assertSame(old.cards[0], next.cards[0])
        assertNotSame(old.cards[1], next.cards[1])
        assertEquals("3", next.cards[1].summary)
    }

    @Test fun `an identical document gives back the old lists`() {
        val old = state(Card(id = "a"))
        val next = state(Card(id = "a")).sharedWith(old)
        assertSame(old.cards, next.cards)
        assertSame(old.pages, next.pages)
    }

    @Test fun `cards that moved or came new keep their own data`() {
        val old = state(Card(id = "a"), Card(id = "b"))
        val next = state(Card(id = "c"), Card(id = "b")).sharedWith(old)
        assertEquals(listOf("c", "b"), next.cards.map { it.id })
        assertSame(old.cards[1], next.cards[1])
    }

    @Test fun `without an old state nothing changes`() {
        val s = state(Card(id = "a"))
        assertSame(s, s.sharedWith(null))
    }
}
