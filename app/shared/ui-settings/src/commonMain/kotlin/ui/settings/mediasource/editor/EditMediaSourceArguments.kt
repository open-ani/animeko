/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.mediasource.editor

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import me.him188.ani.app.domain.media.fetch.MediaSourceManager
import me.him188.ani.app.domain.media.fetch.updateMediaSourceArguments
import me.him188.ani.app.domain.mediasource.codec.MediaSourceArguments
import me.him188.ani.app.platform.currentAniBuildConfig
import me.him188.ani.app.tools.MonoTasker
import me.him188.ani.app.ui.settings.mediasource.rss.SaveableStorage
import me.him188.ani.datasources.api.source.deserializeArgumentsOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * 编辑一个数据源实例的参数: 从 [MediaSourceManager] 加载, 每次修改先立即更新 UI 状态, 再延迟 [saveDebounce] 写回.
 *
 * 订阅来的实例不允许编辑 (debug 构建除外). [createState] 在为该实例开启的 [CoroutineScope] 内构造页面状态,
 * 该 scope 随实例切换或 flow 取消而取消, 页面状态里的后台任务都应启动在它上面.
 */
fun <A : MediaSourceArguments, S> MediaSourceManager.editMediaSourceArguments(
    instanceIdFlow: Flow<String>,
    serializer: KSerializer<A>,
    default: A,
    saveDebounce: Duration = 500.milliseconds,
    createState: CoroutineScope.(
        instanceId: String,
        argumentsStorage: SaveableStorage<A>,
        allowEditState: State<Boolean>,
    ) -> S,
): Flow<S> = instanceIdFlow.transformLatest { instanceId ->
    coroutineScope {
        val saveTasker = MonoTasker(this)
        val arguments = mutableStateOf<A?>(null)
        val allowEdit = mutableStateOf(false)
        launch {
            val config = instanceConfigFlow(instanceId).first()
            val persisted = config?.deserializeArgumentsOrNull(serializer) ?: default
            withContext(Dispatchers.Main) {
                arguments.value = persisted
                allowEdit.value = (config != null && config.subscriptionId == null) || currentAniBuildConfig.isDebug
            }
        }
        val storage = SaveableStorage(
            arguments,
            onSave = {
                arguments.value = it
                saveTasker.launch {
                    delay(saveDebounce)
                    updateMediaSourceArguments(instanceId, serializer, it)
                }
            },
            isSavingFlow = saveTasker.isRunning,
        )
        emit(createState(instanceId, storage, allowEdit))
        awaitCancellation()
    }
}.flowOn(Dispatchers.Default)
