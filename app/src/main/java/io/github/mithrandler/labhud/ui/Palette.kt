package io.github.mithrandler.labhud.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mithrandler.labhud.data.LabState
import io.github.mithrandler.labhud.data.Server
import io.github.mithrandler.labhud.ui.settings.visibleSections
import java.text.Normalizer

// The search sheet is also the command palette, as the wall's 🔍 (static/search.js): pages, cards,
// the cards' buttons (RESTART · name) and the sections of ⚙, the last two only for the admin.
// "> text" looks only among the commands. A button opens its card and asks there, as a press does.

enum class HitKind { Page, Card, Action, Section, Container, Link, Web }

data class Hit(
    val label: String,
    val hint: String,
    val kind: HitKind,
    /** What the words are looked for in, already normalised. */
    val text: String,
    /** The page, card or ⚙ section it goes to. */
    val target: String,
    /** For an [HitKind.Action]: the action's name on the card. */
    val action: String = "",
    val icon: String = "",
) {
    val command: Boolean get() = kind == HitKind.Page || kind == HitKind.Action || kind == HitKind.Section
}

const val PALETTE_MAX = 12

private val MARKS = Regex("\\p{Mn}+")
private val WORDS = Regex("\\s+")
private val SPLIT = Regex("[\\s·\\-_.]+")

/** Lower case, without diacritics: "Rețea" finds "retea". */
fun paletteNorm(s: String): String = MARKS.replace(Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD), "")

/** The items with every word of `q`, best first: a label that starts with the query, then one with
 *  a word that does, then the rest, each in the order given. `q` is already normalised. */
fun paletteRank(items: List<Hit>, q: String): List<Hit> {
    val words = q.split(WORDS).filter { it.isNotEmpty() }
    if (words.isEmpty()) return emptyList()
    return items.withIndex().filter { (_, x) -> words.all { it in x.text } }.sortedWith(compareBy({ (_, x) ->
        val label = paletteNorm(x.label)
        when {
            label.startsWith(words[0]) -> 0
            label.split(SPLIT).any { it.startsWith(words[0]) } -> 1
            else -> 2
        }
    }, { it.index })).map { it.value }
}

/** Everything the box can find. A card's buttons and ⚙ only for the admin, as on the card's own screen. */
fun paletteItems(st: LabState, admin: Boolean): List<Hit> {
    val out = mutableListOf<Hit>()
    st.pages.forEach { p ->
        out += Hit(p.title, "page", HitKind.Page, paletteNorm("page go ${p.title}"), p.id, icon = p.icon)
        if (p.games) return@forEach
        p.groups.forEach { g ->
            g.cards.mapNotNull { st.byId[it] }.forEach { c ->
                out += Hit(c.name, "${p.title} · ${g.title}", HitKind.Card,
                    paletteNorm(listOf(c.name, c.subtitle, c.id, g.title, p.title, c.url).joinToString(" ")), c.id, icon = c.icon)
                if (admin) c.actions.forEach { a ->
                    val word = a.label.ifEmpty { a.name }
                    out += Hit("${word.lowercase()} · ${c.name}", if (a.danger || a.confirm.isNotEmpty()) "asks first" else "action",
                        HitKind.Action, paletteNorm("$word ${c.name} ${c.id} ${p.title}"), c.id, a.name, c.icon)
                }
            }
        }
    }
    // the containers the hosts' agents report, found by name; their buttons are the admin's, as a card's
    st.containers.forEach { x ->
        val host = st.byId[x.card] ?: return@forEach
        out += Hit(x.name, "container · ${host.name}", HitKind.Container, paletteNorm("container docker ${x.name} ${host.name}"), x.card)
        if (admin) x.actions.forEach { a ->
            val word = a.label.ifEmpty { a.name }
            out += Hit("${word.lowercase()} · ${x.name}", if (a.danger || a.confirm.isNotEmpty()) "asks first" else "action",
                HitKind.Action, paletteNorm("$word ${x.name} container ${host.name}"), x.card, a.name)
        }
    }
    st.bookmarks.forEach { b ->
        out += Hit(b.name, b.group, HitKind.Link, paletteNorm("${b.name} ${b.group} ${b.url}"), b.url)
    }
    if (admin) visibleSections(admin = true).forEach { s ->
        out += Hit(s.title, "⚙ settings", HitKind.Section, paletteNorm("settings ${s.title} ${s.hint} ${s.keywords}"), s.id)
    }
    return out.distinctBy { listOf(it.kind, it.target, it.action) }
}

