package io.github.mithrandler.labhud.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** How the live connection is doing. */
sealed interface Conn {
    data object Idle : Conn
    data object Connecting : Conn
    data object Live : Conn
    data class Offline(val message: String, val fatal: Boolean = false) : Conn
}

/** What this phone may do on the shown server: signed in as its admin, and whether it has a PIN. */
data class Rights(val admin: Boolean = false, val pin: Boolean = false)

/**
 * The shown server's state: the last one kept on disk at once, then live through the stream for
 * as long as the app is open and unlocked ([run]). Nothing runs in the background: the widget's
 * worker reads the state on its own (see widget/RefreshWorker), and notifications come from ntfy.
 */
class LabRepository(private val context: Context, val store: ServerStore) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow<LabState?>(null)
    private val _conn = MutableStateFlow<Conn>(Conn.Idle)
    private val _server = MutableStateFlow<Server?>(null)
    val state: StateFlow<LabState?> = _state.asStateFlow()
    val conn: StateFlow<Conn> = _conn.asStateFlow()
    val server: StateFlow<Server?> = _server.asStateFlow()
    private val _info = MutableStateFlow<Info?>(null)
    /** What the current server said about itself when the stream (re)started. */
    val info: StateFlow<Info?> = _info.asStateFlow()
    private val _rights = MutableStateFlow(Rights())
    /** From /api/v1/info when the stream starts, and from every sign-in or sign-out. */
    val rights: StateFlow<Rights> = _rights.asStateFlow()
    /** The display's unlock ticket (with a PIN): in memory only, gone with the process or after its idle time. */
    private var ticket: String? = null
    private var ticketUntil = 0L
    private var ticketIdle = 120_000L
    private var job: Job? = null
    /** run() was asked for (app in front and unlocked); a new or other server restarts it. */
    private var wanted = false
    var onNewState: ((Server, LabState) -> Unit)? = null

    init {
        // the shown server and its kept state, even before the stream runs (locked, background)
        scope.launch {
            store.current.distinctUntilChanged().collect { s ->
                if (_server.value?.id != s?.id) {
                    _rights.value = Rights()
                    dropTicket()
                }
                _server.value = s
                _state.value = s?.let { cached(it.id) }
                if (wanted) {
                    job?.cancel()
                    job = null
                    run()
                }
            }
        }
    }

    /** Starts the live stream for the shown server; [stop] ends it. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun run() {
        wanted = true
        if (job?.isActive == true) return
        job = scope.launch {
            val s = store.currentNow() ?: run { _conn.value = Conn.Idle; return@launch }
            val api = api(s) ?: run { _conn.value = Conn.Offline("This phone has no token for ${s.name}: pair it again.", true); return@launch }
            var wait = 2_000L
            while (true) {
                _conn.value = Conn.Connecting
                try {
                    api.info().also { _info.value = it; _rights.value = Rights(it.deviceAdmin, it.pin) }
                    api.stream().collect { st ->
                        _conn.value = Conn.Live
                        wait = 2_000L
                        _state.value = st
                        keep(s.id, st)
                        onNewState?.invoke(s, st)
                    }
                    _conn.value = Conn.Offline("The connection ended.")
                } catch (e: TooOldException) {
                    _conn.value = Conn.Offline(e.message ?: "", true)
                    return@launch
                } catch (e: ApiException) {
                    val fatal = e.code in listOf(401, 403, 404, 421)
                    _conn.value = Conn.Offline(e.message ?: "", fatal)
                    if (fatal) return@launch
                } catch (e: IOException) {
                    _conn.value = Conn.Offline(reach(s, e))
                }
                delay(wait)
                wait = (wait * 2).coerceAtMost(30_000L)
            }
        }
    }

    fun stop() {
        wanted = false
        job?.cancel()
        job = null
        _conn.value = Conn.Idle
    }

    /** Try again now (a pull, a tap on the offline banner). */
    fun retry() {
        stop()
        run()
    }

    suspend fun api(s: Server? = null): LabApi? {
        val server = s ?: store.currentNow() ?: return null
        val token = store.token(server.id) ?: return null
        return LabApi(server.url, token, server.fingerprint, if (s == null || s.id == _server.value?.id) liveTicket() else null)
    }

    /** True when a button may go now: signed in as the admin, and no PIN or a live ticket. */
    fun mayPress(): Boolean = _rights.value.admin && (!_rights.value.pin || liveTicket() != null)

    private fun liveTicket(): String? {
        val now = android.os.SystemClock.elapsedRealtime()
        val t = ticket ?: return null
        if (now > ticketUntil) { dropTicket(); return null }
        ticketUntil = now + ticketIdle   // each use keeps it, as on the display
        return t
    }

    private fun dropTicket() {
        ticket = null
        ticketUntil = 0L
    }

    /** The display's PIN for a ticket; null when it worked, else what labhud said. */
    suspend fun unlock(pin: String): String? {
        val api = api() ?: return "No server paired."
        val r = runCatching { api.unlock(pin) }.getOrElse { return it.message ?: "Could not reach LabHUD." }
        if (!r.ok) return if (r.wait > 0) "Wrong PIN. Wait ${r.wait.toInt() + 1} s." else r.error.ifBlank { "Wrong PIN." }
        ticketIdle = r.idle * 1000L
        ticket = r.ticket
        ticketUntil = android.os.SystemClock.elapsedRealtime() + ticketIdle
        return null
    }

    /** Signs this phone in as labhud's admin; null when it worked, else what labhud said. */
    suspend fun signIn(user: String, password: String, code: String): String? {
        val api = api() ?: return "No server paired."
        val r = runCatching { api.adminLogin(user, password, code) }.getOrElse { return it.message ?: "Could not reach LabHUD." }
        if (!r.ok) return if (r.wait > 0) "Wrong user, password or code. Wait ${r.wait.toInt() + 1} s." else r.error.ifBlank { "Refused." }
        _rights.value = Rights(r.deviceAdmin, r.pin)
        return null
    }

    suspend fun signOut(): String? {
        val api = api() ?: return "No server paired."
        val r = runCatching { api.adminLogout() }.getOrElse { return it.message ?: "Could not reach LabHUD." }
        dropTicket()
        _rights.value = Rights(false, r.pin)
        return null
    }

    /** Reads the rights again (after the admin changed on the server, a PIN set…). */
    suspend fun refreshRights() {
        runCatching { api()?.info() }.getOrNull()?.let { _info.value = it; _rights.value = Rights(it.deviceAdmin, it.pin) }
    }

    suspend fun servers(): List<Server> = store.servers.first()

    private fun reach(s: Server, e: IOException): String {
        val why = e.message.orEmpty()
        return when {
            "CLEARTEXT" in why -> "Android refused plain HTTP to ${s.url}."
            "certificate" in why.lowercase() -> "${s.url} presented a different certificate than when it was paired."
            else -> "Cannot reach ${s.url}. Are you on your home network or VPN?"
        }
    }

    private fun file(id: String) = File(context.filesDir, "state-$id.json")

    suspend fun cached(id: String): LabState? = withContext(Dispatchers.IO) {
        runCatching { LabJson.decodeFromString<LabState>(file(id).readText()) }.getOrNull()
    }

    suspend fun keep(id: String, st: LabState) = withContext(Dispatchers.IO) {
        runCatching {
            val tmp = File(context.filesDir, "state-$id.json.tmp")
            tmp.writeText(LabJson.encodeToString(st))
            tmp.renameTo(file(id))
        }
    }

    fun forget(id: String) {
        file(id).delete()
    }
}
