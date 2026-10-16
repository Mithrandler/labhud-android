package io.github.mithrandler.labhud.lock

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The display's PIN, kept so a fingerprint can stand in for it: sealed with an AES key in the
 * Android Keystore that opens only after a strong biometric, for that one use. A new fingerprint
 * enrolled on the phone voids the key (and so the kept PIN): the PIN is asked again.
 */
object PinVault {
    private const val ALIAS = "labhud-pin"
    private const val TRANSFORM = "AES/GCM/NoPadding"

    fun available(activity: FragmentActivity): Boolean =
        BiometricManager.from(activity).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) spec.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply { init(spec.build()) }.generateKey()
    }

    private fun drop() {
        runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(ALIAS) }
    }

    private fun cipher(mode: Int, iv: ByteArray? = null): Cipher? {
        repeat(2) {
            try {
                return Cipher.getInstance(TRANSFORM).apply {
                    if (iv == null) init(mode, key()) else init(mode, key(), GCMParameterSpec(128, iv))
                }
            } catch (_: KeyPermanentlyInvalidatedException) {
                drop()
                if (iv != null) return null   // what it sealed is gone with it
            }
        }
        return null
    }

    /** Seals [pin] behind a fingerprint; [done] gets the sealed text, or null when it was not done. */
    fun seal(activity: FragmentActivity, pin: String, done: (String?) -> Unit) {
        val c = cipher(Cipher.ENCRYPT_MODE) ?: return done(null)
        prompt(activity, "Use your fingerprint for the PIN", c) { ok ->
            done(ok?.let { Base64.encodeToString(it.iv + it.doFinal(pin.toByteArray()), Base64.NO_WRAP) })
        }
    }

    /** Opens a sealed PIN with a fingerprint; [done] gets it, or null (cancelled, voided). */
    fun open(activity: FragmentActivity, sealed: String, done: (String?) -> Unit) {
        val raw = runCatching { Base64.decode(sealed, Base64.NO_WRAP) }.getOrNull() ?: return done(null)
        if (raw.size < 13) return done(null)
        val c = cipher(Cipher.DECRYPT_MODE, raw.copyOfRange(0, 12)) ?: return done(null)
        prompt(activity, "Unlock the buttons", c) { ok ->
            done(ok?.let { runCatching { String(it.doFinal(raw, 12, raw.size - 12)) }.getOrNull() })
        }
    }

    private fun prompt(activity: FragmentActivity, title: String, c: Cipher, done: (Cipher?) -> Unit) {
        val p = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = done(result.cryptoObject?.cipher)
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = done(null)
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle("Instead of the wall's PIN")
            .setNegativeButtonText("Type the PIN")
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .build()
        p.authenticate(info, BiometricPrompt.CryptoObject(c))
    }
}
