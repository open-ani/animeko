/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.danmaku

import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import me.him188.ani.app.data.network.protocol.DanmakuInfo
import me.him188.ani.app.data.repository.danmaku.SearchDanmakuRequest
import me.him188.ani.app.domain.danmaku.DanmakuLoaderImpl
import me.him188.ani.app.domain.danmaku.SearchDanmakuUseCase
import me.him188.ani.app.domain.episode.SubjectEpisodeInfoBundle
import me.him188.ani.app.domain.settings.GetDanmakuRegexFilterListFlowUseCase
import me.him188.ani.app.domain.usecase.GlobalKoin
import me.him188.ani.app.tools.MonoTasker
import me.him188.ani.app.ui.subject.episode.video.DanmakuStatistics
import me.him188.ani.danmaku.api.Danmaku
import me.him188.ani.danmaku.api.DanmakuEvent
import me.him188.ani.danmaku.api.DanmakuPresentation
import me.him188.ani.danmaku.api.DanmakuSession
import me.him188.ani.danmaku.ui.DanmakuConfig
import me.him188.ani.danmaku.ui.DanmakuHostState
import me.him188.ani.danmaku.ui.DanmakuTrackProperties
import org.koin.core.Koin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.openani.mediamp.MediampPlayer
import kotlin.time.Duration.Companion.milliseconds

@Stable
class PlayerDanmakuState(
    player: MediampPlayer,
    bundleFlow: Flow<SubjectEpisodeInfoBundle>,

    danmakuEnabled: State<Boolean>,
    danmakuConfig: State<DanmakuConfig>,
    private val onSend: suspend (info: DanmakuInfo) -> Danmaku,
    private val onSetEnabled: suspend (enabled: Boolean) -> Unit,
    private val onHideController: () -> Unit,
    private val backgroundScope: CoroutineScope,
    danmakuTrackProperties: DanmakuTrackProperties = DanmakuTrackProperties.Default,
    private val koin: Koin = GlobalKoin,
) : KoinComponent {
    private val getDanmakuRegexFilterListFlowUseCase: GetDanmakuRegexFilterListFlowUseCase by inject()
    private val searchDanmakuUseCase: SearchDanmakuUseCase by inject()
    private val flowScope = backgroundScope

    private val danmakuLoader = DanmakuLoaderImpl(
        bundleFlow.map { bundle ->
            SearchDanmakuRequest(
                bundle.subjectInfo,
                bundle.episodeInfo,
                bundle.episodeId,
            )
        },
        backgroundScope,
        searchDanmakuUseCase = searchDanmakuUseCase,
    )

    val danmakuLoadingState = danmakuLoader.state
    val danmakuStatisticsFlow = danmakuLoadingState.map {
        DanmakuStatistics(it)
    }

    private val danmakuSessionFlow: Flow<DanmakuSession> = danmakuLoader.collectionFlow.mapLatest { session ->
        session.at(
            progress = player.currentPositionMillis.map { it.milliseconds },
            danmakuRegexFilterList = getDanmakuRegexFilterListFlowUseCase(),
        )
    }.shareIn(flowScope, started = SharingStarted.WhileSubscribed(), replay = 1)

    val danmakuEventFlow: Flow<DanmakuEvent> = danmakuSessionFlow.flatMapLatest { it.events }


    val danmakuHostState: DanmakuHostState = DanmakuHostState(danmakuConfig, danmakuTrackProperties)

    val enabled: Boolean by danmakuEnabled
    private val setEnabledTasker = MonoTasker(backgroundScope)

    var danmakuEditorText: String by mutableStateOf("")

    private val sendDanmakuTasker = MonoTasker(backgroundScope)
    val isSending: StateFlow<Boolean> get() = sendDanmakuTasker.isRunning

    fun setEnabled(enabled: Boolean) {
        setEnabledTasker.launch {
            onSetEnabled(enabled)
        }
    }

    suspend fun requestRepopulate() {
        danmakuSessionFlow.first().requestRepopulate()
    }

    suspend fun send(
        info: DanmakuInfo,
    ) {
        val deferred = sendDanmakuTasker.async {
            onSend(info)
        }

        val danmaku = try {
            deferred.await()
        } catch (e: Throwable) {
            danmakuEditorText = info.text
            null
        }

        danmaku?.let {
            backgroundScope.launch {
                // 如果用户此时暂停了视频, 这里就会一直挂起, 所以单独开一个
                danmakuHostState.send(DanmakuPresentation(danmaku, isSelf = true))
            }
        }

        onHideController()
    }

    override fun getKoin(): Koin = koin
}