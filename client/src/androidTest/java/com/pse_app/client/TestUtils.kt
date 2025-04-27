package com.pse_app.client

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch



suspend inline fun <T, R> runWhileCollectingShared(
    flow: SharedFlow<T>,
    crossinline action: suspend (List<T>) -> R,
): R {
    val out = mutableListOf<T>()
    return runCollectSharedInto(flow, out) {
        action(out)
    }
}

suspend inline fun <T> runCollectShared(
    flow: SharedFlow<T>,
    crossinline action: suspend () -> Unit,
): List<T> = runWhileCollectingShared(flow) {
    action()
    it
}
suspend inline fun <T, R> runCollectSharedInto(
    flow: SharedFlow<T>,
    out: MutableList<T>,
    crossinline action: suspend () -> R,
): R = coroutineScope {
    val job = launch {
        flow.collect { out.add(it) }
    }
    val result = action()
    job.cancelAndJoin()
    result
}
