/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.mediasource.api

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import me.him188.ani.app.domain.foundation.HttpClientProvider
import me.him188.ani.app.domain.foundation.ScopedHttpClientUserAgent
import me.him188.ani.app.domain.foundation.get
import me.him188.ani.app.domain.media.fetch.MediaSourceManager
import me.him188.ani.app.domain.media.fetch.updateMediaSourceArguments
import me.him188.ani.app.domain.mediasource.api.ApiMediaSource
import me.him188.ani.app.domain.mediasource.api.ApiMediaSourceArguments
import me.him188.ani.app.domain.mediasource.codec.MediaSourceCodecManager
import me.him188.ani.app.tools.MonoTasker
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.app.ui.settings.mediasource.rss.SaveableStorage
import me.him188.ani.datasources.api.source.MediaSourceConfig
import me.him188.ani.datasources.api.source.deserializeArgumentsOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Stable
class EditApiMediaSourceViewModel(
    initialInstanceId: String,
) : AbstractViewModel(), KoinComponent {
    private val mediaSourceManager: MediaSourceManager by inject()
    private val codecManager: MediaSourceCodecManager by inject()
    private val httpClientProvider: HttpClientProvider by inject()

    private val instanceId: MutableStateFlow<String> = MutableStateFlow(initialInstanceId)

    private val arguments: Flow<ApiMediaSourceArguments> = this.instanceId.flatMapLatest { instanceId ->
        mediaSourceManager.instanceConfigFlow(instanceId).map {
            it?.deserializeArgumentsOrNull(ApiMediaSourceArguments.serializer()) ?: ApiMediaSourceArguments.Default
        }
    }

    private val saveTasker = MonoTasker(backgroundScope)
    private val testClient = httpClientProvider.get(ScopedHttpClientUserAgent.BROWSER)

    val state: Flow<EditApiMediaSourceState> = this.instanceId.transformLatest { instanceId ->
        coroutineScope {
            val arguments = mutableStateOf<ApiMediaSourceArguments?>(null)
            val allowEdit = mutableStateOf(false)
            launch {
                val config = mediaSourceManager.instanceConfigFlow(instanceId).first()
                val persisted = config
                    ?.deserializeArgumentsOrNull(ApiMediaSourceArguments.serializer())
                    ?: ApiMediaSourceArguments.Default
                withContext(Dispatchers.Main) {
                    arguments.value = persisted
                    allowEdit.value = config != null && config.subscriptionId == null
                }
            }
            emit(
                EditApiMediaSourceState(
                    argumentsStorage = SaveableStorage(
                        arguments,
                        onSave = {
                            arguments.value = it
                            saveTasker.launch {
                                mediaSourceManager.updateMediaSourceArguments(
                                    instanceId,
                                    ApiMediaSourceArguments.serializer(),
                                    it,
                                )
                            }
                        },
                        isSavingFlow = saveTasker.isRunning,
                    ),
                    allowEditState = allowEdit,
                    instanceId = instanceId,
                    codecManager = codecManager,
                ),
            )
        }
    }.flowOn(Dispatchers.Default)

    val testState: ApiTestState = ApiTestState(
        // 这里用的是已保存的配置, 也就是只有保存成功之后, 才会更新测试 (和触发重新查询)
        argumentsState = arguments.produceState<ApiMediaSourceArguments?>(null),
        createSource = { arguments ->
            ApiMediaSource(
                mediaSourceId = "api-test:$initialInstanceId",
                config = MediaSourceConfig(
                    serializedArguments = Json.encodeToJsonElement(ApiMediaSourceArguments.serializer(), arguments),
                ),
                client = testClient,
            )
        },
        backgroundScope = backgroundScope,
    )
}
