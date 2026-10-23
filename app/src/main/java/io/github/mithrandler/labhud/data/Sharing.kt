package io.github.mithrandler.labhud.data

/**
 * [this] state with every part that equals the same part of [old] swapped for [old]'s instance. Each
 * message from the stream is decoded into all-new objects; Compose skips a composable only when it
 * gets the very same instance, so without this one changed number redraws every card on the screen.
 */
fun LabState.sharedWith(old: LabState?): LabState {
    if (old == null) return this
    val before = old.cards.associateBy { it.id }
    return copy(
        cards = if (old.cards == cards) old.cards else cards.map { c -> before[c.id]?.takeIf { it == c } ?: c },
        pages = reuse(old.pages, pages),
        problems = reuse(old.problems, problems),
        alerts = reuse(old.alerts, alerts),
        containers = reuse(old.containers, containers),
        bookmarks = reuse(old.bookmarks, bookmarks),
    )
}

private fun <T> reuse(old: List<T>, new: List<T>): List<T> = if (old == new) old else new
