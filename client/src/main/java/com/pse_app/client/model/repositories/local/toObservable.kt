package com.pse_app.client.model.repositories.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

internal fun <T, R> Flow<T?>.toObservable(onMatch: T, getter: () -> R): Flow<R> {
    return flow {
        emit(getter())
        this@toObservable.collect {
            if ((it ?: onMatch) == onMatch) {
                emit(getter())
            }
        }
    }
}
