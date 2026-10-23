package io.github.mithrandler.labhud.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay

/**
 * Like a [LaunchedEffect], but [block] runs only while the screen is STARTED: it is cancelled when the app
 * goes to the background and starts again, from the top, when it comes back. A poll loop in a plain
 * LaunchedEffect keeps asking the server for as long as the process lives, in front or not.
 */
@Composable
fun WhileStarted(vararg keys: Any?, block: suspend CoroutineScope.() -> Unit) {
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner, *keys) { owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { block() } }
}

/** [WhileStarted] that runs [block] now and then every [intervalMs]. */
@Composable
fun PollWhileStarted(vararg keys: Any?, intervalMs: Long, block: suspend CoroutineScope.() -> Unit) {
    WhileStarted(*keys) {
        while (true) {
            block()
            delay(intervalMs)
        }
    }
}
