package com.pse_app.client.model.exceptions


/**
 * Base class for exceptions thrown by Model.
 */
sealed class ModelException(message: String?=null, cause: Throwable?=null): Exception(message, cause)
