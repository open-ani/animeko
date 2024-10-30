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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import io.ktor.client.plugins.BrowserUserAgent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.media.fetch.MediaSourceManager
import me.him188.ani.app.domain.media.fetch.toClientProxyConfig
import me.him188.ani.app.domain.media.fetch.updateMediaSourceArguments
import me.him188.ani.app.domain.mediasource.codec.MediaSourceCodecManager
import me.him188.ani.app.domain.mediasource.rss.DefaultRssMediaSourceEngine
import me.him188.ani.app.domain.mediasource.rss.RssMediaSourceArguments
import me.him188.ani.app.domain.mediasource.rss.RssSearchConfig
import me.him188.ani.app.domain.rss.RssParser
import me.him188.ani.app.tools.MonoTasker
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.app.ui.settings.mediasource.rss.test.RssTestPaneState
import me.him188.ani.datasources.api.source.HttpMediaSource
import me.him188.ani.datasources.api.source.createHttpClient
import me.him188.ani.datasources.api.source.deserializeArgumentsOrNull
import me.him188.ani.utils.coroutines.onReplacement
import me.him188.ani.utils.ktor.proxy
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Stable
class EditRssMediaSourceViewModel(
    initialInstanceId: String,
) : AbstractViewModel(), KoinComponent {
    private val mediaSourceManager: MediaSourceManager by inject()
    private val settingsRepository: SettingsRepository by inject()
    private val codecManager: MediaSourceCodecManager by inject()

    private val instanceId: MutableStateFlow<String> = MutableStateFlow(initialInstanceId)

    private val arguments = this.instanceId.flatMapLatest { instanceId ->
        mediaSourceManager.instanceConfigFlow(instanceId).map {
            it?.deserializeArgumentsOrNull(
                RssMediaSourceArguments.serializer(),
            ) ?: RssMediaSourceArguments.Default
        }
    }

    val state: Flow<EditRssMediaSourceState> = this.instanceId.transformLatest { instanceId ->
        coroutineScope {
            val saveTasker = MonoTasker(this)
            val arguments = mutableStateOf<RssMediaSourceArguments?>(null)
            val allowEdit = mutableStateOf(false)
            launch {
                val config = mediaSourceManager.instanceConfigFlow(instanceId).first()
                val persisted = config
                    ?.deserializeArgumentsOrNull(RssMediaSourceArguments.serializer())
                    ?: RssMediaSourceArguments.Default
                withContext(Dispatchers.Main) {
                    arguments.value = persisted
                    allowEdit.value = config != null && config.subscriptionId == null
                }
            }
            emit(
                EditRssMediaSourceState(
                    argumentsStorage = SaveableStorage(
                        arguments,
                        onSave = {
                            arguments.value = it
                            saveTasker.launch {
                                mediaSourceManager.updateMediaSourceArguments(
                                    instanceId,
                                    RssMediaSourceArguments.serializer(),
                                    it,
                                )
                            }
                        },
                        isSavingState = derivedStateOf { saveTasker.isRunning },
                    ),
                    allowEditState = allowEdit,
                    instanceId = instanceId,
                    codecManager = codecManager,
                ),
            )
        }
    }.flowOn(Dispatchers.Default)

    private val client = settingsRepository.proxySettings.flow.map {
        HttpMediaSource.createHttpClient {
            proxy(it.default.toClientProxyConfig())
            BrowserUserAgent()
        }
    }.onReplacement {
        it.close()
    }.shareInBackground(started = SharingStarted.Lazily)

    val testState: RssTestPaneState = RssTestPaneState(
        // 这里用的是序列化之后的配置, 也就是只有保存成功之后, 才会更新测试 (和触发重新查询)
        searchConfigState = arguments.map { it.searchConfig }.produceState(RssSearchConfig.Empty),
        engine = DefaultRssMediaSourceEngine(client, parser = RssParser(includeOrigin = true)),
        backgroundScope,
    )

    override fun onCleared() {
        super.onCleared()
        client.replayCache.firstOrNull()?.close()
    }
}