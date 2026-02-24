package org.example.project.ui.components

import arrow.core.Either
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.example.project.core.domain.DomainError
import org.example.project.core.domain.toMessage

/**
 * # Async Operation Helper Extensions for MutableStateFlow
 *
 * This file provides standardized extension functions for executing async operations
 * in ViewModels with automatic loading state management and user feedback handling.
 *
 * ## Overview
 *
 * The helpers eliminate boilerplate code from async operations by handling the common pattern of:
 * 1. Setting a loading flag to true
 * 2. Executing the async operation
 * 3. Handling success/failure with state updates and user notifications
 * 4. Setting the loading flag back to false
 *
 * ## Function Variants
 *
 * There are four main extension functions, grouped by error handling approach:
 *
 * ### runCatching-based (for operations that throw exceptions):
 * - [executeAsyncOperation]: Full control over success/error state updates
 * - [executeAsyncOperationWithNotice]: Simplified version with automatic notice handling
 *
 * ### Arrow Either-based (for functional domain operations):
 * - [executeEitherOperation]: Full control over success/error state updates
 * - [executeEitherOperationWithNotice]: Simplified version with automatic notice handling
 *
 * ## Choosing the Right Function
 *
 * **Use `executeAsyncOperation` when:**
 * - Operation can throw exceptions (file I/O, network calls, etc.)
 * - You need custom state transformations on success/failure
 * - Result value needs to be incorporated into state
 *
 * **Use `executeAsyncOperationWithNotice` when:**
 * - Operation can throw exceptions
 * - You only need to show success/error notices
 * - Simple toggle loading flag pattern
 *
 * **Use `executeEitherOperation` when:**
 * - Operation returns Either<DomainError, R>
 * - You need custom state transformations on success/failure
 * - Working with functional domain layer
 *
 * **Use `executeEitherOperationWithNotice` when:**
 * - Operation returns Either<DomainError, R>
 * - You only need to show success/error notices
 * - Simple toggle loading flag pattern
 *
 * ## Related Functions
 *
 * These helpers work with:
 * - [successNotice]: Creates success feedback banner
 * - [errorNotice]: Creates error feedback banner
 * - [DomainError.toMessage]: Converts domain errors to user-friendly messages
 */

