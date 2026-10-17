package io.github.mithrandler.labhud.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.IOException
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/** What went wrong, in words a person can act on. */
class ApiException(message: String, val code: Int = 0) : IOException(message)

/** A server answering under the wrong API, or too old to have one. */
class TooOldException(message: String) : IOException(message)

private val JSON = "application/json".toMediaType()

/**
 * The HTTP side of one labhud. Over HTTPS with a fingerprint (from the pairing link), only that
 * exact certificate is accepted: labhud's own is usually self-signed, so the system's trust would
 * refuse it, and accepting any certificate would hand the token to whoever sits in between.
 */
class LabApi(val baseUrl: String, private val token: String?, fingerprint: String = "", private val ticket: String? = null) {
    private val base = baseUrl.trimEnd('/')
    val client: OkHttpClient = clientFor(fingerprint)

    private fun request(path: String) = Request.Builder().url(base + path).apply {
        if (token != null) header("Authorization", "Bearer $token")
        if (ticket != null) header("X-Labhud-Unlock", ticket)
    }

    private suspend fun call(req: Request): String = withContext(Dispatchers.IO) {
        client.newCall(req).execute().use { r -> body(r) }
    }

    private fun body(r: Response): String {
        val text = r.body.string()
        if (!r.isSuccessful) {
            throw ApiException(
                when (r.code) {
                    401, 403, 421 -> "LabHUD refused this phone. It may have been unpaired: pair it again."
                    404 -> "This LabHUD has no app API. Update it to a version with phone pairing."
                    503 -> "LabHUD has too many open screens right now."
                    else -> "LabHUD answered HTTP ${r.code}."
                }, r.code
            )
        }
        return text
    }

    /** For the routes whose 403/409 carries labhud's own words ({ok:false, error}): the answer, never thrown. */
    private suspend fun answer(path: String, json: String): String = withContext(Dispatchers.IO) {
        client.newCall(request(path).post(json.toRequestBody(JSON)).build()).execute().use { r ->
            val text = r.body.string()
            if (r.code in listOf(200, 400, 403, 404, 409, 502) && text.startsWith("{")) text
            else body(r)
        }
    }

    private suspend fun get(path: String) = call(request(path).get().build())
    private suspend fun post(path: String, json: String) = call(request(path).post(json.toRequestBody(JSON)).build())

    suspend fun info(): Info {
        val info = LabJson.decodeFromString<Info>(get("/api/v1/info"))
        if (info.api != 1) throw TooOldException("This LabHUD speaks API ${info.api}; this app knows 1. Update the app.")
        return info
    }

    suspend fun state(): LabState = LabJson.decodeFromString(get("/api/v1/state"))
    suspend fun card(id: String): Card = LabJson.decodeFromString(get("/api/v1/card/$id"))
    suspend fun history(long: Boolean): History = LabJson.decodeFromString(get("/api/v1/history" + if (long) "?long" else ""))
    suspend fun events(): Events = LabJson.decodeFromString(get("/api/v1/events"))
    suspend fun uptime(week: Boolean): Uptime = LabJson.decodeFromString(get("/api/v1/uptime?range=" + if (week) "7d" else "24h"))

    suspend fun pair(code: String, name: String): PairResponse {
        val body = buildJsonObject { put("code", code); put("name", name) }.toString()
        val text = try {
            post("/api/v1/pair", body)
        } catch (e: ApiException) {
            throw if (e.code == 403) ApiException("The code is wrong, used or expired. Make a new one.", 403)
            else if (e.code == 404) ApiException("This LabHUD is too old for the app: update it first.", 404) else e
        }
        return LabJson.decodeFromString(text)
    }

