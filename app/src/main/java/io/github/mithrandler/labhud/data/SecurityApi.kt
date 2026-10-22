package io.github.mithrandler.labhud.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** ⚙ > Security as POST /api/security/get answers it (api/security.py): no secret, only whether one is set. */
@Serializable
data class SecurityState(
    val ok: Boolean = false,
    val error: String = "",
    val kept: Boolean = false,
    @SerialName("session_hours") val sessionHours: Int = 720,
    @SerialName("pin_minutes") val pinMinutes: Int = 2,
    @SerialName("pin_on") val pinOn: Boolean = false,
    @SerialName("trusted_proxies") val trustedProxies: List<String> = emptyList(),
    @SerialName("admin_networks") val adminNetworks: List<String> = emptyList(),
    @SerialName("your_ip") val yourIp: String = "",
    @SerialName("via_proxy") val viaProxy: Boolean = false,
    val oidc: OidcSetup? = null,
    @SerialName("secret_set") val secretSet: Boolean = false,
    val callback: String = "",
)

@Serializable
data class OidcSetup(
    val issuer: String = "",
    @SerialName("client_id") val clientId: String = "",
    val allow: List<String> = emptyList(),
    @SerialName("allow_groups") val allowGroups: List<String> = emptyList(),
    @SerialName("redirect_uri") val redirectUri: String? = null,
)

@Serializable
data class OidcStart(val ok: Boolean = false, val error: String = "", val url: String = "", val bind: String = "")

@Serializable
private data class PageConfig(val admin: AdminFlags = AdminFlags())

@Serializable
private data class AdminFlags(val on: Boolean = false, val oidc: Boolean = false)

suspend fun LabApi.security(): SecurityState = LabJson.decodeFromString(postAnswer("/api/security/get", "{}"))

/** session_hours and pin_minutes (the rest of ⚙ > Security is typed on the wall). */
suspend fun LabApi.saveTimeouts(sessionHours: Int, pinMinutes: Int): ActionAnswer = LabJson.decodeFromString(
    postAnswer("/api/security/save", buildJsonObject { put("session_hours", sessionHours); put("pin_minutes", pinMinutes) }.toString()))

/** Does this LabHUD offer single sign-on? (/api/config, which a paired phone may read.) */
suspend fun LabApi.oidcOffered(): Boolean =
    runCatching { LabJson.decodeFromString<PageConfig>(getText("/api/config")).admin.oidc }.getOrDefault(false)

suspend fun LabApi.oidcStart(): OidcStart =
    LabJson.decodeFromString(postAnswer("/api/admin/oidc/start", buildJsonObject { put("mode", "app") }.toString()))

suspend fun LabApi.oidcFinish(r: String, bind: String): AdminAnswer =
    LabJson.decodeFromString(postAnswer("/api/admin/oidc/finish", buildJsonObject { put("r", r); put("bind", bind) }.toString()))

/**
 * Signing this phone in as the admin with single sign-on: start asks the server for the provider's
 * address and a `bind` secret, kept here in memory only; the browser signs in at the provider and
 * labhud sends it back to labhud://oidc?r=<one-time id>; finish trades r + bind, over this phone's
 * own token, for the admin's rights. r alone (it was in a URL) is worth nothing: the server wants the
 * same phone and the bind, within two minutes.
 */
object Sso {
    private var bind: String? = null
    private var serverId: String? = null
    private var startedAt = 0L

    /** Opens the provider in the browser; null, or why not. */
    suspend fun start(context: Context, repo: LabRepository): String? {
        val server = repo.servers().firstOrNull { it.id == repo.server.value?.id } ?: return "No server paired."
        val api = repo.api(server) ?: return "No server paired."
        val r = runCatching { api.oidcStart() }.getOrElse { return it.message ?: "Could not reach LabHUD." }
        if (!r.ok) return if (r.error == "forbidden") "This LabHUD refused single sign-on from here." else r.error.ifBlank { "Refused." }
        bind = r.bind
        serverId = server.id
        startedAt = System.currentTimeMillis()
        return runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(r.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            null
        }.getOrElse { "No browser on this phone to sign in with." }
    }

    /** labhud://oidc?r=… or ?error=…: the message to show. */
    suspend fun finish(link: Uri, repo: LabRepository): String {
        val error = link.getQueryParameter("error")
        val r = link.getQueryParameter("r")
        val b = bind
        val sid = serverId
        bind = null; serverId = null
        if (error != null) return "Single sign-on: " + when (error) {
            "denied" -> "the provider said no."
            "refused" -> "that user may not be this LabHUD's admin."
            "network" -> "not from one of the admin networks."
            else -> "it did not work (see LabHUD's log)."
        }
        if (r == null || b == null || sid == null || System.currentTimeMillis() - startedAt > 10 * 60_000L)
            return "Single sign-on: start it again from Settings › Security."
        val server = repo.servers().firstOrNull { it.id == sid } ?: return "That server is gone."
        val api = repo.api(server) ?: return "That server is gone."
        val a = runCatching { api.oidcFinish(r, b) }.getOrElse { return it.message ?: "Could not reach LabHUD." }
        if (!a.ok) return "Single sign-on: expired. Start it again."
        repo.refreshRights()
        return "Signed in as the admin (single sign-on)."
    }
}

/** The wall's PIN: [new] is 4 digits, or null to remove it; [old] is the current one (none when there is none yet). The server asks the admin level. */
suspend fun LabApi.setPin(old: String, new: String?): AdminAnswer = LabJson.decodeFromString(
    postAnswer("/api/lock/pin", buildJsonObject { put("old", old); if (new == null) put("new", kotlinx.serialization.json.JsonNull) else put("new", new) }.toString()))

/** Whether a new PIN may be sent: exactly 4 digits, typed twice the same. */
fun pinOk(new: String, again: String) = Regex("[0-9]{4}").matches(new) && new == again
