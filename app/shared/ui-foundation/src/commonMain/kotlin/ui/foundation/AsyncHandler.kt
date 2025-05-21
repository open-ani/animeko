package me.him188.ani.app.ui.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.foundation.widgets.showLoadError
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * 一个简单的异步任务处理器. 设计为了处理 `onClick` 等回调时调用后台任务.
 *
 * 使用 [launch] 启用一个协程, 其异常将会被捕获, 然后被对应的 [rememberAsyncHandler] 里的 callback 处理.
 */
@Stable
interface AsyncHandler {
    /**
     * 此时是否有任何任务正在进行中
     */
    val isWorking: Boolean

    /**
     * 在 UI 线程启动一个异步任务.
     *
     * [block] 内的任何异常 [Throwable] 将被捕获, 通过 [rememberAsyncHandler] 时传入的 `onException` 处理.
     */
    fun launch(
        coroutineContext: CoroutineContext = EmptyCoroutineContext,
        block: suspend CoroutineScope.() -> Unit,
    ): Job
}

/**
 * Remember 一个使用 [me.him188.ani.app.ui.foundation.widgets.Toaster] 处理所有异常的 [AsyncHandler].
 *
 * @see AsyncHandler
 */
@Composable
fun rememberAsyncHandler(): AsyncHandler {
    val toaster by rememberUpdatedState(LocalToaster.current)
    return rememberAsyncHandler(
        onException = { e ->
            toaster.showLoadError(LoadError.fromException(e))
        },
    )
}

/**
 * Remember 一个自定义处理异常的 [AsyncHandler].
 *
 * @param onException 处理异常的函数. 在 [AsyncHandler.launch] 发生任何异常时都会被调用.
 *
 * @see AsyncHandler
 * @see LoadError
 */
@Composable
fun rememberAsyncHandler(
    onException: (Throwable) -> Unit,
): AsyncHandler {
    val scope = rememberCoroutineScope()
    val onExceptionUpdated by rememberUpdatedState(onException)
    val handler = remember(scope) {
        object : AsyncHandler {
            override var isWorking: Boolean by mutableStateOf(false)

            override fun launch(
                coroutineContext: CoroutineContext,
                block: suspend CoroutineScope.() -> Unit,
            ): Job = scope.launch(Dispatchers.Main.immediate + coroutineContext, start = CoroutineStart.ATOMIC) {
                try {
                    isWorking = true
                    block()
                } catch (e: Throwable) {
                    onExceptionUpdated(e)
                } finally {
                    isWorking = false
                }
            }
        }
    }
    return handler
}
