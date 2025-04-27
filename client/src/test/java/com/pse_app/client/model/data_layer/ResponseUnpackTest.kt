package com.pse_app.client.model.data_layer

import com.pse_app.client.model.exceptions.UnexpectedServerException
import io.ktor.http.HttpStatusCode
import org.junit.Test

class ResponseUnpackTest {
    @Test(expected = UnexpectedServerException::class)
    fun unpackThrowTest() {
        Response(HttpStatusCode.InternalServerError, null).unpack()
    }
}
