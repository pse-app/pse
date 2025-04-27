package com.pse_app.client.model.data_layer

import com.pse_app.client.model.exceptions.UnexpectedServerException
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess

/**
 * Represents response obtained from backend.
 * [response] is null unless [status] is successful.
 */
data class Response<T> (
    /** Status of response */
    val status: HttpStatusCode,
    /** Deserialized response body if success */
    val response: T?
) {
    /**
     * Returns response body or throws exception otherwise.
     * Only use once expected failing status codes where handled.
     *
     * @throws UnexpectedServerException
     */
    fun unpack(): T {
        if (status.isSuccess()) {
            return response!!
        }
        throw UnexpectedServerException("Server returned status: $status ")
    }
}
