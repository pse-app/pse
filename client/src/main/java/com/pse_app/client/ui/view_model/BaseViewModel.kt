package com.pse_app.client.ui.view_model

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pse_app.client.StringResource
import com.pse_app.client.loggingTag
import com.pse_app.client.model.exceptions.LoginRejectedException
import com.pse_app.client.model.exceptions.SessionMissingException
import com.pse_app.client.model.exceptions.SessionRejectedException
import com.pse_app.client.prettyPrintException
import com.pse_app.client.ui.NavigationEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The common superclass of all view models in this application.
 *
 * This class handles errors through [errors] as well as navigation events through
 * [navigationChannel], without holding any reference to the snack bar or navigation
 * controller.
 * For events to actually be processed, the corresponding channels must be subscribed to.
 */
abstract class BaseViewModel : ViewModel() {
    init {
        Log.d(loggingTag, "Creating $loggingTag")
    }

    /**
     * Callback that is run whenever the [ViewModel]'s View enters the composition.
     */
    open fun onEntry() {
        Log.d(loggingTag, "Loading $loggingTag")
    }

    private val _errors = MutableSharedFlow<StringResource>()
    /**
     * A [SharedFlow] of errors that were caught in a view model and are
     * to be displayed in the corresponding View.
     */
    val errors = _errors.asSharedFlow()

    private val _navigationChannel: Channel<NavigationEvent> = Channel(Channel.BUFFERED)
    /**
     * A [Flow] over the navigation events raised by this view model.
     * This [Flow] is backed by a [Channel], i.e. only one receiver will get the events
     * that are emitted by this flow.
     */
    val navigations: Flow<NavigationEvent> = _navigationChannel.receiveAsFlow()

    /**
     * Sends the given [NavigationEvent]
     */
    fun navigate(navigationEvent: NavigationEvent) {
        // Send the event synchronously if the channel buffer is not full.
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            _navigationChannel.send(navigationEvent)
        }
    }

    /**
     * Runs the specified code block while handling exceptions using [handleException]
     */
    @Suppress("TooGenericExceptionCaught") // This method needs to catch generic exceptions to handle them
    inline fun runCatchingFlow(
        cont: () -> Unit,
    ) {
        try {
            cont()
        } catch (ex: Exception) {
            handleException(ex)
        }
    }

    private fun logEmitError(error: Throwable) {
        val errorSource = error.stackTrace.firstOrNull()?.className ?: "Unknown"
        Log.i(
            "$loggingTag ($errorSource)",
            "Displaying caught exception in Snackbar",
            error,
        )
        unguardedViewModelScope.launch { _errors.emit(prettyPrintException(error)) }
    }

    /** Handles the [Throwable] passed by displaying it to the user without blocking
     *  and returns a [Job].
     *
     * [Error]s get rethrown to the caller.
     */
    @Suppress("TooGenericExceptionCaught") // Method must handle case in which raiseLogin fails without crashing app
    fun handleException(error: Throwable) {
        // Avoid accidentally catching CancellationExceptions when handling exceptions in
        // coroutines.
        if (error is CancellationException)
            throw error

        // Never catch hard Errors
        if (error is Error) {
            throw error
        }

        logEmitError(error)

        if (error is SessionMissingException || error is LoginRejectedException || error is SessionRejectedException) {
            try {
                raiseLogin()
            } catch (ex: Exception) {
                logEmitError(ex)
            }
        }
    }

    /**
     * A [CoroutineScope] that behaves like [ViewModel.viewModelScope] but also uses
     * [handleException] to handle exceptions using a [CoroutineExceptionHandler].
     */
    val viewModelScope
        get() = CoroutineScope(
            unguardedViewModelScope.coroutineContext.plus(CoroutineExceptionHandler { _, ex ->
                handleException(ex)
            })
        )

    /**
     * Converts a [Flow] to a [SharedFlow] in [viewModelScope] using the initial value.
     */
    fun <T> Flow<T>.toStateFlow(
        initial: T,
        sharingStarted: SharingStarted = SharingStarted.Eagerly
    ): StateFlow<T> =
        stateIn(viewModelScope, sharingStarted, initial)

    /**
     * Launches the block using [viewModelScope], forgetting the [Job] returned by the
     * operation. Useful for implementing [Unit] functions with function expression syntax.
     */
    inline fun launchViewModel(crossinline block: suspend CoroutineScope.() -> Unit) {
        viewModelScope.launch { block() }
    }

    /**
     * The base [ViewModel.viewModelScope], without exception handling.
     */
    val unguardedViewModelScope
        get() = ViewModel::viewModelScope.get(this)

    /**
     * Used to implement back navigation when this view model's view is in the composition.
     * By default, behaves similarly to the builtin back button, with the exception of
     * more gracefully handling an unexpectedly empty back-stack.
     */
    open fun goBack() = navigate(NavigationEvent.DefaultBack)

    /**
     * Used to implement navigation when [handleException] catches one of [LoginRejectedException]
     * or [SessionMissingException].
     * Defaults to navigating to the login view.
     */
    open fun raiseLogin() = navigate(NavigationEvent.DefaultRaiseLogin)

    override fun onCleared() {
        super.onCleared()
        _navigationChannel.close()
    }
}