    /** Pairs by signing in as labhud's admin (password + authenticator code), without a QR code. */
    suspend fun loginPair(user: String, password: String, code: String, name: String): PairResponse {
        val body = buildJsonObject { put("user", user); put("password", password); put("code", code); put("name", name) }.toString()
        val text = try {
            post("/api/v1/login-pair", body)
        } catch (e: ApiException) {
            throw when (e.code) {
                403 -> ApiException("Wrong user, password or code, or this LabHUD has no admin. Wait a few seconds between tries.", 403)
                404 -> ApiException("This LabHUD is too old to sign in from the app: update it, or pair with a QR code.", 404)
                409 -> ApiException("LabHUD has as many phones as it takes: remove one in ⚙ > Phones.", 409)
                else -> e
            }
        }
        return LabJson.decodeFromString(text)
    }

    suspend fun game(id: String): GameServer = LabJson.decodeFromString(get("/api/v1/game/$id"))
    suspend fun notes(after: Long): Notes = LabJson.decodeFromString(get("/api/v1/notifications?after=$after"))
    suspend fun phones(): Phones = LabJson.decodeFromString(get("/api/v1/devices"))

    suspend fun adminLogin(user: String, password: String, code: String): AdminAnswer =
        LabJson.decodeFromString(answer("/api/v1/admin-login", buildJsonObject { put("user", user); put("password", password); put("code", code) }.toString()))

    suspend fun adminLogout(): AdminAnswer = LabJson.decodeFromString(answer("/api/v1/admin-logout", "{}"))

    /** The display's PIN for a ticket that lets the buttons through for [AdminAnswer.idle] seconds. */
    suspend fun unlock(pin: String): AdminAnswer =
        LabJson.decodeFromString(answer("/api/v1/unlock", buildJsonObject { put("pin", pin) }.toString()))

    /** This phone's UnifiedPush endpoint, "" to forget it. */
    suspend fun push(endpoint: String): ActionAnswer =
        LabJson.decodeFromString(answer("/api/v1/push", buildJsonObject { put("endpoint", endpoint) }.toString()))

    // --- what ⚙ does, for a phone signed in as the admin (and unlocked) ---

    suspend fun agents(): Agents = LabJson.decodeFromString(answer("/api/agents/list", "{}"))
    /** Ticks capabilities on an agent (all of them, the new list), as ⚙ > Agents does. */
    suspend fun agentCaps(id: String, caps: List<String>): ActionAnswer = LabJson.decodeFromString(answer("/api/agents/caps",
        buildJsonObject { put("id", id); put("caps", kotlinx.serialization.json.JsonArray(caps.map { kotlinx.serialization.json.JsonPrimitive(it) })) }.toString()))

    /** The ticked targets of one capability (the new list). */
    suspend fun agentTargets(id: String, cap: String, targets: List<String>): ActionAnswer = LabJson.decodeFromString(answer("/api/agents/targets",
        buildJsonObject { put("id", id); put("cap", cap); put("targets", kotlinx.serialization.json.JsonArray(targets.map { kotlinx.serialization.json.JsonPrimitive(it) })) }.toString()))

    /** The agent downloads the release labhud pins, checks it and starts again (labhud 0.3.14+). */
    suspend fun agentUpdate(id: String): ActionAnswer = LabJson.decodeFromString(answer("/api/agents/update",
        buildJsonObject { put("id", id) }.toString()))

    suspend fun alertSetup(): AlertSetup = LabJson.decodeFromString(answer("/api/setup/alerts", "{}"))

    /** Where news goes (ntfy, app, both), the rules sent back as they came. */
    suspend fun saveNotify(setup: AlertSetup, via: String): ActionAnswer {
        val body = buildJsonObject {
            put("rules", kotlinx.serialization.json.JsonArray(setup.rules))
            put("notify", buildJsonObject {
                put("via", via)
                put("quiet", kotlinx.serialization.json.JsonArray((setup.notify.quiet ?: listOf(0, 0)).map { kotlinx.serialization.json.JsonPrimitive(it) }))
            })
        }
        return LabJson.decodeFromString(answer("/api/setup/alerts-save", body.toString()))
    }

    suspend fun notifyTest(): ActionAnswer = LabJson.decodeFromString(answer("/api/setup/notify-test", "{}"))
    suspend fun unpair(name: String): ActionAnswer =
        LabJson.decodeFromString(answer("/api/v1/unpair", buildJsonObject { put("name", name) }.toString()))

