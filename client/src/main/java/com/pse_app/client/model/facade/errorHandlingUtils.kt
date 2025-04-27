package com.pse_app.client.model.facade

import com.pse_app.client.model.exceptions.InternalException
import com.pse_app.client.model.exceptions.ModelException
import kotlinx.coroutines.CancellationException

/**
 * Executes block, catching [Exception] and wrapping it in [InternalException]
 */
@Suppress("TooGenericExceptionCaught") // Function must catch generic exceptions to handle them
inline fun <T> catchInternal(block: () -> T): T {
    try {
        return block()
    } catch (e: Exception) {
        when (e) {
            is ModelException, is CancellationException -> {
                throw e
            }
            else -> throw InternalException(e)
        }
    }
}
