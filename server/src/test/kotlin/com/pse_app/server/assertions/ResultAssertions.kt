package com.pse_app.server.assertions

import com.pse_app.server.data.ErrorCategory
import com.pse_app.server.data.Result
import org.junit.jupiter.api.Assertions.fail

fun <T>assertSuccess(result: Result<T>): T = assertSuccess(result) { it }
fun <T>assertSuccess(result: Result<T>, msg: String): T = assertSuccess(result, msg) { it }

fun <T, U>assertSuccess(result: Result<T>, elementTest: (T) -> U): U {
    return assertSuccess(result, "Result failed", elementTest)
}
fun <T, U>assertSuccess(result: Result<T>, msg: String, elementTest: (T) -> U): U = when (result) {
    is Result.Success -> elementTest(result.value)
    is Result.Error -> fail("${msg}: ${result.message}")
}

fun <T>assertError(result: Result<T>): String = assertError(result, "Result was a success")
fun <T>assertError(result: Result<T>, msg: String): String = when (result) {
    is Result.Success -> fail("${msg}: ${result.value}")
    is Result.Error -> result.message
}

fun <T>assertError(expectedCategory: ErrorCategory, result: Result<T>): String {
    return assertError(expectedCategory, result, "Unexpected Result")
}
fun <T>assertError(expectedCategory: ErrorCategory, result: Result<T>, msg: String): String = when (result) {
    is Result.Success -> fail("${msg}, expected=${expectedCategory}, got=<Success>: ${result.value}")
    is Result.Error ->
        if (result.category == expectedCategory) {
            result.message
        } else {
            fail("${msg}, expected=${expectedCategory}, got=${result.category}: ${result.message}")
        }
}
