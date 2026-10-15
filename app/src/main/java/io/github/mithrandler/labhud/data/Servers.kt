package io.github.mithrandler.labhud.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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

/** Settings of this phone, not of the server. */
data class AppSettings(
    val lockMode: LockMode = LockMode.None,
    val wallOnBoot: Boolean = false,
)

private val Context.store by preferencesDataStore("labhud")

/** The paired servers, which one is shown, and this phone's settings. */
class ServerStore(private val context: Context) {
    private val serversKey = stringPreferencesKey("servers")
    private val currentKey = stringPreferencesKey("current")
    /** Before 0.1.2: a switch, on unless turned off. Read once, for those who had turned it on. */
    private val unlockKey = booleanPreferencesKey("require_unlock")
    private val lockKey = stringPreferencesKey("lock_mode")
    private val wallBootKey = booleanPreferencesKey("wall_on_boot")
    private fun tokenKey(id: String) = stringPreferencesKey("token_$id")

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
        AppSettings(lockMode = mode, wallOnBoot = p[wallBootKey] ?: false)
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

    suspend fun setLockMode(mode: LockMode) = context.store.edit { it[lockKey] = mode.name }
    suspend fun setWallOnBoot(on: Boolean) = context.store.edit { it[wallBootKey] = on }
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
