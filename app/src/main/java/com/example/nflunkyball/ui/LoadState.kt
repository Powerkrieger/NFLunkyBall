package com.example.nflunkyball.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/** One async load as a screen sees it — replaces the `(value: T?, status: String?)` pairs
 *  where "loading", "loaded" and "failed" were folded into one nullable string. */
sealed interface LoadState<out T> {
    data object Idle : LoadState<Nothing>
    data object Loading : LoadState<Nothing>
    data class Loaded<T>(val value: T) : LoadState<T>
    /** [message] is the server's/transport's own text; [reasonRes] an app-originated reason
     *  (no server access, nothing cached…) that the screen resolves to a localised string.
     *  Exactly one of the two is set. */
    data class Failed(val message: String? = null, @StringRes val reasonRes: Int? = null) : LoadState<Nothing>
}

val <T> LoadState<T>.valueOrNull: T? get() = (this as? LoadState.Loaded)?.value

/** The user-facing text for a failure, whichever way it was expressed. */
@Composable
fun LoadState.Failed.text(): String = message ?: reasonRes?.let { stringResource(it) } ?: ""
