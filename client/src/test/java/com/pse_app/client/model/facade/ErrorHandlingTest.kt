package com.pse_app.client.model.facade

import com.pse_app.client.model.exceptions.InternalException
import com.pse_app.client.model.exceptions.ModelException
import com.pse_app.client.model.exceptions.NoActiveUserException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class ErrorHandlingTest {
    @Test(expected = ModelException::class)
    fun catchModelExceptionTest() {
        catchInternal {
            throw NoActiveUserException()
        }
    }

    @Test
    fun propagateCancellationTest() = runTest(timeout = 1.seconds) {
        val job = launch {
            catchInternal {
                delay(2.seconds)
            }
        }
        job.cancel()
    }

    @Test(expected = InternalException::class)
    fun catchUnexpectedExceptionTest() {
        catchInternal {
            throw ArithmeticException()
        }
    }
}
