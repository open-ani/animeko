/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.mediasource

import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import me.him188.ani.app.domain.media.fetch.MediaSourceManager
import me.him188.ani.app.domain.media.fetch.updateMediaSourceArguments
import me.him188.ani.app.domain.mediasource.codec.MediaSourceArguments
import me.him188.ani.app.domain.mediasource.MediaSourceConfigurationEditor
import me.him188.ani.app.tools.MonoTasker
import me.him188.ani.datasources.api.source.MediaSourceConfig

/**
 * 本机数据源 [instanceId] 的配置读写, 经 [manager] 持久化.
 *
 * 新的保存会取代尚未完成的保存; [debounce] 内的连续保存只写入最后一次.
 */
class LocalMediaSourceConfigurationEditor(
    private val manager: MediaSourceManager,
    private val instanceId: String,
    scope: CoroutineScope,
    private val debounce: Duration = Duration.ZERO,
) : MediaSourceConfigurationEditor {
    private val tasker = MonoTasker(scope)

    override val config: Flow<MediaSourceConfig?> = manager.instanceConfigFlow(instanceId)
    override val isSaving: Flow<Boolean> = tasker.isRunning

    override fun <T : MediaSourceArguments> saveArguments(serializer: KSerializer<T>, arguments: T) {
        tasker.launch {
            delay(debounce)
            manager.updateMediaSourceArguments(instanceId, serializer, arguments)
        }
    }

    override fun close() {}
}
