package org.example.project.ui.components

import arrow.core.Either
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.example.project.core.domain.DomainError
import org.example.project.core.domain.toMessage

/**
 * Extension function for MutableStateFlow that executes an async operation with automatic
 * loading state and feedback notice handling using runCatching pattern.
 *
 * Typical usage:
 * ```
 * _state.executeAsyncOperation(
 *     loadingUpdate = { it.copy(isSaving = true) },
 *     successUpdate = { state, result -> state.copy(isSaving = false, notice = successNotice("Saved")) },
 *     errorUpdate = { state, error -> state.copy(isSaving = false, notice = errorNotice("Error: ${error.message}")) },
 *     operation = { saveSomething() }
 * )
 * ```
 *
 * @param loadingUpdate Function to set the loading state to true
 * @param successUpdate Function to update state on success with result
 * @param errorUpdate Function to update state on error
 * @param operation The async operation to execute
 */
suspend fun <T, R> MutableStateFlow<T>.executeAsyncOperation(
    loadingUpdate: (T) -> T,
    successUpdate: (T, R) -> T,
    errorUpdate: (T, Throwable) -> T,
    operation: suspend () -> R,
) {
    update { loadingUpdate(it) }
    runCatching { operation() }
        .onSuccess { result ->
            update { successUpdate(it, result) }
        }
        .onFailure { error ->
            update { errorUpdate(it, error) }
        }
}

/**
 * Extension function for MutableStateFlow that executes an async operation with automatic
 * loading state and feedback notice handling using Arrow Either pattern.
 *
 * Typical usage:
 * ```
 * _state.executeEitherOperation(
 *     loadingUpdate = { it.copy(isSaving = true) },
 *     successUpdate = { state, result -> state.copy(isSaving = false, notice = successNotice("Saved")) },
 *     errorUpdate = { state, error -> state.copy(isSaving = false, notice = errorNotice(error.toMessage())) },
 *     operation = { saveSomething() }
 * )
 * ```
 *
 * @param loadingUpdate Function to set the loading state to true
 * @param successUpdate Function to update state on success with result
 * @param errorUpdate Function to update state on error
 * @param operation The async operation that returns Either<DomainError, R>
 */
suspend fun <T, R> MutableStateFlow<T>.executeEitherOperation(
    loadingUpdate: (T) -> T,
    successUpdate: (T, R) -> T,
    errorUpdate: (T, DomainError) -> T,
    operation: suspend () -> Either<DomainError, R>,
) {
    update { loadingUpdate(it) }
    operation().fold(
        ifLeft = { error ->
            update { errorUpdate(it, error) }
        },
        ifRight = { result ->
            update { successUpdate(it, result) }
        },
    )
}

/**
 * Convenience overload for executeAsyncOperation that handles common pattern where
 * you just need to toggle a loading flag and set success/error notices.
 *
 * Usage:
 * ```
 * _state.executeAsyncOperationWithNotice(
 *     loadingUpdate = { it.copy(isSaving = true) },
 *     noticeUpdate = { state, notice -> state.copy(isSaving = false, notice = notice) },
 *     successMessage = "Saved successfully",
 *     errorMessagePrefix = "Save failed",
 *     operation = { saveSomething() },
 *     onSuccess = { result -> loadData() } // optional
 * )
 * ```
 */
suspend fun <T, R> MutableStateFlow<T>.executeAsyncOperationWithNotice(
    loadingUpdate: (T) -> T,
    noticeUpdate: (T, FeedbackBannerModel) -> T,
    successMessage: String,
    errorMessagePrefix: String = "Errore",
    operation: suspend () -> R,
    onSuccess: (suspend (R) -> Unit)? = null,
) {
    update { loadingUpdate(it) }
    runCatching { operation() }
        .onSuccess { result ->
            update { noticeUpdate(it, successNotice(successMessage)) }
            onSuccess?.invoke(result)
        }
        .onFailure { error ->
            update { noticeUpdate(it, errorNotice("$errorMessagePrefix: ${error.message}")) }
        }
}

/**
 * Convenience overload for executeEitherOperation that handles common pattern where
 * you just need to toggle a loading flag and set success/error notices.
 *
 * Usage:
 * ```
 * _state.executeEitherOperationWithNotice(
 *     loadingUpdate = { it.copy(isSaving = true) },
 *     noticeUpdate = { state, notice -> state.copy(isSaving = false, notice = notice) },
 *     successMessage = "Saved successfully",
 *     operation = { saveSomething() },
 *     onSuccess = { result -> loadData() } // optional
 * )
 * ```
 */
suspend fun <T, R> MutableStateFlow<T>.executeEitherOperationWithNotice(
    loadingUpdate: (T) -> T,
    noticeUpdate: (T, FeedbackBannerModel?) -> T,
    successMessage: String? = null,
    operation: suspend () -> Either<DomainError, R>,
    onSuccess: (suspend (R) -> Unit)? = null,
    onError: (suspend (DomainError) -> Unit)? = null,
) {
    update { loadingUpdate(it) }
    operation().fold(
        ifLeft = { error ->
            update { noticeUpdate(it, errorNotice(error.toMessage())) }
            onError?.invoke(error)
        },
        ifRight = { result ->
            update { noticeUpdate(it, successMessage?.let { successNotice(it) }) }
            onSuccess?.invoke(result)
        },
    )
}
