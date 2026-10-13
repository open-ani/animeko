/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.mediasource.rss

import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import me.him188.ani.app.domain.mediasource.rss.RssMediaSourceArguments
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * 一个支持保存的配置的容器. 适用于一个 class, 每次需要修改其中的一个属性而保持其他属性不变.
 *
 * @param Container 包含许多子项配置的容器类型, 例如 [RssMediaSourceArguments]
 */
class SaveableStorage<Container : Any>(
    /**
     * 当前的配置容器实例.
     *
     * `null` 表示还在加载中.
     */
    val containerState: State<Container?>,
    /**
     * 当有任意属性修改时调用.
     *
     * 该函数应该**立即**修改 [containerState], 也就是它在返回之前必须已经修改完成了 [containerState],
     * 否则会导致 UI 编辑框的用户输入被取消 (光标位置问题).
     *
     * 因此, 该保存通常应当实现为异步的: 先立即修改 UI 状态, 然后在后台保存修改.
     * 注意考虑 debounce, 因为用户每输入一个字符都会触发保存.
     *
     * Debounce 事件不宜太长, 否则会导致用户改完一个设置之后想要退出页面, 但是必须等 debounce 完成.
     */
    private val onSave: (Container) -> Unit,
    /**
     * 是否正在保存中. 可用于 UI 阻止用户退出页面.
     */
    val isSavingFlow: Flow<Boolean>,
) {
    val container by containerState

    /**
     * 获取引用 [Container] 的一个属性并支持修改这个属性.
     *
     * 属性不能为 `null`. 如需支持 `null`, 请使用 [propNullable].
     * @param copy 当修改时调用, 用于应用修改, 构造一个新的 [Container]
     * @param default 当 [container] 仍然为 `null`
     */
    fun <P : Any> prop(
        get: (Container) -> P,
        copy: Container.(P) -> Container,
        default: P,
    ): ReadWriteProperty<Any?, P> = object : ReadWriteProperty<Any?, P> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): P {
            return container?.let(get) ?: default
        }

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: P) {
            val arguments = container ?: return
            onSave(arguments.copy(value))
        }
    }

    /**
     * @param default 当 Config 内的属性返回 `null` 时使用.
     *
     * @see prop
     */
    fun <P> propNullable(
        get: (Container) -> P?,
        copy: Container.(P?) -> Container,
        default: P? = null,
    ): ReadWriteProperty<Any?, P?> = object : ReadWriteProperty<Any?, P?> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): P? {
            return container?.let(get) ?: default
        }

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: P?) {
            val arguments = container ?: return
            onSave(arguments.copy(value))
        }
    }

    /**
     * 立即覆盖当前整个容器
     */
    fun set(container: Container) {
        onSave(container)
    }
}

@TestOnly
fun <T : Any> createTestSaveableStorage(
    initialValue: T?,
    isSaving: Boolean = false,
): SaveableStorage<T> {
    val containerState = mutableStateOf(initialValue)
    return SaveableStorage(
        containerState = containerState,
        onSave = { containerState.value = it },
        isSavingFlow = MutableStateFlow(isSaving),
    )
}

/**
 * [SaveableStorage] 容器里的一个配置块. 读取时容器未加载则返回 [default]; 修改时构造新容器并保存.
 *
 * 嵌套的配置 (如 `arguments.searchConfig.autoMatch`) 用 [sub] 逐层取出, 每层只描述自己到父级的一对 get/copy.
 * 这样针对同一配置块类型的编辑状态与 UI (如自动匹配层) 可以在不同数据源的编辑器之间共用, 不关心它在各自参数里的位置.
 */
@Stable
class ConfigSection<C : Any>(
    private val read: () -> C?,
    private val write: (C) -> Unit,
    /**
     * 容器未加载时读到的值, 也作为嵌套块默认值的来源.
     */
    val default: C,
) {
    /**
     * 当前值, 容器未加载时为 [default].
     */
    val value: C get() = read() ?: default

    /**
     * 引用本块的一个属性并支持修改它. 属性不能为 `null`.
     */
    fun <P : Any> prop(
        get: (C) -> P,
        copy: C.(P) -> C,
    ): ReadWriteProperty<Any?, P> = object : ReadWriteProperty<Any?, P> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): P = read()?.let(get) ?: get(default)

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: P) {
            val current = read() ?: return
            write(current.copy(value))
        }
    }

    /**
     * 本块里的一个嵌套配置块.
     */
    fun <S : Any> sub(
        get: (C) -> S,
        copy: C.(S) -> C,
    ): ConfigSection<S> = ConfigSection(
        read = { read()?.let(get) },
        write = { sub -> read()?.let { write(it.copy(sub)) } },
        default = get(default),
    )
}

/**
 * 以整个容器为根的配置块.
 */
fun <C : Any> SaveableStorage<C>.section(default: C): ConfigSection<C> =
    ConfigSection(read = { container }, write = { set(it) }, default = default)
