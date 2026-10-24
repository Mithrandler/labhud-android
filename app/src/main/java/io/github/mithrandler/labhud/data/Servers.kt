package io.github.mithrandler.labhud.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** One paired labhud. The token is not here: it is kept encrypted, see [TokenCrypto]. */
@Serializable
data class Server(
    val id: String,
    val name: String,
    val url: String,
    /** SHA-256 of labhud's own certificate (hex), from the pairing link; "" = the system's trust. */
    val fingerprint: String = "",
    val deviceName: String = "",
)

/** How the app is locked: not at all, with the phone's screen lock, or with a fingerprint. */
enum class LockMode { None, ScreenLock, Fingerprint }

/** The app's look: the phone's (System), dark, light, or the wall's own black HUD. Wall mode is always HUD. */
enum class ThemeMode { System, Dark, Light, Hud }

/** Settings of this phone, not of the server. */
data class AppSettings(
    val lockMode: LockMode = LockMode.None,
    val wallOnBoot: Boolean = false,
    /** Wall mode shows the glance view (?glance=1): big tiles, one number each. */
    val wallGlance: Boolean = true,
    val notify: NotifyMode = NotifyMode.Off,
    val theme: ThemeMode = ThemeMode.Hud,
    /** Text size in percent (85 to 130, steps of 5) and whether the font is the monospace one: this phone only, like the theme. */
    val textScale: Int = 100,
    val monoFont: Boolean = false,
    /** The HUD base this phone uses instead of the board's (oled, grey, paper, terminal, glass); null follows the board. */
    val baseOverride: String? = null,
    /** What this phone opens as (the wall page has its own, [wallGlance]). */
    val phoneView: io.github.mithrandler.labhud.ui.ViewChoice = io.github.mithrandler.labhud.ui.ViewChoice.Auto,
    /** Wall mode dims the screen in a window of the night. */
    val screen: io.github.mithrandler.labhud.wall.ScreenSchedule = io.github.mithrandler.labhud.wall.ScreenSchedule(),
)

/**
 * How this phone hears news: Instant (UnifiedPush: labhud wakes the app, the app reads the news
 * itself), Ntfy (the ntfy app shows labhud's own messages; nothing here), or Off.
 */
enum class NotifyMode { Instant, Ntfy, Off }

private val Context.store by preferencesDataStore("labhud")

/** The paired servers, which one is shown, and this phone's settings. */
class ServerStore(private val context: Context) {
    private val serversKey = stringPreferencesKey("servers")
    private val currentKey = stringPreferencesKey("current")
    /** An old switch, on unless turned off. Read once, for those who had turned it on. */
    private val unlockKey = booleanPreferencesKey("require_unlock")
    private val lockKey = stringPreferencesKey("lock_mode")
    private val wallBootKey = booleanPreferencesKey("wall_on_boot")
    private val wallGlanceKey = booleanPreferencesKey("wall_glance")
    private val themeKey = stringPreferencesKey("theme_mode")
    private val textScaleKey = intPreferencesKey("text_scale")
    private val monoKey = booleanPreferencesKey("mono_font")
    private val baseOverrideKey = stringPreferencesKey("base_override")
    private val phoneViewKey = stringPreferencesKey("phone_view")
    private val screenOnKey = booleanPreferencesKey("screen_schedule")
    private val screenFromKey = androidx.datastore.preferences.core.intPreferencesKey("screen_from")
    private val screenToKey = androidx.datastore.preferences.core.intPreferencesKey("screen_to")
    private val screenDimKey = androidx.datastore.preferences.core.intPreferencesKey("screen_dim")
    private val favSectionsKey = androidx.datastore.preferences.core.stringSetPreferencesKey("fav_sections")
    private fun favCardsKey(id: String) = androidx.datastore.preferences.core.stringSetPreferencesKey("fav_cards_$id")
    private fun tokenKey(id: String) = stringPreferencesKey("token_$id")
    private fun pinKey(id: String) = stringPreferencesKey("pin_$id")
    private fun lastNoteKey(id: String) = androidx.datastore.preferences.core.longPreferencesKey("last_note_$id")
    private fun dismissedKey(id: String) = androidx.datastore.preferences.core.stringSetPreferencesKey("dismissed_$id")
    private fun clearedNotesKey(id: String) = androidx.datastore.preferences.core.longPreferencesKey("cleared_notes_$id")
    private val notifyKey = stringPreferencesKey("notify_mode")
    private val pushKey = stringPreferencesKey("push_endpoint")

