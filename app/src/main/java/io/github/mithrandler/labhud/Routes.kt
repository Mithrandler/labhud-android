package io.github.mithrandler.labhud

/**
 * The routes of the screens that take an id. An id is text from the server (a card's, an agent's, a game's): one with a "/", "?",
 * "#" or "%" in it would build another route or none, so every id is percent-encoded here and decoded by Navigation.
 */
object Routes {
    fun card(id: String, ask: String = "") = "card/${enc(id)}" + if (ask.isEmpty()) "" else "?ask=${enc(ask)}"
    fun host(id: String) = "host/${enc(id)}"
    fun game(id: String) = "game/${enc(id)}"
    fun agent(id: String) = "agent/${enc(id)}"
    fun settings(id: String) = "settings/${enc(id)}"

    /** Percent-encodes everything but the unreserved characters (RFC 3986), the text as UTF-8. */
    fun enc(s: String): String = buildString {
        for (b in s.toByteArray(Charsets.UTF_8)) {
            val c = b.toInt() and 0xFF
            if (c.toChar() in 'A'..'Z' || c.toChar() in 'a'..'z' || c.toChar() in '0'..'9' || c.toChar() in "-._~") append(c.toChar())
            else append('%').append("0123456789ABCDEF"[c shr 4]).append("0123456789ABCDEF"[c and 15])
        }
    }
}
