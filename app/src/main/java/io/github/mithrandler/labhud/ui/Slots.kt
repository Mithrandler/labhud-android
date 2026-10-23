package io.github.mithrandler.labhud.ui

import io.github.mithrandler.labhud.data.Card
import io.github.mithrandler.labhud.data.GameServer
import io.github.mithrandler.labhud.data.Page

/** The height of a group's title on Status, dp (its space above included). */
internal const val TITLE_H = 26f

/**
 * One thing on a Status screen, with a key for its grid cell: a card, a game server, a group's title, the "+N" of the problems that
 * did not fit, or (with [mini]) a card forced to a small tile, as in the "Needs attention" row.
 */
internal data class Slot(
    val key: String,
    val card: Card? = null,
    val game: GameServer? = null,
    val title: String? = null,
    val bad: Boolean = false,
    val more: Int = 0,
    val mini: Boolean = false,
) {
    /** Cells it takes in a line of [cols]: a title the line, a small tile one, a card by its kind, a game server one cell on a wide screen, else half a line or more. */
    fun span(cols: Int): Int = when {
        title != null -> cols
        more > 0 || mini -> 1
        else -> card?.tileSpan(cols) ?: if (cols >= 4) 1 else cols
    }

    /** How tall it is, in dp. */
    fun height(tile: Float): Float = when {
        title != null -> TITLE_H
        more > 0 || mini -> tile
        else -> card?.tileHeight(tile) ?: tile
    }
}

/** A card in trouble: the server marks it, or a number of it is red. */
internal fun isProblem(c: Card): Boolean = c.bad == true || c.severity() == "crit"

/**
 * What a page shows, in order: on the first page a "Needs attention" row (the cards in trouble as small tiles, at most one line, the
 * rest as "+N"), the favourites, then every group under its title, the cards in trouble first after the big ones (the order inside is kept).
 */
internal fun buildSlots(page: Page, first: Boolean, byId: Map<String, Card>, favs: Set<String>, cols: Int): List<Slot> {
    if (page.games) return page.servers.map { Slot("game-" + it.id.ifEmpty { it.name }, game = it) }
    val out = mutableListOf<Slot>()
    if (first) {
        val trouble = page.groups.flatMap { it.cards }.mapNotNull { byId[it] }.filter(::isProblem).distinctBy { it.id }
        if (trouble.isNotEmpty()) {
            out += Slot("t-attention", title = "Needs attention", bad = true)
            val shown = if (trouble.size > cols) trouble.take(maxOf(cols - 1, 1)) else trouble
            shown.forEach { out += Slot("c-attn-${it.id}", it, mini = true) }
            if (trouble.size > shown.size) out += Slot("attn-more", more = trouble.size - shown.size)
        }
        val fav = favs.mapNotNull { byId[it] }.sortedBy { it.name.lowercase() }
        if (fav.isNotEmpty()) {
            out += Slot("t-favorites", title = "Favorites")
            fav.forEach { out += Slot("c-fav-${it.id}", it) }
        }
    }
    for (g in page.groups) {
        val cards = g.cards.mapNotNull { byId[it] }
        if (cards.isEmpty()) continue
        if (g.title.isNotEmpty()) out += Slot("t-${g.id}", title = g.title, bad = cards.any(::isProblem))
        // a big card (the host) stays the group's first; then the cards in trouble
        cards.sortedBy { if (it.isBig()) 0 else if (isProblem(it)) 1 else 2 }.forEach { out += Slot("c-${g.id}-${it.id}", it) }
    }
    return out
}

/** A title never ends a screen with its cards on the next one: it moves down with them. */
internal fun keepTitlesWithCards(screens: List<List<Slot>>): List<List<Slot>> {
    val out = screens.map { it.toMutableList() }
    for (i in 0 until out.size - 1) {
        while (out[i].size > 1 && out[i].last().title != null) out[i + 1].add(0, out[i].removeAt(out[i].size - 1))
    }
    return out
}
