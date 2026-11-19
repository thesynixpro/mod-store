package com.aprax.modstore.data.download

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/** Byte counter for one in-flight download. */
data class TransferProgress(
    val bytes: Long,
    val total: Long?,
) {
    /** 0f..1f, or null when the source did not declare a length and it cannot be known. */
    val fraction: Float?
        get() {
            val size = total ?: return null
            if (size <= 0L) return null
            return (bytes.toDouble() / size.toDouble()).toFloat().coerceIn(0f, 1f)
        }
}

/**
 * In-memory mirror of live download progress.
 *
 * The database is the source of truth for a download's *state*; this object only carries
 * the fast-moving byte counter, because writing every 64 KB chunk to Room would be wasteful
 * and would make the UI stutter. The worker updates both, at a throttled rate.
 */
@Singleton
class MutableProgressUpdater @Inject constructor() {

    private val _progress = MutableStateFlow<Map<String, TransferProgress>>(emptyMap())
    val progress: StateFlow<Map<String, TransferProgress>> = _progress.asStateFlow()

    fun update(downloadId: String, bytes: Long, total: Long?) {
        _progress.update { current ->
            current + (downloadId to TransferProgress(bytes = bytes, total = total))
        }
    }

    fun clear(downloadId: String) {
        _progress.update { current -> current - downloadId }
    }

    fun snapshot(downloadId: String): TransferProgress? = _progress.value[downloadId]
}