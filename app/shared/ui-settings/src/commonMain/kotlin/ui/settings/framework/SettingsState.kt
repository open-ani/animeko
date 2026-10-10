/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.framework

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import me.him188.ani.app.data.repository.user.Settings
import me.him188.ani.app.tools.MonoTasker
import me.him188.ani.app.ui.foundation.produceState
import me.him188.ani.utils.platform.annotations.TestOnly


fun <Value : Placeholder, Placeholder> Settings<Value>.stateIn(
    backgroundScope: CoroutineScope,
    placeholder: Placeholder,
): BaseSettingsState<Value, Placeholder> {
    return BaseSettingsState(
        flow.produceState(placeholder, backgroundScope),
        onUpdate = { set(it) },
        placeholder,
        backgroundScope,
    )
}

typealias SettingsState<T> = BaseSettingsState<T, T>

/**
 * 封装一个设置项目, 用于在 UI 中使用.
 *
 * 有两个泛型以支持 [Placeholder] 与 [Value] 类型不同. 一般使用 [SettingsState] 即可.
 */
@Stable
class BaseSettingsState<in Value : Placeholder, out Placeholder>(
    valueState: State<Placeholder>,
    private val onUpdate: suspend (Value) -> Unit, // background scope
    private val placeholder: Placeholder,
    backgroundScope: CoroutineScope,
    private val initiallyLoaded: Boolean = false,
    /**
     * [onUpdate] 的启动方式. [CoroutineStart.UNDISPATCHED] 让它在 [update] 的调用线程上立即执行到第一个挂起点,
     * 之后的 [update] 无法在它开始之前将其取消.
     */
    private val updateStart: CoroutineStart = CoroutineStart.DEFAULT,
) : State<Placeholder> {
    private val tasker = MonoTasker(backgroundScope)
    fun update(value: Value) {
        tasker.launch(start = updateStart) {
            onUpdate(value)
        }
    }

    suspend fun updateSuspended(value: Value) {
        tasker.launch(start = updateStart) {
            onUpdate(value)
        }.join()
    }

    override val value: Placeholder by valueState
    val isLoading by derivedStateOf { !initiallyLoaded && value === placeholder }
    val isUpdating get() = tasker.isRunning
}

@TestOnly
fun <T> createTestSettingsState(value: T, backgroundScope: CoroutineScope): SettingsState<T> {
    val state = mutableStateOf(value)
    return SettingsState(state, onUpdate = { state.value = it }, value, backgroundScope)
}

@TestOnly
@Composable
fun <T> rememberTestSettingsState(value: T): SettingsState<T> {
    val scope = rememberCoroutineScope()
    return remember { createTestSettingsState(value, scope) }
}