    suspend fun logs(agent: String, container: String): LogsAnswer =
        LabJson.decodeFromString(answer("/api/setup/logs", buildJsonObject { put("agent", agent); put("target", container) }.toString()))

    suspend fun action(name: String): ActionAnswer =
        runCatching { LabJson.decodeFromString<ActionAnswer>(answer("/action/$name", "{}")) }
            .getOrElse { ActionAnswer(false, it.message ?: "could not reach LabHUD") }

    suspend fun actionLog(name: String, result: String, took: Long) {
        runCatching { post("/api/action-log/$name", buildJsonObject { put("result", result); put("took", took) }.toString()) }
    }

    suspend fun maintenance(card: String, minutes: Int): ActionAnswer =
        runCatching { LabJson.decodeFromString<ActionAnswer>(answer("/api/maintenance/$card", buildJsonObject { put("minutes", minutes) }.toString())) }
            .getOrElse { ActionAnswer(false, it.message ?: "could not reach LabHUD") }

    /** The live state: one value per `event: state`, until the flow is cancelled or fails. */
    fun stream(): Flow<LabState> = callbackFlow {
        val streaming = client.newBuilder().readTimeout(60, TimeUnit.SECONDS).build()
        val source = EventSources.createFactory(streaming).newEventSource(
            request("/api/v1/stream").header("Accept", "text/event-stream").build(),
            object : EventSourceListener() {
                override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                    if (type == "state") runCatching { LabJson.decodeFromString<LabState>(data) }.onSuccess { trySend(it) }
                }

                override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                    val err = if (response != null && !response.isSuccessful) runCatching { body(response) }.exceptionOrNull()
                    else null
                    close(err ?: t ?: IOException("the stream ended"))
                }

                override fun onClosed(eventSource: EventSource) {
                    close(IOException("the stream ended"))
                }
            }
        )
        awaitClose { source.cancel() }
    }

    companion object {
        fun clientFor(fingerprint: String): OkHttpClient {
            val b = OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .callTimeout(30, TimeUnit.SECONDS)
            if (fingerprint.isNotBlank()) {
                val tm = PinnedTrustManager(fingerprint)
                val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf<TrustManager>(tm), null) }
                b.sslSocketFactory(ssl.socketFactory, tm)
                // the name in the certificate does not matter: the certificate itself is pinned
                b.hostnameVerifier { _, _ -> true }
            }
            return b.build()
        }

        /**
         * The SHA-256 of the certificate [url] presents, read once without trusting it: for signing
         * in without a QR code (which would carry it). From then on only that certificate is accepted.
         */
        suspend fun firstFingerprint(url: String): String = withContext(Dispatchers.IO) {
            var seen = ""
            val tm = object : X509TrustManager {
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                    seen = chain?.firstOrNull()?.let { sha256(it.encoded) } ?: throw CertificateException("no certificate")
                }
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = throw CertificateException("not a server")
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
            val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf<TrustManager>(tm), null) }
            val c = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).callTimeout(15, TimeUnit.SECONDS)
                .sslSocketFactory(ssl.socketFactory, tm).hostnameVerifier { _, _ -> true }.build()
            c.newCall(Request.Builder().url(url.trimEnd('/') + "/api/v1/info").build()).execute().close()
            seen.ifEmpty { throw IOException("no certificate from $url") }
        }

        fun sha256(der: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(der).joinToString("") { "%02x".format(it) }
    }
}

/** Accepts exactly one certificate: the one whose SHA-256 the pairing link carried. */
class PinnedTrustManager(fingerprint: String) : X509TrustManager {
    private val want = fingerprint.lowercase().replace(":", "")

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        val leaf = chain?.firstOrNull() ?: throw CertificateException("no certificate")
        if (LabApi.sha256(leaf.encoded) != want) throw CertificateException("not the certificate LabHUD was paired with")
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) =
        throw CertificateException("not a server")

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}
