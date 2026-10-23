package io.github.mithrandler.labhud.ui

import io.github.mithrandler.labhud.data.Card
import io.github.mithrandler.labhud.data.GameServer
import io.github.mithrandler.labhud.data.Page

/** The height of a title on Status ("Needs attention"), dp (its space above included). */
internal const val TITLE_H = 26f

/** A group of several cards on a listing page, folded into one tile: how many are in trouble; the list is on its own screen. */
internal data class GroupTile(val id: String, val title: String, val cards: List<Card>) {
    /** Broken things, not broken cards: a card that lists what is wrong counts each row, any other counts one. */
    val problems: Int get() = cards.filter(::isProblem).sumOf { brokenRows(it).size.coerceAtLeast(1) }
    val warnings: Int get() = cards.count { !isProblem(it) && it.severity() == "warn" }
    val hasProblem: Boolean get() = cards.any(::isProblem)
}

/** The rows of a card that its list marks as bad (FAILURES: "pve2: fleet-update"); empty for a card that lists nothing. */
internal fun brokenRows(c: Card) = c.rows.filter { it.bad }

/** Where a host comes among the hosts: PVE, PVE2, then the others as configured. */
internal fun hostRank(c: Card): Int = when (c.node.lowercase()) { "pve" -> 0; "pve2" -> 1; else -> 2 }

/**
 * One thing on a Status screen, with a key for its grid cell: a card (a small tile; [wide] a host, two cells with its four meters),
 * a [group]'s summary tile, a game server, or the title of the "Needs attention" / "Favorites" rows.
 */
internal data class Slot(
    val key: String,
    val card: Card? = null,
    val game: GameServer? = null,
    val title: String? = null,
    val bad: Boolean = false,
    val group: GroupTile? = null,
    val wide: Boolean = false,
    val mini: Boolean = false,
) {
    /** Cells it takes in a line of [cols]: a title the line, a host two, every other tile one, a game server one cell on a wide screen. */
    fun span(cols: Int): Int = when {
        title != null -> cols
        wide -> 2.coerceAtMost(cols)
        group != null || card != null -> 1
        else -> if (cols >= 4) 1 else cols
    }

    /** How tall it is, in dp: every tile the same. */
    fun height(tile: Float): Float = if (title != null) TITLE_H else tile
}

/** A card in trouble: the server marks it, or a number of it is red. */
internal fun isProblem(c: Card): Boolean = c.bad == true || c.severity() == "crit"

/** A Proxmox host: the card with the rings of a machine, whose guests are inside it. */
internal fun isHost(c: Card): Boolean = c.type == "proxmox" && c.kind != "guest"

/**
 * What a page shows, in order, as the wall does: the cards in trouble first (a "Needs attention" row), the favourites on the first
 * page, then, on General, a tile per card (a host two cells wide with its guests inside it, so a guest is a tile of its own only when
 * it is in trouble), and on the other pages one summary tile per group of several cards.
 */
internal fun buildSlots(page: Page, first: Boolean, byId: Map<String, Card>, favs: Set<String>): List<Slot> {
    if (page.games) return page.servers.map { Slot("game-" + it.id.ifEmpty { it.name }, game = it) }
    val out = mutableListOf<Slot>()
    val groups = page.groups.map { it to it.cards.mapNotNull { id -> byId[id] } }.filter { it.second.isNotEmpty() }
    val expanded = page.id == "general"
    fun folded(cards: List<Card>) = !expanded && cards.size > 1
    // the hosts first, PVE and PVE2 at the head (a host in trouble is red where it is)
    val hosts = if (expanded) groups.flatMap { it.second }.filter(::isHost).distinctBy { it.id }.sortedBy(::hostRank) else emptyList()
    hosts.forEach { out += Slot("c-host-${it.id}", it, wide = true) }
    val hostIds = hosts.map { it.id }.toSet()
    // in trouble: a card of its own, or, where a group is folded into one tile, that tile (not its cards as well)
    val trouble = groups.filter { !folded(it.second) }.flatMap { it.second }.filter { isProblem(it) && it.id !in hostIds }.distinctBy { it.id }
    val troubledGroups = groups.filter { folded(it.second) && it.second.any(::isProblem) }
    if (trouble.isNotEmpty() || troubledGroups.isNotEmpty()) {
        out += Slot("t-attention", title = "Needs attention", bad = true)
        troubledGroups.forEach { (g, cards) -> out += Slot("g-${g.id}", group = GroupTile(g.id, g.title, cards)) }
        trouble.forEach { out += Slot("c-attn-${it.id}", it, wide = isHost(it), mini = !isHost(it)) }
    }
    if (first) {
        val fav = favs.mapNotNull { byId[it] }.filter { f -> trouble.none { it.id == f.id } && f.id !in hostIds }.sortedBy { it.name.lowercase() }
        if (fav.isNotEmpty()) {
            out += Slot("t-favorites", title = "Favorites")
            fav.forEach { out += Slot("c-fav-${it.id}", it, mini = true) }
        }
    }
    val inTrouble = trouble.map { it.id }.toSet() + hostIds
    for ((g, cards) in groups) {
        if (!folded(cards)) {
            val nodes = cards.filter(::isHost).map { it.node }.filter { it.isNotEmpty() }.toSet()
            for (c in cards) {
                if (c.id in inTrouble) continue
                if (c.kind == "guest" && c.node in nodes) continue
                out += Slot("c-${g.id}-${c.id}", c, wide = isHost(c), mini = !isHost(c))
            }
        } else if (troubledGroups.none { it.first.id == g.id }) out += Slot("g-${g.id}", group = GroupTile(g.id, g.title, cards))
    }
    return out
}
