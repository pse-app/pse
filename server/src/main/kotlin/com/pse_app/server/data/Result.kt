package com.pse_app.server.data

import com.pse_app.server.data.Result.Error
import com.pse_app.server.data.Result.Success

/**
 * Models a result of a computation that can yield an error. A Result is always either an instance of
 * [Success] or an instance of [Error].
 */
sealed interface Result<out T> {

    /**
     * A [Result] value of a successful computation.
     */
    data class Success<out T>(val value: T) : Result<T>

    /**
     * A [Result] value of an errored computation.
     */
    data class Error(val category: ErrorCategory, val message: String) : Result<Nothing> {
        constructor(message: String): this(ErrorCategory.Generic, message)
    }

    companion object {

        /**
         * Takes two results and applies the [joiner] functions to both its values if both are [Success]. Otherwise
         * returns the first error.
         */
        inline fun <A, B, R> ap(result1: Result<A>, result2: Result<B>, joiner: (A, B) -> R): Result<R> =
            result1.flatMap { value1 -> result2.map { value2 -> joiner(value1, value2) } }

        /**
         * Takes three results and applies the [joiner] functions to all its values if all are [Success]. Otherwise
         * returns the first error.
         */
        inline fun <A, B, C, R> ap(result1: Result<A>, result2: Result<B>, result3: Result<C>, joiner: (A, B, C) -> R): Result<R> =
            result1.flatMap { value1 -> result2.flatMap { value2 -> result3.map { value3 -> joiner(value1, value2, value3) } } }

        /**
         * Attempts to run the provided action catching exceptions of type [E] and converting them into [Error] values.
         */
        inline fun <T, reified E : Exception> tryTo(action: () -> T): Result<T> = try {
            Success(action())
        } catch (e: Exception) {
            if (e is E) Error(e.message ?: "") else throw e
        }
    }
}

/**
 * If this result is a [Success], invoke the provided function with the sored value. If the function yields an
 * [Error], returns that error, otherwise return this result.
 */
inline fun <T> Result<T>.void(result: (T) -> Result<Any?>): Result<T> = flatMap { value -> result(value).map { _ -> value } }

/**
 * If this result is a [Success] and the provided result is an [Error], returns the provided result, otherwise
 * returns this result.
 */
fun <T> Result<T>.void(result: Result<Any?>): Result<T> = flatMap { value -> result.map { _ -> value } }

/**
 * Simultaneously flat-maps result value and error message. `.bimap(s, e)` is the same as `.mapError(e).flatMap(s)`.
 */
inline fun <T, U> Result<T>.flatBimap(successFunction: (T) -> Result<U>, errorFunction: (String) -> String): Result<U> =
    mapError(errorFunction).flatMap(successFunction)

/**
 * Simultaneously maps result value and error message. `.bimap(s, e)` is the same as `.mapError(e).map(s)`.
 */
inline fun <T, U> Result<T>.bimap(successFunction: (T) -> U, errorFunction: (String) -> String): Result<U> =
    mapError(errorFunction).map(successFunction)

/**
 * Return an equal result. If the result is a [Success], also invoke the provided action.
 */
inline fun <T> Result<T>.tap(action: (T) -> Unit): Result<T> = map { value -> action(value); value }

/**
 * If this [Result] is a [Success], [mapErrorCategory] does nothing. Otherwise yields a new [Error] with
 * the provided function applied to the error category.
 */
inline fun <T> Result<T>.mapErrorCategory(function: (ErrorCategory, String) -> ErrorCategory): Result<T> =
    when (this) {
        is Success -> this
        is Error -> Error(function(category, message), message)
    }

/**
 * If this [Result] is a [Success], [mapError] does nothing. Otherwise yields a new [Error] with the
 * provided function applied to the error message.
 */
inline fun <T> Result<T>.mapError(function: (String) -> String): Result<T> =
    when (this) {
        is Success -> this
        is Error -> Error(category, function(message))
    }

/**
 * Yields a new [Result] whose value is the provided function applied to the value of this [Result].
 * If this [Result] is an [Error], `map` does nothing.
 */
inline fun <T, U> Result<T>.map(function: (T) -> U): Result<U> =
    when (this) {
        is Success -> Success(function(value))
        is Error -> this
    }

/**
 * The same as [flatMap].
 */
inline fun <T, U> Result<T>.andThen(function: (T) -> Result<U>): Result<U> = flatMap(function)

/**
 * If this [Result] is a [Success], yields the application of the provided function to the store value.
 * If this [Result] is an [Error], `flatMap` does nothing.
 */
inline fun <T, U> Result<T>.flatMap(function: (T) -> Result<U>): Result<U> =
    when (this) {
        is Success -> function(value)
        is Error -> this
    }

/**
 * If all results in this [Iterable] are [Success], returns a [Success] containing a [List] of all their values.
 * Otherwise, returns the first error.
 */
fun <T> Iterable<Result<T>>.lift(): Result<List<T>> = this.map { elem ->
    when (elem) {
        is Error -> return@lift elem
        is Success -> elem.value
    }
}.let(::Success)

/**
 * If all values of this [Map] are [Success], returns a [Success] containing a [Map] where the values are unwrapped.
 * Otherwise, returns the first error.
 */
fun <K, V> Map<K, Result<V>>.lift(): Result<Map<K, V>> = this.mapValues { (_, value) ->
    when (value) {
        is Error -> return@lift value
        is Success -> value.value
    }
}.let(::Success)


/**
 * Takes a nullable result and replaces [Success] values containing `null` with [Error] values with the
 * provided error category and message.
 */
fun <T> Result<T?>.nonNull(error: Error): Result<T> = flatMap { it.errIfNull(error) }

/**
 * Takes a nullable result and replaces [Success] values containing `null` with [Error] values with the
 * provided error message.
 */
fun <T> Result<T?>.nonNull(error: String): Result<T> = this.nonNull(Error(error))

/**
 * Takes a nullable result and replaces [Success] values containing `null` with [Error] values with the
 * provided error category and message.
 */
fun <T> Result<T?>.nonNull(category: ErrorCategory, error: String): Result<T> = this.nonNull(Error(category, error))

/**
 * Takes a result holding an [AutoCloseable] resource, maps the result while making sure, the resource
 * is closed at the end of map.
 */
inline fun <T : AutoCloseable, U> Result<T>.useMap(function: (T) -> U): Result<U> = map { res -> res.use { function(it) } }

/**
 * Takes a [Result] containing a [Result] and flattens it to a single level of [Result].
 */
fun <T, U : Result<T>> Result<U>.flatten(): Result<T> = flatMap { it }

/**
 * Converts a nullable type to a result with the specified error if null.
 */
fun <T> T?.errIfNull(error: Error): Result<T> = when (this) {
    null -> error
    else -> Success(this)
}

/**
 * Converts a nullable type to a result with the specified error message if null.
 */
fun <T> T?.errIfNull(error: String): Result<T> = this.errIfNull(Error(error))

/**
 * Converts a nullable type to a result with the specified error message and category if null.
 */
fun <T> T?.errIfNull(category: ErrorCategory, error: String): Result<T> = this.errIfNull(Error(category, error))