    private fun decode(p: Preferences): List<Server> =
        p[serversKey]?.let { runCatching { LabJson.decodeFromString<List<Server>>(it) }.getOrNull() } ?: emptyList()

    val servers: Flow<List<Server>> = context.store.data.map(::decode)

    val current: Flow<Server?> = context.store.data.map { p ->
        val all = decode(p)
        all.firstOrNull { it.id == p[currentKey] } ?: all.firstOrNull()
    }

    val settings: Flow<AppSettings> = context.store.data.map { p ->
        val mode = p[lockKey]?.let { runCatching { LockMode.valueOf(it) }.getOrNull() }
            ?: if (p[unlockKey] == true) LockMode.ScreenLock else LockMode.None
        AppSettings(lockMode = mode, wallOnBoot = p[wallBootKey] ?: false, wallGlance = p[wallGlanceKey] ?: true,
            notify = p[notifyKey]?.let { runCatching { NotifyMode.valueOf(it) }.getOrNull() } ?: NotifyMode.Off,
            theme = p[themeKey]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.Hud,
            textScale = io.github.mithrandler.labhud.ui.textScaleOf(p[textScaleKey] ?: 100), monoFont = p[monoKey] ?: false, baseOverride = p[baseOverrideKey],
            phoneView = p[phoneViewKey]?.let { runCatching { io.github.mithrandler.labhud.ui.ViewChoice.valueOf(it) }.getOrNull() }
                ?: io.github.mithrandler.labhud.ui.ViewChoice.Auto,
            screen = io.github.mithrandler.labhud.wall.ScreenSchedule().let { d ->
                d.copy(enabled = p[screenOnKey] ?: d.enabled, from = p[screenFromKey] ?: d.from, to = p[screenToKey] ?: d.to, dim = p[screenDimKey] ?: d.dim)
            })
    }

    suspend fun currentNow(): Server? = current.first()

    suspend fun add(server: Server, token: String) {
        val sealed = TokenCrypto.seal(token)
        context.store.edit { p ->
            p[serversKey] = LabJson.encodeToString(decode(p).filter { it.id != server.id } + server)
            p[tokenKey(server.id)] = sealed
            p[currentKey] = server.id
        }
    }

    suspend fun remove(id: String) {
        context.store.edit { p ->
            p[serversKey] = LabJson.encodeToString(decode(p).filter { it.id != id })
            p.remove(tokenKey(id))
            p.remove(pinKey(id))
            p.remove(lastNoteKey(id))
            p.remove(clearedNotesKey(id))
            p.remove(favCardsKey(id))
            p.remove(dismissedKey(id))
            if (p[currentKey] == id) p.remove(currentKey)
        }
    }

    suspend fun rename(id: String, name: String) {
        context.store.edit { p ->
            p[serversKey] = LabJson.encodeToString(decode(p).map { if (it.id == id) it.copy(name = name) else it })
        }
    }

    suspend fun select(id: String) {
        context.store.edit { it[currentKey] = id }
    }

    suspend fun token(id: String): String? =
        context.store.data.first()[tokenKey(id)]?.let { runCatching { TokenCrypto.open(it) }.getOrNull() }

    /** The display's PIN sealed behind a fingerprint (lock/PinVault), or null. */
    suspend fun sealedPin(id: String): String? = context.store.data.first()[pinKey(id)]
    suspend fun setSealedPin(id: String, sealed: String?) = context.store.edit { if (sealed == null) it.remove(pinKey(id)) else it[pinKey(id)] = sealed }

