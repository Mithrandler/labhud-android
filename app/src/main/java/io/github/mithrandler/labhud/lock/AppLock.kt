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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The app's lock: the phone's fingerprint, face or PIN once when the app opens, then nothing more
 * (actions included) until the app has spent [IDLE_MS] in the background. A token is as good as
 * standing at an unlocked display, so a phone left on a table must not open on its own.
 */
object AppLock {
    const val IDLE_MS = 5 * 60_000L
    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()
    private var hiddenAt = 0L
    var required = true
        set(value) {
            field = value
            if (!value) _unlocked.value = true
        }

    /** Android 10 cannot mix a strong biometric with the PIN in one prompt; a weak one it can. */
    private val authenticators =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) BIOMETRIC_STRONG or DEVICE_CREDENTIAL
        else BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    fun onHidden() {
        hiddenAt = SystemClock.elapsedRealtime()
    }

    fun onShown() {
        if (required && hiddenAt != 0L && SystemClock.elapsedRealtime() - hiddenAt > IDLE_MS) _unlocked.value = false
    }

    fun lockNow() {
        if (required) _unlocked.value = false
    }

    /** The phone has no screen lock at all: there is nothing to ask, so the app stays open. */
    fun canAsk(activity: FragmentActivity): Boolean =
        BiometricManager.from(activity).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS

    fun prompt(activity: FragmentActivity, onError: (String) -> Unit) {
        if (!required || !canAsk(activity)) {
            _unlocked.value = true
            return
        }
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                _unlocked.value = true
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onError(errString.toString())
            }
        })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock LabHUD")
                .setSubtitle("Your lab's status and its buttons")
                .setAllowedAuthenticators(authenticators)
                .build()
        )
    }
}
