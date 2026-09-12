package com.example.nflunkyball.ui

/** One async load as a screen sees it — replaces the `(value: T?, status: String?)` pairs
 *  where "loading", "loaded" and "failed" were folded into one nullable string. */
sealed interface LoadState<out T> {
    data object Idle : LoadState<Nothing>
    data object Loading : LoadState<Nothing>
    data class Loaded<T>(val value: T) : LoadState<T>
    data class Failed(val message: String) : LoadState<Nothing>
}

val <T> LoadState<T>.valueOrNull: T? get() = (this as? LoadState.Loaded)?.value
