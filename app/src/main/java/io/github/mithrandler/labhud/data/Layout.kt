package io.github.mithrandler.labhud.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/* ⚙ > Dashboard: the pages, groups and cards as labhud's layout.py draws them (/api/setup/layout) and takes them back (/api/setup/layout-save), which applies them as a whole: a card is never lost or made up. */

@Serializable
data class LayoutCard(
    val id: String = "",
    val name: String = "",
    /** The kind set in the config ("" when it is guessed). */
    val kind: String = "",
    val guess: String? = null,
    val icon: String = "",
    /** "" (the page's own), s, m, l or xl. */
    val size: String = "",
    val hidden: Boolean = false,
)

@Serializable
data class LayoutGroup(
    val id: String = "",
    val title: String = "",
    /** Adds the Proxmox guests by itself: it cannot be dropped, and has no cards to move. */
    val auto: Boolean = false,
    val hidden: Boolean = false,
    val cards: List<LayoutCard> = emptyList(),
)

@Serializable
data class LayoutPage(
    val id: String = "",
    val title: String = "",
    val icon: String = "",
    val games: Boolean = false,
    /** 0: as the screen's width says; else 1 to 6. */
    val columns: Int = 0,
    /** "", "compact" or "normal". */
    val density: String = "",
    val groups: List<LayoutGroup> = emptyList(),
)

@Serializable
data class LayoutView(val ok: Boolean = false, val error: String = "", val pages: List<LayoutPage> = emptyList())

suspend fun LabApi.layout(): LayoutView = LabJson.decodeFromString(postAnswer("/api/setup/layout", "{}"))

suspend fun LabApi.saveLayout(pages: List<LayoutPage>): ActionAnswer = LabJson.decodeFromString(postAnswer("/api/setup/layout-save", layoutBody(pages)))

/** What layout-save is sent: every value as it stands ("" and 0 remove what the config had, so a cleared icon is cleared). */
fun layoutBody(pages: List<LayoutPage>): String = buildJsonObject {
    put("pages", buildJsonArray {
        pages.forEach { p ->
            add(buildJsonObject {
                put("id", p.id); put("title", p.title); put("icon", p.icon)
                put("columns", p.columns); put("density", p.density)
                put("groups", buildJsonArray {
                    p.groups.forEach { g ->
                        add(buildJsonObject {
                            put("id", g.id); put("title", g.title); put("hidden", g.hidden)
                            put("cards", buildJsonArray {
                                g.cards.forEach { c ->
                                    add(buildJsonObject {
                                        put("id", c.id); put("icon", c.icon); put("size", c.size); put("hidden", c.hidden)
                                    })
                                }
                            })
                        })
                    }
                })
            })
        }
    })
}.toString()

// --- the edits, pure: each returns the pages anew ---

/** [list] with the element at [index] moved by [delta] places (stays when it would leave the list). */
fun <T> List<T>.moved(index: Int, delta: Int): List<T> {
    val to = index + delta
    if (index !in indices || to !in indices) return this
    return toMutableList().also { val x = it.removeAt(index); it.add(to, x) }
}

fun List<LayoutPage>.movePage(id: String, delta: Int): List<LayoutPage> = moved(indexOfFirst { it.id == id }, delta)

fun List<LayoutPage>.editPage(id: String, edit: (LayoutPage) -> LayoutPage): List<LayoutPage> = map { if (it.id == id) edit(it) else it }

fun List<LayoutPage>.moveGroup(pageId: String, groupId: String, delta: Int): List<LayoutPage> =
    editPage(pageId) { p -> p.copy(groups = p.groups.moved(p.groups.indexOfFirst { it.id == groupId }, delta)) }

fun List<LayoutPage>.editGroup(groupId: String, edit: (LayoutGroup) -> LayoutGroup): List<LayoutPage> =
    map { p -> p.copy(groups = p.groups.map { if (it.id == groupId) edit(it) else it }) }

fun List<LayoutPage>.editCard(cardId: String, edit: (LayoutCard) -> LayoutCard): List<LayoutPage> =
    map { p -> p.copy(groups = p.groups.map { g -> g.copy(cards = g.cards.map { if (it.id == cardId) edit(it) else it }) }) }

fun List<LayoutPage>.moveCard(groupId: String, cardId: String, delta: Int): List<LayoutPage> =
    editGroup(groupId) { g -> g.copy(cards = g.cards.moved(g.cards.indexOfFirst { it.id == cardId }, delta)) }

/** A group id nobody has yet ("group-2"...): groups are named by letters, digits, - and _. */
fun List<LayoutPage>.freeGroupId(): String {
    val taken = flatMap { p -> p.groups.map { it.id } }.toSet()
    return generateSequence(1) { it + 1 }.map { "group-$it" }.first { it !in taken }
}

/**
 * The card [cardId] at the end of group [toGroup] of page [toPage]; with [newTitle] a group of that name is made there first
 * (an empty group is dropped by the server, so a group only comes with a card). A group left empty goes when the layout is saved.
 */
fun List<LayoutPage>.moveCardTo(cardId: String, toPage: String, toGroup: String?, newTitle: String?): List<LayoutPage> {
    val card = flatMap { p -> p.groups.flatMap { it.cards } }.firstOrNull { it.id == cardId } ?: return this
    val gid = toGroup ?: freeGroupId()
    val removed = map { p -> p.copy(groups = p.groups.map { g -> g.copy(cards = g.cards.filter { it.id != cardId }) }) }
    return removed.editPage(toPage) { p ->
        if (toGroup != null && p.groups.any { it.id == toGroup }) {
            p.copy(groups = p.groups.map { if (it.id == toGroup) it.copy(cards = it.cards + card) else it })
        } else {
            p.copy(groups = p.groups + LayoutGroup(id = gid, title = newTitle?.trim().takeUnless { it.isNullOrEmpty() } ?: "New group", cards = listOf(card)))
        }
    }
}

/** Pages with a group and a card to show: what the server will keep (it drops the empty ones). */
fun List<LayoutPage>.kept(): List<LayoutPage> = mapNotNull { p ->
    val gs = p.groups.filter { it.auto || it.cards.isNotEmpty() }
    if (gs.isEmpty() && !p.games) null else p.copy(groups = gs)
}
