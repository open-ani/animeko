/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.mediasource.editor

import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import me.him188.ani.app.domain.mediasource.codec.MediaSourceArguments
import me.him188.ani.app.ui.settings.mediasource.rss.ConfigSection
import me.him188.ani.app.ui.settings.mediasource.rss.SaveableStorage
import me.him188.ani.app.ui.settings.mediasource.rss.section

/**
 * 数据源配置编辑器的状态基类: 参数容器的加载与可编辑状态, 以及每种数据源都有的名称与图标.
 *
 * 各字段通过 [arguments] 取出的 [ConfigSection] 读写, 每次修改都构造新的参数对象交给 [SaveableStorage] 保存.
 */
@Stable
abstract class MediaSourceConfigState<A : MediaSourceArguments>(
    val argumentsStorage: SaveableStorage<A>,
    private val allowEditState: State<Boolean>,
    default: A,
) {
    protected val arguments: ConfigSection<A> = argumentsStorage.section(default)

    val isLoading: Boolean by derivedStateOf { argumentsStorage.container == null }

    /**
     * 参数已加载且允许编辑 (订阅来的数据源不允许).
     */
    val enableEdit: Boolean by derivedStateOf { !isLoading && allowEditState.value }

    abstract var displayName: String
    abstract var iconUrl: String

    val displayNameIsError: Boolean by derivedStateOf { displayName.isBlank() }
}