/** What the box shows for what is typed: "> " alone lists the buttons, "> x" only the commands. */
fun paletteHits(items: List<Hit>, raw: String, max: Int = PALETTE_MAX): List<Hit> {
    val typed = raw.trim()
    val cmd = typed.startsWith(">")
    val q = paletteNorm(if (cmd) typed.drop(1).trim() else typed)
    val all = if (cmd) items.filter { it.command } else items
    return if (cmd && q.isEmpty()) all.filter { it.kind == HitKind.Action }.take(max) else paletteRank(all, q).take(max)
}

/** The last hit of a plain search that matches nothing else: the web search of `[search] url` ("" or no `%s`: none). */
fun paletteWeb(search: String, raw: String): Hit? {
    val typed = raw.trim()
    if (typed.isEmpty() || typed.startsWith(">") || !search.startsWith("http") || "%s" !in search) return null
    val host = runCatching { java.net.URI(search.replace("%s", "")).host }.getOrNull().orEmpty()
    return Hit("Search the web for “$typed”", host, HitKind.Web, "", search.replace("%s", java.net.URLEncoder.encode(typed, "UTF-8")))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaletteSheet(
    st: LabState,
    admin: Boolean,
    server: Server?,
    onDismiss: () -> Unit,
    onPage: (String) -> Unit,
    onCard: (String) -> Unit,
    onAction: (card: String, action: String) -> Unit,
    onSection: (String) -> Unit,
) {
    var q by remember { mutableStateOf("") }
    val items = remember(st, admin) { paletteItems(st, admin) }
    val hits = remember(items, q) { paletteHits(items, q) + listOfNotNull(paletteWeb(st.search, q)) }
    val uri = LocalUriHandler.current
    fun run(h: Hit) = when (h.kind) {
        HitKind.Link, HitKind.Web -> { onDismiss(); uri.openUri(h.target) }
        HitKind.Container -> onCard(h.target)
        HitKind.Page -> onPage(h.target)
        HitKind.Card -> onCard(h.target)
        HitKind.Action -> onAction(h.target, h.action)
        HitKind.Section -> onSection(h.target)
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Lab.card) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
            OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text(if (admin) "Search, > for commands" else "Search the cards and pages") },
                leadingIcon = { Icon(LabIcons.search, null) },
                // Enter runs the first hit, as on the wall
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { hits.firstOrNull()?.let(::run) }))
            hits.forEach { h ->
                val byId = st.byId[h.target]
                Row(Modifier.fillMaxWidth().clickable { run(h) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (h.kind == HitKind.Card && byId?.status != null) StatusDot(byId)
                    else NamedIcon(h.icon, kindIcon(h.kind), server, 18.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(h.label, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f))
                    Text(h.hint, fontSize = 12.sp, maxLines = 1, color = if (h.hint == "asks first") Lab.warn else Lab.muted,
                        modifier = Modifier.padding(start = 8.dp))
                }
            }
            if (q.isNotBlank() && hits.isEmpty()) Text("Nothing matches.", color = Lab.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

private fun kindIcon(k: HitKind): ImageVector = when (k) {
    HitKind.Container -> LabIcons.status
    HitKind.Link -> LabIcons.link
    HitKind.Web -> LabIcons.search
    HitKind.Page -> LabIcons.pages
    HitKind.Card -> LabIcons.status
    HitKind.Action -> LabIcons.bolt
    HitKind.Section -> LabIcons.settings
}
