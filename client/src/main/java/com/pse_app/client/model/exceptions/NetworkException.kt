package com.pse_app.client.model.exceptions

/**
 * Model operation failed due to network timeout or failed connection.
 */
class NetworkException(message: String?=null, cause: Throwable?=null): ModelException(message, cause)
