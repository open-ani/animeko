/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.app.domain.mediasource

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import me.him188.ani.app.domain.mediasource.codec.MediaSourceArguments
import me.him188.ani.datasources.api.source.MediaSourceConfig

/** 单个数据源的配置读写入口，生命周期由持有表单的 ViewModel 管理。 */
interface MediaSourceConfigurationEditor {
    val config: Flow<MediaSourceConfig?>
    val isSaving: Flow<Boolean>

    fun <T : MediaSourceArguments> saveArguments(serializer: KSerializer<T>, arguments: T)

    /** 停止接收新输入，已排队的保存可以继续完成。 */
    fun close()
}
