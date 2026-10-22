package io.github.mithrandler.labhud.wall

import kotlinx.serialization.json.JsonPrimitive

/** The page's sessionStorage key for the unlock ticket (static/lock.js, LOCK_KEY). */
const val UNLOCK_KEY = "labhud-unlock"

/** The wall's address: the page, with `?glance=1` for the glance view. */
fun wallAddress(serverUrl: String, glance: Boolean): String = serverUrl.trimEnd('/') + "/" + if (glance) "?glance=1" else ""

/**
 * A script that hands the app's unlock ticket to the wall page before the page's own scripts run, so
 * unlocking once (in the app) is enough: lock.js reads the ticket from sessionStorage when the lock
 * turns on. Null without a ticket (no PIN, or locked: the page asks for the PIN as before). The
 * ticket is quoted by a JSON encoder, never glued into the script as text.
 */
fun unlockScript(ticket: String?): String? {
    if (ticket.isNullOrEmpty()) return null
    val quoted = JsonPrimitive(ticket).toString()
    return "try { sessionStorage.setItem(${JsonPrimitive(UNLOCK_KEY).toString()}, $quoted); } catch (e) {}"
}
