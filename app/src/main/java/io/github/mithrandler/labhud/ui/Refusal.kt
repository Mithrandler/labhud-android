package io.github.mithrandler.labhud.ui

import io.github.mithrandler.labhud.data.Rights

/**
 * What a refusal from labhud means for this phone, in words to act on. labhud answers every refusal
 * with the one word "forbidden" (it must not say which of the rules was it); the app knows the
 * rights it last saw, so it can say what to do: sign in as the admin, or unlock with the PIN.
 */
fun refusalText(error: String, rights: Rights): String = when {
    error.isNotBlank() && error != "forbidden" -> error
    !rights.admin -> "This phone only looks. Sign in as the admin in Settings > Account."
    rights.pin -> "Locked. Unlock with the PIN, then try again."
    else -> "LabHUD refused. Sign in again in Settings > Account: the admin may have changed."
}

/** True for the answer a stale or missing sign-in gets, so the screen reads the rights again. */
fun isRefusal(error: String): Boolean = error.isBlank() || error == "forbidden"
