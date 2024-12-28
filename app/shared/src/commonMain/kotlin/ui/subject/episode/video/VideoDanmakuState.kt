/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.video

import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.models.danmaku.DanmakuFilterConfig
import me.him188.ani.app.data.models.danmaku.DanmakuRegexFilter
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.data.models.episode.displayName
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.domain.danmaku.CombinedDanmakuFetchResult
import me.him188.ani.app.domain.danmaku.protocol.DanmakuInfo
import me.him188.ani.app.tools.MonoTasker
import me.him188.ani.app.ui.foundation.BackgroundScope
import me.him188.ani.app.ui.foundation.HasBackgroundScope
import me.him188.ani.app.ui.subject.episode.statistics.DanmakuLoadingState
import me.him188.ani.danmaku.api.Danmaku
import me.him188.ani.danmaku.api.DanmakuCollection
import me.him188.ani.danmaku.api.DanmakuEvent
import me.him188.ani.danmaku.api.DanmakuPresentation
import me.him188.ani.danmaku.api.DanmakuSearchRequest
import me.him188.ani.danmaku.api.DanmakuSession
import me.him188.ani.danmaku.api.TimeBasedDanmakuSession
import me.him188.ani.danmaku.api.emptyDanmakuCollection
import me.him188.ani.danmaku.ui.DanmakuConfig
import me.him188.ani.danmaku.ui.DanmakuHostState
import me.him188.ani.danmaku.ui.DanmakuTrackProperties
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@Stable
abstract class DanmakuStatistics {
    abstract val danmakuLoadingState: DanmakuLoadingState

    val isDanmakuLoading by derivedStateOf {
        this.danmakuLoadingState is DanmakuLoadingState.Loading
    }
}

@Stable
class MutableDanmakuStatistics : DanmakuStatistics() {
    override var danmakuLoadingState: DanmakuLoadingState by mutableStateOf(DanmakuLoadingState.Idle)
}

@Stable
class DelegateDanmakuStatistics(
    danmakuLoadingState: State<DanmakuLoadingState>,
) : DanmakuStatistics() {
    override val danmakuLoadingState: DanmakuLoadingState by danmakuLoadingState
}

class LoadDanmakuRequest(
    val subjectInfo: SubjectInfo,
    val episodeInfo: EpisodeInfo,
    val episodeId: Int,
    val filename: String?,
    val fileLength: Long?,
)

@Stable
interface DanmakuLoader {
    val state: StateFlow<DanmakuLoadingState>
    val eventFlow: Flow<DanmakuEvent>

    suspend fun requestRepopulate()
}

@Stable
class DanmakuLoaderImpl(
    requestFlow: Flow<LoadDanmakuRequest?>,
    currentPosition: Flow<Duration>,
    danmakuFilterConfig: Flow<DanmakuFilterConfig>,
    danmakuRegexFilterList: Flow<List<DanmakuRegexFilter>>,
    private val onFetch: suspend (request: DanmakuSearchRequest) -> CombinedDanmakuFetchResult,
    parentCoroutineContext: CoroutineContext,
) : HasBackgroundScope by BackgroundScope(parentCoroutineContext), DanmakuLoader {
    override val state: MutableStateFlow<DanmakuLoadingState> = MutableStateFlow(DanmakuLoadingState.Idle)

    private val collectionFlow: Flow<DanmakuCollection> =
        requestFlow.distinctUntilChanged().transformLatest { request ->
            emit(emptyDanmakuCollection()) // 每次更换 mediaFetchSession 时 (ep 变更), 首先清空历史弹幕

            if (request == null) {
                state.value = DanmakuLoadingState.Idle
                return@transformLatest
            }
            state.value = DanmakuLoadingState.Loading
            val filename = request.filename
            try {
                val subject = request.subjectInfo
                val episode = request.episodeInfo
                val result = onFetch(
                    DanmakuSearchRequest(
                        subjectId = subject.subjectId,
                        subjectPrimaryName = subject.displayName,
                        subjectNames = subject.allNames,
                        subjectPublishDate = subject.airDate,
                        episodeId = episode.episodeId,
                        episodeSort = episode.sort,
                        episodeEp = episode.ep,
                        episodeName = episode.displayName,
                        filename = filename,
                        fileHash = "aa".repeat(16),
                        fileSize = request.fileLength,
                        videoDuration = 0.milliseconds,
                    ),
                )
                state.value = DanmakuLoadingState.Success(result.matchInfos)
                emit(TimeBasedDanmakuSession.create(result.list))
            } catch (e: CancellationException) {
                state.value = DanmakuLoadingState.Idle
                throw e
            } catch (e: Throwable) {
                state.value = DanmakuLoadingState.Failed(e)
                throw e
            }
        }.shareInBackground(started = SharingStarted.Lazily)

    private val sessionFlow: Flow<DanmakuSession> = collectionFlow.mapLatest { session ->
        session.at(
            progress = currentPosition,
            danmakuRegexFilterList = combine(danmakuFilterConfig, danmakuRegexFilterList) { config, list ->
                if (!config.enableRegexFilter) emptyList()
                else list.filter { it.enabled }.map { it.regex }
            },
        )
    }.shareInBackground(started = SharingStarted.Lazily)

    override val eventFlow: Flow<DanmakuEvent> = sessionFlow.flatMapLatest { it.events }
    override suspend fun requestRepopulate() {
        sessionFlow.first().requestRepopulate()
    }
}

@Stable
class VideoDanmakuState(
    danmakuEnabled: State<Boolean>,
    danmakuConfig: State<DanmakuConfig>,
    private val onSend: suspend (info: DanmakuInfo) -> Danmaku,
    private val onSetEnabled: suspend (enabled: Boolean) -> Unit,
    private val onHideController: () -> Unit,
    private val backgroundScope: CoroutineScope,
    danmakuTrackProperties: DanmakuTrackProperties = DanmakuTrackProperties.Default,
) {
    val danmakuHostState: DanmakuHostState =
        DanmakuHostState(danmakuConfig, danmakuTrackProperties)

    val enabled: Boolean by danmakuEnabled

    private val setEnabledTasker = MonoTasker(backgroundScope)
    fun setEnabled(enabled: Boolean) {
        setEnabledTasker.launch {
            onSetEnabled(enabled)
        }
    }

    var danmakuEditorText: String by mutableStateOf("")

    val sendDanmakuTasker = MonoTasker(backgroundScope)
    val isSending: StateFlow<Boolean> get() = sendDanmakuTasker.isRunning

    fun sendAsync(
        info: DanmakuInfo,
        then: (suspend () -> Unit)?
    ) {
        sendDanmakuTasker.launch {
            val danmaku = onSend(info)
            try {
                backgroundScope.launch {
                    // 如果用户此时暂停了视频, 这里就会一直挂起, 所以单独开一个
                    danmakuHostState.send(DanmakuPresentation(danmaku, isSelf = true))
                }
                onHideController()
                withContext(Dispatchers.Main) {
                    then?.invoke()
                }
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) {
                    danmakuEditorText = info.text
                }
            }
        }
    }
}