/**
 * Executes an async operation with automatic loading state and feedback notice handling
 * using the runCatching pattern for exception-based error handling.
 *
 * This function provides full control over how success and error results update the state.
 * Use this when you need to incorporate operation results into the state or need custom
 * error handling logic beyond simple notifications.
 *
 * The operation is wrapped in [runCatching], so any exceptions thrown will be caught
 * and passed to [errorUpdate].
 *
 * @param T The type of state managed by the MutableStateFlow
 * @param R The result type of the async operation
 * @param loadingUpdate Function to update state when operation starts (typically sets a loading flag)
 * @param successUpdate Function to update state on success, receives current state and operation result
 * @param errorUpdate Function to update state on error, receives current state and the exception
 * @param operation Suspending function that performs the async operation
 *
 * ## Example: Auto-assignment with result handling
 *
 * ```kotlin
 * _state.executeAsyncOperation(
 *     loadingUpdate = { it.copy(isAutoAssigning = true) },
 *     successUpdate = { state, result ->
 *         val noticeText = "Autoassegnazione completata: ${result.assignedCount} slot assegnati"
 *         state.copy(
 *             isAutoAssigning = false,
 *             autoAssignUnresolved = result.unresolved,
 *             notice = successNotice(noticeText)
 *         )
 *     },
 *     errorUpdate = { state, error ->
 *         state.copy(
 *             isAutoAssigning = false,
 *             notice = errorNotice("Errore autoassegnazione: ${error.message}")
 *         )
 *     },
 *     operation = { autoAssegnaProgramma(programId) }
 * )
 * ```
 *
 * ## Example: Print operation with file path result
 *
 * ```kotlin
 * _state.executeAsyncOperation(
 *     loadingUpdate = { it.copy(isPrintingProgram = true) },
 *     successUpdate = { state, path ->
 *         state.copy(
 *             isPrintingProgram = false,
 *             notice = successNotice("Programma stampato: ${path.fileName}")
 *         )
 *     },
 *     errorUpdate = { state, error ->
 *         state.copy(
 *             isPrintingProgram = false,
 *             notice = errorNotice("Errore stampa: ${error.message}")
 *         )
 *     },
 *     operation = { stampaProgramma(programId) }
 * )
 * ```
 *
 * @see executeAsyncOperationWithNotice for a simplified version with automatic notice handling
 * @see executeEitherOperation for Arrow Either-based error handling
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
 * Executes an async operation with automatic loading state and feedback notice handling
 * using the Arrow Either pattern for functional error handling.
 *
 * This function provides full control over how success and error results update the state.
 * Use this when working with domain layer operations that return Either<DomainError, R>
 * and you need to incorporate the result into state or implement custom error handling.
 *
 * Unlike [executeAsyncOperation], this function works with explicitly typed domain errors
 * through Arrow's Either type, making error handling more type-safe and functional.
 *
 * @param T The type of state managed by the MutableStateFlow
 * @param R The result type of the async operation (the Right side of Either)
 * @param loadingUpdate Function to update state when operation starts (typically sets a loading flag)
 * @param successUpdate Function to update state on success, receives current state and operation result
 * @param errorUpdate Function to update state on error, receives current state and the DomainError
 * @param operation Suspending function that performs the operation and returns Either<DomainError, R>
 *
 * ## Example: Schema refresh with preview state
 *
 * ```kotlin
 * _state.executeEitherOperation(
 *     loadingUpdate = { it.copy(isRefreshingProgramFromSchemas = true) },
 *     successUpdate = { state, preview ->
 *         state.copy(
 *             isRefreshingProgramFromSchemas = false,
 *             schemaRefreshPreview = preview
 *         )
 *     },
 *     errorUpdate = { state, error ->
 *         state.copy(
 *             isRefreshingProgramFromSchemas = false,
 *             notice = FeedbackBannerModel(error.toMessage(), FeedbackBannerKind.ERROR)
 *         )
 *     },
 *     operation = { aggiornaProgrammaDaSchemi(programId) }
 * )
 * ```
 *
 * ## Example: Schema update with custom success notice
 *
 * ```kotlin
 * _state.executeEitherOperation(
 *     loadingUpdate = { it.copy(isRefreshingSchemas = true) },
 *     successUpdate = { state, result ->
 *         val message = "Schemi aggiornati: ${result.templatesAdded} aggiunti, ${result.templatesUpdated} aggiornati"
 *         state.copy(
 *             isRefreshingSchemas = false,
 *             notice = FeedbackBannerModel(message, FeedbackBannerKind.SUCCESS)
 *         )
 *     },
 *     errorUpdate = { state, error ->
 *         state.copy(
 *             isRefreshingSchemas = false,
 *             notice = errorNotice(error.toMessage())
 *         )
 *     },
 *     operation = { aggiornaSchemi() }
 * )
 * ```
 *
 * @see executeEitherOperationWithNotice for a simplified version with automatic notice handling
 * @see executeAsyncOperation for runCatching-based error handling
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
 * Simplified version of [executeAsyncOperation] that automatically handles success/error notices.
 *
 * This convenience function handles the common pattern where you just need to toggle a
 * loading flag and show success/error notices to the user. It automatically creates
 * [FeedbackBannerModel] instances using [successNotice] and [errorNotice].
 *
 * Use this for simple operations where:
 * - The operation result doesn't need to be stored in state
 * - Standard success/error notifications are sufficient
 * - You want to minimize boilerplate code
 *
 * The operation is wrapped in [runCatching], so any exceptions thrown will be caught
 * and converted to error notices.
 *
 * @param T The type of state managed by the MutableStateFlow
 * @param R The result type of the async operation
 * @param loadingUpdate Function to update state when operation starts (typically sets a loading flag)
 * @param noticeUpdate Function to update state with the feedback notice (typically clears loading flag and sets notice)
 * @param successMessage Message to display in the success notice
 * @param errorMessagePrefix Prefix for error messages (defaults to "Errore"), will be followed by ": ${error.message}"
 * @param operation Suspending function that performs the async operation
 * @param onSuccess Optional callback invoked after state update on success, useful for triggering follow-up actions like reloading data
 *
 * ## Example: Save settings with reload
 *
 * ```kotlin
 * _state.executeAsyncOperationWithNotice(
 *     loadingUpdate = { it.copy(isSavingAssignmentSettings = true) },
 *     noticeUpdate = { state, notice ->
 *         state.copy(isSavingAssignmentSettings = false, notice = notice)
 *     },
 *     successMessage = "Impostazioni assegnatore salvate",
 *     errorMessagePrefix = "Errore salvataggio impostazioni",
 *     operation = { salvaImpostazioniAssegnatore(settings) }
 * )
 * ```
 *
 * ## Example: Reactivate week with follow-up reload
 *
 * ```kotlin
 * _state.executeAsyncOperationWithNotice(
 *     loadingUpdate = { it },  // No loading flag for quick operations
 *     noticeUpdate = { state, notice -> state.copy(notice = notice) },
 *     successMessage = "Settimana riattivata",
 *     errorMessagePrefix = "Errore riattivazione",
 *     operation = { weekPlanStore.updateWeekStatus(weekId, WeekPlanStatus.ACTIVE) },
 *     onSuccess = { loadWeeksForSelectedProgram() }  // Reload data after success
 * )
 * ```
 *
 * ## Example: Save with dialog close
 *
 * ```kotlin
 * _state.executeAsyncOperationWithNotice(
 *     loadingUpdate = { it.copy(isSavingPartEditor = true) },
 *     noticeUpdate = { state, notice ->
 *         state.copy(
 *             partEditorWeekId = null,  // Close dialog
 *             partEditorParts = emptyList(),
 *             isSavingPartEditor = false,
 *             notice = notice
 *         )
 *     },
 *     successMessage = "Modifiche salvate",
 *     operation = { weekPlanStore.saveWeekParts(weekId, parts) }
 * )
 * ```
 *
 * @see executeAsyncOperation for full control over state updates
 * @see executeEitherOperationWithNotice for Either-based error handling
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
 * Simplified version of [executeEitherOperation] that automatically handles success/error notices.
 *
 * This convenience function handles the common pattern where you just need to toggle a
 * loading flag and show success/error notices to the user. It automatically creates
 * [FeedbackBannerModel] instances using [successNotice] and [errorNotice], with domain
 * errors converted to messages via [DomainError.toMessage].
 *
 * Use this for simple operations where:
 * - The operation returns Either<DomainError, R>
 * - The operation result doesn't need to be stored in state
 * - Standard success/error notifications are sufficient
 * - You want to minimize boilerplate code
 *
 * @param T The type of state managed by the MutableStateFlow
 * @param R The result type of the async operation (the Right side of Either)
 * @param loadingUpdate Function to update state when operation starts (typically sets a loading flag)
 * @param noticeUpdate Function to update state with the feedback notice (typically clears loading flag and sets notice)
 * @param successMessage Optional message to display in success notice. If null, no success notice is shown.
 * @param operation Suspending function that performs the operation and returns Either<DomainError, R>
 * @param onSuccess Optional callback invoked after state update on success, useful for triggering follow-up actions like reloading data
 * @param onError Optional callback invoked after state update on error, useful for custom error handling logic
 *
 * ## Example: Delete with reload
 *
 * ```kotlin
 * _state.executeEitherOperationWithNotice(
 *     loadingUpdate = { it.copy(isDeletingSelectedProgram = true) },
 *     noticeUpdate = { state, notice ->
 *         state.copy(isDeletingSelectedProgram = false, notice = notice)
 *     },
 *     successMessage = "Programma selezionato eliminato",
 *     operation = { eliminaProgrammaFuturo(programId, today) },
 *     onSuccess = { loadProgramsAndWeeks() }
 * )
 * ```
 *
 * ## Example: Assignment with silent success
 *
 * ```kotlin
 * _state.executeEitherOperationWithNotice(
 *     loadingUpdate = { it.copy(isAssigning = true) },
 *     noticeUpdate = { state, notice -> state.copy(isAssigning = false, notice = notice) },
 *     successMessage = null,  // No success notice shown
 *     operation = {
 *         assegnaPersona(
 *             weekStartDate = weekStartDate,
 *             weeklyPartId = weeklyPartId,
 *             personId = personId,
 *             slot = slot
 *         )
 *     },
 *     onSuccess = { loadWeeksForSelectedProgram() }
 * )
 * ```
 *
 * ## Example: Remove assignment with notice
 *
 * ```kotlin
 * _state.executeEitherOperationWithNotice(
 *     loadingUpdate = { it.copy(isRemovingAssignment = true) },
 *     noticeUpdate = { state, notice ->
 *         state.copy(isRemovingAssignment = false, notice = notice)
 *     },
 *     successMessage = "Assegnazione rimossa",
 *     operation = { rimuoviAssegnazione(assignmentId) },
 *     onSuccess = { loadWeeksForSelectedProgram() }
 * )
 * ```
 *
 * ## Example: Nested operations (create then generate)
 *
 * ```kotlin
 * _state.executeEitherOperationWithNotice(
 *     loadingUpdate = { it.copy(isCreatingProgram = true) },
 *     noticeUpdate = { state, notice ->
 *         state.copy(isCreatingProgram = false, notice = notice)
 *     },
 *     successMessage = null,  // Nested operation will show success
 *     operation = { creaProssimoProgramma() },
 *     onSuccess = { program ->
 *         // Nested async operation
 *         _state.executeEitherOperationWithNotice(
 *             loadingUpdate = { it },
 *             noticeUpdate = { state, notice -> state.copy(notice = notice) },
 *             successMessage = "Programma ${formatMonthYearLabel(program.month, program.year)} creato",
 *             operation = { generaSettimaneProgramma(program.id.value) },
 *             onSuccess = { loadProgramsAndWeeks() }
 *         )
 *     }
 * )
 * ```
 *
 * @see executeEitherOperation for full control over state updates
 * @see executeAsyncOperationWithNotice for runCatching-based error handling
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
