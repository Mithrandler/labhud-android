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
class LabApi(val baseUrl: String, private val token: String?, fingerprint: String = "") {
    private val base = baseUrl.trimEnd('/')
    val client: OkHttpClient = clientFor(fingerprint)

    private fun request(path: String) = Request.Builder().url(base + path).apply {
        if (token != null) header("Authorization", "Bearer $token")
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

    suspend fun action(name: String): ActionAnswer =
        runCatching { LabJson.decodeFromString<ActionAnswer>(post("/action/$name", "{}")) }
            .getOrElse { ActionAnswer(false, it.message ?: "could not reach LabHUD") }

    suspend fun actionLog(name: String, result: String, took: Long) {
        runCatching { post("/api/action-log/$name", buildJsonObject { put("result", result); put("took", took) }.toString()) }
    }

    suspend fun maintenance(card: String, minutes: Int): ActionAnswer =
        runCatching { LabJson.decodeFromString<ActionAnswer>(post("/api/maintenance/$card", buildJsonObject { put("minutes", minutes) }.toString())) }
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
