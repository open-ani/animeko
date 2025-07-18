/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation

import androidx.annotation.CallSuper
import androidx.compose.runtime.RememberObserver
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.thisLogger
import me.him188.ani.utils.logging.trace

/**
 * 带有 [backgroundScope], 当 [AbstractViewModel] 被 forget 时自动 close scope 以防资源泄露.
 *
 * 因此 [AbstractViewModel] 需要与 compose remember 一起使用, 否则需手动管理生命周期.
 * 在构造 [AbstractViewModel] 时需要考虑其声明周期问题.
 */ // We can't use Android's Viewmodel because it's not available in Desktop platforms. 
abstract class AbstractViewModel : RememberObserver, ViewModel(), HasBackgroundScope {
    val logger by lazy { thisLogger() }

    private var _backgroundScope = createBackgroundScope()
    override val backgroundScope: CoroutineScope
        get() {
            return _backgroundScope
        }


    private var referenceCount = 0

    @CallSuper
    override fun onAbandoned() {
        referenceCount--
    }

    @CallSuper
    override fun onForgotten() {
        referenceCount--
    }

    @CallSuper
    override fun onRemembered() {
        referenceCount++
        logger.trace { "${this::class.simpleName} onRemembered, refCount=$referenceCount" }
        if (referenceCount == 1) {
            this.init() // first remember
        }
    }

    private fun createBackgroundScope(): CoroutineScope {
        return CoroutineScope(
            CoroutineExceptionHandler { coroutineContext, throwable ->
                logger.error(throwable) { "Unhandled exception in background scope for viewmodel ${this::class.qualifiedName}, coroutineContext: $coroutineContext" }
            } + SupervisorJob(),
        )
    }

    /**
     * Called when the view model is remembered the first time.
     */
    protected open fun init() {
    }

    override fun onCleared() {
        backgroundScope.cancel()
        super.onCleared()
    }
}
