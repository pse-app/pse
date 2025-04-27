package com.pse_app.client.model.exceptions

/**
 * Model encountered unexpected error requesting server endpoint.
 *
 * Similar exceptions:
 *
 * [BadResponseException] for invalid server responses
 * [ServerErrorException] for internal server errors
 * [NetworkException] for errors with HTTP connection
 */
class UnexpectedServerException(message: String?=null, cause: Throwable?=null): ModelException(message, cause)
