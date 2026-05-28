package me.him188.ani.utils.coroutines

import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

actual suspend fun <R> runInterruptible(context: CoroutineContext, block: () -> R): R =
    withContext(context) { block() }
