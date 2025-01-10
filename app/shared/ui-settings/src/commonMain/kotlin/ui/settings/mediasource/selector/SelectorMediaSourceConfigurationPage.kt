/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.mediasource.selector

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import io.ktor.client.plugins.BrowserUserAgent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.media.fetch.MediaSourceManager
import me.him188.ani.app.domain.media.fetch.updateMediaSourceArguments
import me.him188.ani.app.domain.media.resolver.WebViewVideoExtractor
import me.him188.ani.app.domain.mediasource.codec.MediaSourceCodecManager
import me.him188.ani.app.domain.mediasource.web.DefaultSelectorMediaSourceEngine
import me.him188.ani.app.domain.mediasource.web.SelectorMediaSourceArguments
import me.him188.ani.app.domain.settings.ProxyProvider
import me.him188.ani.app.domain.settings.collectProxyTo
import me.him188.ani.app.platform.Context
import me.him188.ani.app.tools.MonoTasker
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.app.ui.foundation.launchInBackground
import me.him188.ani.app.ui.foundation.produceState
import me.him188.ani.app.ui.settings.mediasource.rss.SaveableStorage
import me.him188.ani.datasources.api.source.HttpMediaSource
import me.him188.ani.datasources.api.source.asAutoCloseable
import me.him188.ani.datasources.api.source.createHttpClient
import me.him188.ani.datasources.api.source.deserializeArgumentsOrNull
import me.him188.ani.utils.ktor.registerLogging
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject

private typealias ArgumentsType = SelectorMediaSourceArguments

@Stable
class EditSelectorMediaSourceViewModel(
    initialInstanceId: String,
    context: Context,
) : AbstractViewModel(), KoinComponent {
    private val mediaSourceManager: MediaSourceManager by inject()
    private val settingsRepository: SettingsRepository by inject()
    private val proxyProvider: ProxyProvider by inject()

    private val instanceId: MutableStateFlow<String> = MutableStateFlow(initialInstanceId)

    private val arguments = this.instanceId.flatMapLatest { instanceId ->
        mediaSourceManager.instanceConfigFlow(instanceId).map {
            it?.deserializeArgumentsOrNull(
                ArgumentsType.serializer(),
            ) ?: ArgumentsType.Default
        }
    }

    val state: Flow<EditSelectorMediaSourcePageState> = this.instanceId.transformLatest { instanceId ->
        coroutineScope {
            val saveTasker = MonoTasker(this)
            val arguments = mutableStateOf<ArgumentsType?>(null)
            val allowEdit = mutableStateOf(false)
            launch {
                val config = mediaSourceManager.instanceConfigFlow(instanceId).first()
                val persisted = config
                    ?.deserializeArgumentsOrNull(ArgumentsType.serializer())
                    ?: ArgumentsType.Default
                withContext(Dispatchers.Main) {
                    arguments.value = persisted
                    allowEdit.value = config != null && config.subscriptionId == null
                }
            }
            emit(
                EditSelectorMediaSourcePageState(
                    argumentsStorage = SaveableStorage(
                        arguments,
                        onSave = {
                            arguments.value = it
                            saveTasker.launch {
                                delay(500)
                                mediaSourceManager.updateMediaSourceArguments(
                                    instanceId,
                                    ArgumentsType.serializer(),
                                    it,
                                )
                            }
                        },
                        isSavingFlow = saveTasker.isRunning,
                    ),
                    allowEditState = allowEdit,
                    engine = DefaultSelectorMediaSourceEngine(flowOf(client)),
                    webViewVideoExtractor = combine(
                        proxyProvider.proxy,
                        settingsRepository.videoResolverSettings.flow.distinctUntilChanged(),
                    ) { proxySettings, videoResolverSettings ->
                        WebViewVideoExtractor(proxySettings, videoResolverSettings)
                    }.produceState(null, this),
                    codecManager = get<MediaSourceCodecManager>(),
                    backgroundScope = this,
                    context,
                    flowDispatcher = Dispatchers.Default,
                ),
            )
            awaitCancellation()
        }
    }.flowOn(Dispatchers.Default)

    private val client by lazy {
        HttpMediaSource.createHttpClient {
            proxyProvider.proxy
            BrowserUserAgent()
        }.apply {
            registerLogging(logger)
            launchInBackground {
                proxyProvider.collectProxyTo(this@apply)
            }
            addCloseable(this.asAutoCloseable())
        }
    }

//    val testState: RssTestPaneState = RssTestPaneState(
//        // 这里用的是序列化之后的配置, 也就是只有保存成功之后, 才会更新测试 (和触发重新查询)
//        searchConfigState = arguments.map { it.searchConfig }.produceState(RssSearchConfig.Empty),
//        engine = DefaultRssMediaSourceEngine(client, parser = RssParser(includeOrigin = true)),
//        backgroundScope,
//    )
}