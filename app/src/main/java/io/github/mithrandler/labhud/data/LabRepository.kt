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
import kotlinx.coroutines.flow.collectLatest
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
    /**
     * Not live. [message] says why, in words; [fatal]: retrying will not help (unpaired, too old).
     * [retryAt] (elapsedRealtime) is when the next try starts, [trying] while one runs.
     */
    data class Offline(val message: String, val fatal: Boolean = false, val retryAt: Long = 0, val trying: Boolean = false) : Conn
}

/** What this phone may do on the shown server: signed in as its admin, and whether it has a PIN. */
data class Rights(val admin: Boolean = false, val pin: Boolean = false, val user: String = "")

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
    private val _liveAt = MutableStateFlow(0L)
    /** When the stream last delivered (wall-clock ms), for "last data at…" while offline. */
    val liveAt: StateFlow<Long> = _liveAt.asStateFlow()

    /** A wall page picked in the palette: Status shows it and clears this. */
    val pagePick = MutableStateFlow<String?>(null)
    private val _info = MutableStateFlow<Info?>(null)
    /** What the current server said about itself when the stream (re)started. */
    val info: StateFlow<Info?> = _info.asStateFlow()
    private val _sparks = MutableStateFlow<Map<String, List<Double?>>>(emptyMap())
    /** The last half hour of every number with a trend; refreshed while [run] is on. */
    val sparks: StateFlow<Map<String, List<Double?>>> = _sparks.asStateFlow()
    private val _uptime = MutableStateFlow<Uptime?>(null)
    /** The last 24 hours of uptime for every card; refreshed while [run] is on. */
    val uptime: StateFlow<Uptime?> = _uptime.asStateFlow()
    private var sideJob: Job? = null
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
                    _sparks.value = emptyMap()
                    _uptime.value = null
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

    /** The state as it is now, for a screen's first frame (a composable must not read [state]'s value itself). */
    fun snapshot(): LabState? = _state.value

    /** Starts the live stream for the shown server; [stop] ends it. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun run() {
        wanted = true
        if (job?.isActive == true) return
        sideJob?.cancel()
        sideJob = scope.launch {
            // the cards' sparklines (one request a minute) and uptime (every 5 minutes), as the server offers them
            launch { _info.collectLatest { i -> pollSide(i?.trends?.isNotEmpty() == true, 60_000) { api()?.sparks()?.series?.let { _sparks.value = it } }.also { if (i?.trends.isNullOrEmpty()) _sparks.value = emptyMap() } } }
            launch { _info.collectLatest { i -> pollSide(i?.uptime == true, 5 * 60_000) { api()?.uptime(false)?.let { _uptime.value = it } } } }
        }
        job = scope.launch {
            val s = store.currentNow() ?: run { _conn.value = Conn.Idle; return@launch }
            val api = api(s) ?: run { _conn.value = Conn.Offline("This phone has no token for ${s.name}: pair it again.", true); return@launch }
            var wait = 2_000L
            while (true) {
                // after a failure the reason stays on screen while the next try runs
                _conn.value = (_conn.value as? Conn.Offline)?.takeIf { !it.fatal }?.copy(trying = true, retryAt = 0) ?: Conn.Connecting
                try {
                    api.info().also { _info.value = it; _rights.value = Rights(it.deviceAdmin, it.pin, it.user.orEmpty()) }
                    api.stream().collect { st ->
                        _conn.value = Conn.Live
                        _liveAt.value = System.currentTimeMillis()
                        wait = 2_000L
                        // the same document again (nothing changed but the clock) redraws nothing
                        val shared = st.sharedWith(_state.value)
                        if (shared != _state.value) _state.value = shared
                        keep(s.id, st)
                        onNewState?.invoke(s, st)
                    }
                    _conn.value = Conn.Offline("The connection to ${s.name} ended.")
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
                (_conn.value as? Conn.Offline)?.let { _conn.value = it.copy(retryAt = android.os.SystemClock.elapsedRealtime() + wait, trying = false) }
                delay(wait)
                wait = (wait * 2).coerceAtMost(30_000L)
            }
        }
    }

    /** Runs [block] now and then every [every] ms while [on]; a failed round waits for the next one. */
    private suspend fun pollSide(on: Boolean, every: Long, block: suspend () -> Unit) {
        if (!on) return
        while (true) {
            runCatching { block() }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
            delay(every)
        }
    }

    fun stop() {
        wanted = false
        sideJob?.cancel()
        sideJob = null
        flush()
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

    /** The live unlock ticket, for the wall page in Wall mode (it is the same PIN, the same idle time). Null without one. */
    fun unlockTicket(): String? = liveTicket()

    /** Locks the buttons again at once (the ticket is forgotten); the admin sign-in stays. */
    fun lockNow() = dropTicket()

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
        r.user?.let { u -> _rights.value = _rights.value.copy(user = u) }
        return null
    }

    /**
     * Sets, changes or removes (new = null) the wall's PIN; null when it worked, else what labhud said. Every ticket ends with the change, so
     * after a set the phone unlocks again with the new one, and the rights are read anew.
     */
    suspend fun changePin(old: String, new: String?): String? {
        val api = api() ?: return "No server paired."
        val r = runCatching { api.setPin(old, new) }.getOrElse { return it.message ?: "Could not reach LabHUD." }
        if (!r.ok) return if (r.wait > 0) "Wrong PIN. Wait ${r.wait.toInt() + 1} s." else r.error.ifBlank { "Refused." }
        dropTicket()
        refreshRights()
        if (new != null) unlock(new)
        return null
    }

    /** Signs this phone in as labhud's admin; null when it worked, else what labhud said. */
    suspend fun signIn(user: String, password: String, code: String): String? {
        val api = api() ?: return "No server paired."
        val r = runCatching { api.adminLogin(user, password, code) }.getOrElse { return it.message ?: "Could not reach LabHUD." }
        if (!r.ok) return if (r.wait > 0) "Wrong user, password or code. Wait ${r.wait.toInt() + 1} s." else r.error.ifBlank { "Refused." }
        _rights.value = Rights(r.deviceAdmin, r.pin, r.user.orEmpty())
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
        runCatching { api()?.info() }.getOrNull()?.let { _info.value = it; _rights.value = Rights(it.deviceAdmin, it.pin, it.user.orEmpty()) }
    }

    suspend fun servers(): List<Server> = store.servers.first()

    private fun reach(s: Server, e: IOException): String = Companion.reach(s.url, e)

    companion object {
        private const val KEEP_EVERY = 60_000L

        /** Why [url] could not be reached, in words a person can act on, from the exception's chain. */
        fun reach(url: String, e: Throwable): String {
            val chain = generateSequence(e) { it.cause }.take(6).toList()
            val why = chain.joinToString(" ") { it.message.orEmpty() }
            fun has(k: Class<*>) = chain.any { k.isInstance(it) }
            return when {
                "CLEARTEXT" in why -> "Android refused plain HTTP to $url."
                "certificate" in why.lowercase() || has(javax.net.ssl.SSLPeerUnverifiedException::class.java) ->
                    "$url presented a different certificate than when it was paired. If LabHUD got a new one, pair again."
                has(java.net.UnknownHostException::class.java) -> "The name in $url is not known here: no network, or a name only your home DNS knows."
                has(java.net.NoRouteToHostException::class.java) -> "No route to $url: this phone is not on your home network or VPN."
                has(java.net.ConnectException::class.java) && "refused" in why.lowercase() ->
                    "$url refused the connection: LabHUD is not running there, or not on that port."
                has(java.net.SocketTimeoutException::class.java) || "timeout" in why.lowercase() ->
                    "No answer from $url. Either LabHUD is off, or this phone cannot get there (not on your home network or VPN, or a firewall in between)."
                has(javax.net.ssl.SSLException::class.java) -> "$url does not speak HTTPS the way it did when paired: ${e.message.orEmpty().take(80)}"
                else -> "Cannot reach $url (${e.message.orEmpty().take(80)}). Are you on your home network or VPN?"
            }
        }
    }

    private fun file(id: String) = File(context.filesDir, "state-$id.json")

    suspend fun cached(id: String): LabState? = withContext(Dispatchers.IO) {
        runCatching { LabJson.decodeFromString<LabState>(file(id).readText()) }.getOrNull()
    }

    /** The newest state not on disk yet, and when the last one went there (elapsedRealtime). */
    private var unsaved: Pair<String, LabState>? = null
    private var savedAt = 0L

    /** The state arrives up to once a second; the disk gets it once a minute, and at [stop]. */
    suspend fun keep(id: String, st: LabState, now: Boolean = false) {
        unsaved = id to st
        if (now || android.os.SystemClock.elapsedRealtime() - savedAt >= KEEP_EVERY) flushNow()
    }

    private suspend fun flushNow() = withContext(Dispatchers.IO) {
        val (id, st) = unsaved ?: return@withContext
        unsaved = null
        savedAt = android.os.SystemClock.elapsedRealtime()
        runCatching {
            val tmp = File(context.filesDir, "state-$id.json.tmp")
            tmp.writeText(LabJson.encodeToString(st))
            tmp.renameTo(file(id))
        }
    }

    /** Writes what the debounce still holds (the app went to the background or the server changed). */
    fun flush() {
        if (unsaved != null) scope.launch { flushNow() }
    }

    fun forget(id: String) {
        if (unsaved?.first == id) unsaved = null
        file(id).delete()
    }
}