    /** The id of the newest notification already shown for a server (the outbox's ids rise). */
    suspend fun lastNote(id: String): Long? = context.store.data.first()[lastNoteKey(id)]
    /** The time (Unix seconds) up to which this phone has cleared the Notifications list; 0 = never. */
    suspend fun clearedNotes(id: String): Long = context.store.data.first()[clearedNotesKey(id)] ?: 0L
    suspend fun setClearedNotes(id: String, time: Long) = context.store.edit { it[clearedNotesKey(id)] = time }
    suspend fun setLastNote(id: String, last: Long) = context.store.edit { it[lastNoteKey(id)] = last }

    suspend fun setNotify(mode: NotifyMode) = context.store.edit { it[notifyKey] = mode.name }
    /** The UnifiedPush endpoint this phone has now (one for every server: each gets it). */
    suspend fun pushEndpoint(): String? = context.store.data.first()[pushKey]
    suspend fun setPushEndpoint(url: String?) = context.store.edit { if (url == null) it.remove(pushKey) else it[pushKey] = url }

    suspend fun setLockMode(mode: LockMode) = context.store.edit { it[lockKey] = mode.name }
    suspend fun setTheme(mode: ThemeMode) = context.store.edit { it[themeKey] = mode.name }
    suspend fun setTextScale(percent: Int) = context.store.edit { it[textScaleKey] = io.github.mithrandler.labhud.ui.textScaleOf(percent) }
    suspend fun setMonoFont(on: Boolean) = context.store.edit { it[monoKey] = on }
    suspend fun setBaseOverride(base: String?) = context.store.edit { if (base == null) it.remove(baseOverrideKey) else it[baseOverrideKey] = base }
    suspend fun setWallOnBoot(on: Boolean) = context.store.edit { it[wallBootKey] = on }
    suspend fun setWallGlance(on: Boolean) = context.store.edit { it[wallGlanceKey] = on }
    suspend fun setScreen(v: io.github.mithrandler.labhud.wall.ScreenSchedule) = context.store.edit {
        it[screenOnKey] = v.enabled
        it[screenFromKey] = v.from.coerceIn(0, 1439)
        it[screenToKey] = v.to.coerceIn(0, 1439)
        it[screenDimKey] = v.dim.coerceIn(0, 100)
    }
    suspend fun setPhoneView(v: io.github.mithrandler.labhud.ui.ViewChoice) = context.store.edit { it[phoneViewKey] = v.name }

    /** The settings sections starred on this phone (any server). */
    val favoriteSections: Flow<Set<String>> = context.store.data.map { it[favSectionsKey] ?: emptySet() }
    suspend fun toggleFavoriteSection(id: String) = context.store.edit {
        val now = it[favSectionsKey] ?: emptySet()
        it[favSectionsKey] = if (id in now) now - id else now + id
    }

    /** The alerts and problems the bell was told to forget on this phone, for one server (see ui/Dismissals.kt). */
    fun dismissed(serverId: String): Flow<Set<String>> = context.store.data.map { it[dismissedKey(serverId)] ?: emptySet() }
    suspend fun setDismissed(serverId: String, keys: Set<String>) = context.store.edit {
        if (keys.isEmpty()) it.remove(dismissedKey(serverId)) else it[dismissedKey(serverId)] = keys
    }

    /** The cards starred on this phone for one server, shown first. */
    fun favoriteCards(serverId: String): Flow<Set<String>> = context.store.data.map { it[favCardsKey(serverId)] ?: emptySet() }
    suspend fun toggleFavoriteCard(serverId: String, card: String) = context.store.edit {
        val now = it[favCardsKey(serverId)] ?: emptySet()
        it[favCardsKey(serverId)] = if (card in now) now - card else now + card
    }
}

/**
 * Tokens sealed with an AES-GCM key that lives in the Android Keystore and never leaves it: a
 * copy of the app's files (a backup, a rooted read) holds only ciphertext.
 */
object TokenCrypto {
    private const val ALIAS = "labhud-tokens"
    private const val TRANSFORM = "AES/GCM/NoPadding"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    fun seal(plain: String): String {
        val c = Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val out = c.iv + c.doFinal(plain.toByteArray())
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    fun open(sealed: String): String {
        val raw = Base64.decode(sealed, Base64.NO_WRAP)
        val c = Cipher.getInstance(TRANSFORM)
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw, 0, 12))
        return String(c.doFinal(raw, 12, raw.size - 12))
    }
}
