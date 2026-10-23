package io.github.mithrandler.labhud.lock

import android.os.Build
import android.os.SystemClock
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import io.github.mithrandler.labhud.data.LockMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The app's lock, chosen in Settings: none (the default), the phone's screen lock, or a
 * fingerprint. Asked once when the app opens, then nothing more (actions included) until the app
 * has spent [IDLE_MS] in the background.
 */
object AppLock {
    const val IDLE_MS = 5 * 60_000L
    private val _unlocked = MutableStateFlow(true)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()
    private var hiddenAt = 0L
    private var known = false

    /** Called when the app comes back after more than [IDLE_MS] in the background, lock or no lock: the held PIN and ticket go. */
    var onIdleExpired: (() -> Unit)? = null

    /** Set from the stored settings. The first value read decides whether the app opens locked. */
    var mode: LockMode = LockMode.None
        set(value) {
            field = value
            if (value == LockMode.None) _unlocked.value = true
            else if (!known) _unlocked.value = false
            known = true
        }

    val on: Boolean get() = mode != LockMode.None

    /** Android 10 cannot mix a strong biometric with the screen lock in one prompt; a weak one it can. */
    private val strong = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) BIOMETRIC_STRONG else BIOMETRIC_WEAK

    /** The screen lock alone needs Android 11; on 10 the prompt also offers the fingerprint. */
    private val screenLock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) DEVICE_CREDENTIAL else BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    private fun authenticators(m: LockMode) = if (m == LockMode.Fingerprint) strong else screenLock

    fun onHidden() {
        hiddenAt = SystemClock.elapsedRealtime()
    }

    fun onShown() {
        if (hiddenAt != 0L && SystemClock.elapsedRealtime() - hiddenAt > IDLE_MS) {
            onIdleExpired?.invoke()
            if (on) _unlocked.value = false
        }
    }

    fun lockNow() {
        if (on) _unlocked.value = false
    }

    /** Whether this phone can do [m]: a screen lock set, a fingerprint enrolled. */
    fun canUse(activity: FragmentActivity, m: LockMode): Boolean =
        m == LockMode.None || BiometricManager.from(activity).canAuthenticate(authenticators(m)) == BiometricManager.BIOMETRIC_SUCCESS

    /**
     * Opens the app. With a fingerprint and the wall's PIN already sealed ([sealedPin]), the one fingerprint prompt
     * also opens the sealed PIN ([onPin] gets it), so the buttons are unlocked with the app; if that prompt is
     * cancelled, the plain prompt (the phone's screen lock as its other button) asks instead.
     */
    fun prompt(activity: FragmentActivity, sealedPin: String? = null, onPin: (String) -> Unit = {}, onError: (String) -> Unit) {
        if (!on || !canUse(activity, mode)) {
            _unlocked.value = true
            return
        }
        if (mode == LockMode.Fingerprint && sealedPin != null) {
            PinVault.open(activity, sealedPin, "Unlock LabHUD") { pin ->
                if (pin != null) { _unlocked.value = true; onPin(pin) }
                else ask(activity, mode, onOk = { _unlocked.value = true }, onError = onError)
            }
            return
        }
        ask(activity, mode, onOk = { _unlocked.value = true }, onError = onError)
    }

    /**
     * One prompt for [m]. A fingerprint prompt offers the screen lock as its other button, so a
     * cut finger or a locked-out sensor never locks you out of the app.
     */
    fun ask(activity: FragmentActivity, m: LockMode, onOk: () -> Unit, onError: (String) -> Unit = {}) {
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onOk()

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (m == LockMode.Fingerprint && errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) ask(activity, LockMode.ScreenLock, onOk, onError)
                else onError(errString.toString())
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock LabHUD")
            .setSubtitle("Your lab's status and its buttons")
            .setAllowedAuthenticators(authenticators(m))
        if (m == LockMode.Fingerprint) info.setNegativeButtonText("Use screen lock")
        prompt.authenticate(info.build())
    }
}
