package io.github.mithrandler.labhud.data

import java.net.URI
import java.net.URLDecoder
import java.util.UUID

/** What a pairing link carries: labhud://pair?u=<address>&c=<code>[&fp=<sha256>]. */
data class PairLink(val url: String, val code: String, val fingerprint: String)

object Pairing {
    private val CODE = Regex("[A-Za-z0-9_-]{16,64}")
    private val FP = Regex("[0-9a-f]{64}")

    /** The link from a QR code or pasted text, or null when it is not a LabHUD pairing link. */
    fun parse(text: String): PairLink? {
        val uri = runCatching { URI(text.trim()) }.getOrNull() ?: return null
        if (uri.scheme != "labhud" || uri.host != "pair") return null
        val q = (uri.rawQuery ?: return null).split("&").mapNotNull { part ->
            val (k, v) = part.split("=", limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
            k to URLDecoder.decode(v, "UTF-8")
        }.toMap()
        val url = q["u"]?.trimEnd('/') ?: return null
        val code = q["c"] ?: return null
        val fp = q["fp"]?.lowercase() ?: ""
        if (!(url.startsWith("http://") || url.startsWith("https://")) || !CODE.matches(code)) return null
        if (fp.isNotEmpty() && !FP.matches(fp)) return null
        return PairLink(url, code, fp)
    }

    /** Pairs and keeps the server (with its encrypted token) as the one shown. */
    suspend fun pair(link: PairLink, deviceName: String, store: ServerStore): Server {
        val answer = LabApi(link.url, null, link.fingerprint).pair(link.code, deviceName)
        if (!answer.ok || answer.token.isBlank()) throw ApiException(answer.error.ifBlank { "LabHUD refused the code." })
        val server = Server(
            id = UUID.randomUUID().toString(),
            name = answer.info.title.ifBlank { "LabHUD" },
            url = link.url,
            fingerprint = link.fingerprint,
            deviceName = answer.name,
        )
        store.add(server, answer.token)
        return server
    }
}
