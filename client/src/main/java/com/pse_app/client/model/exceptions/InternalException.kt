package com.pse_app.client.model.exceptions

/**
 * An unexpected exception occurred in the model.
 */
class InternalException(cause: Throwable): ModelException(
    "An unexpected exception occurred in the model",
    cause
)
