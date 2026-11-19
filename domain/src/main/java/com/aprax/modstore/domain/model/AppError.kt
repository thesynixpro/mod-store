package com.aprax.modstore.domain.model

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * A comparable, typed failure.
 *
 * Repositories return [Result] and carry one of these as the failure, which lets the UI
 * branch on the cause instead of pattern-matching on message text. Every source-specific
 * failure is represented so one unreachable site degrades to a local error state rather
 * than taking down the whole screen.
 */
sealed class AppError(
    override val message: String,
    override val cause: Throwable? = null,
) : Exception(message, cause) {

    /** The device could not reach the network at all. */
    class Offline(cause: Throwable? = null) :
        AppError("You appear to be offline. Check your connection and try again.", cause)

    /** DNS/connect/TLS failure talking to a specific source. */
    class SourceUnavailable(
        val sourceId: SourceId,
        val sourceName: String,
        cause: Throwable? = null,
    ) : AppError("$sourceName can't be reached right now.", cause)

    /** The source answered, but not with anything usable (layout change, blocked page). */
    class SourceChanged(
        val sourceId: SourceId,
        val sourceName: String,
        cause: Throwable? = null,
    ) : AppError("$sourceName returned a page this app couldn't read.", cause)

    /** The source explicitly asked us to slow down. We comply rather than retry harder. */
    class RateLimited(val retryAfterSeconds: Long? = null) :
        AppError("This source is rate-limiting requests. Try again in a little while.")

    /** The source refused programmatic access, so the app offers its link instead. */
    class NoProgrammaticAccess(
        val sourceId: SourceId,
        val sourceName: String,
    ) : AppError("$sourceName doesn't offer a public API, so listings open on its site.")

    /** The requested listing no longer exists on the source. */
    class NotFound(val what: String = "listing") :
        AppError("That $what is no longer available on the source site.")

    /** A response was received but could not be parsed. */
    class Malformed(val sourceName: String, cause: Throwable? = null) :
        AppError("$sourceName sent data this app couldn't understand.", cause)

    /** Local storage failure (Room/DataStore). */
    class Storage(cause: Throwable? = null) :
        AppError("A local storage error occurred.", cause)

    /** Timed out waiting on a source. */
    class Timeout(val sourceName: String) :
        AppError("$sourceName took too long to respond.")

    /** Anything not otherwise classified. */
    class Unexpected(cause: Throwable? = null) :
        AppError("Something unexpected went wrong.", cause)

    /**
     * Whether retrying the same call could plausibly succeed. The UI uses this to decide
     * between showing a "Try again" button and showing a one-way link to the source.
     */
    val isRetryable: Boolean
        get() = when (this) {
            is Offline, is SourceUnavailable, is Timeout, is RateLimited -> true
            is NoProgrammaticAccess, is NotFound, is Malformed, is SourceChanged -> false
            is Storage, is Unexpected -> true
        }
}

/** Unwraps a [Result] or throws the underlying [AppError], never a raw cause. */
fun <T> Result<T>.getOrThrowAppError(): T = getOrElse { throw it }

/**
 * Folds a [Result] into a pair of nullable values so UI state holders can express
 * "loading finished with either data or an error" in one assignment.
 */
inline fun <T, R> Result<T>.foldValue(
    onSuccess: (T) -> R,
    onFailure: (AppError) -> R,
): R = fold(
    onSuccess = onSuccess,
    onFailure = { error -> onFailure(error as? AppError ?: AppError.Unexpected(error)) },
)

/** Reactive variant of [foldValue] for flows of results. */
fun <T, R> Flow<Result<T>>.mapResult(
    onSuccess: (T) -> R,
    onFailure: (AppError) -> R,
): Flow<R> = map { result ->
    result.fold(
        onSuccess = onSuccess,
        onFailure = { error -> onFailure(error as? AppError ?: AppError.Unexpected(error)) },
    )
}
