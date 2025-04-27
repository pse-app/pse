package com.pse_app.client.model.exceptions

/**
 * Server encountered internal error
 *
 * See [UnexpectedServerException] for other server related errors
 */
class ServerErrorException(responseMessage: String):
    ModelException("Internal server error: $responseMessage")
