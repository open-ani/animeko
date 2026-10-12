/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.mediasource.api

import androidx.annotation.UiThread
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.app.data.repository.RepositoryUnknownException
import me.him188.ani.app.domain.media.resolver.MediaResolutionException
import me.him188.ani.app.domain.media.resolver.ResolutionFailures
import me.him188.ani.app.domain.mediasource.api.ApiMediaSource
import me.him188.ani.app.domain.mediasource.api.ApiMediaSourceArguments
import me.him188.ani.app.domain.mediasource.test.RefreshResult
import me.him188.ani.app.tools.MonoTasker
import me.him188.ani.app.ui.settings.mediasource.AbstractMediaSourceTestState
import me.him188.ani.datasources.api.matcher.WebVideo
import me.him188.ani.datasources.api.source.BrowseChannel
import me.him188.ani.datasources.api.source.BrowseEpisode
import me.him188.ani.datasources.api.source.BrowseSubject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

@Immutable
sealed class ApiTestSubjectResult : RefreshResult {
    @Immutable
    data class Success(val subjects: List<BrowseSubject>) : ApiTestSubjectResult(), RefreshResult.Success

    @Immutable
    data object InvalidConfig : ApiTestSubjectResult(), RefreshResult.InvalidConfig

    @Immutable
    data class ApiError(override val exception: RepositoryException) : ApiTestSubjectResult(), RefreshResult.ApiError

    @Immutable
    data class UnknownError(override val exception: Throwable) : ApiTestSubjectResult(), RefreshResult.UnknownError
}

@Immutable
sealed class ApiTestEpisodeResult : RefreshResult {
    @Immutable
    data class Success(val channels: List<BrowseChannel>) : ApiTestEpisodeResult(), RefreshResult.Success

    @Immutable
    data class ApiError(override val exception: RepositoryException) : ApiTestEpisodeResult(), RefreshResult.ApiError

    @Immutable
    data class UnknownError(override val exception: Throwable) : ApiTestEpisodeResult(), RefreshResult.UnknownError
}

@Immutable
sealed class ApiTestVideoResult : RefreshResult {
    abstract val episode: BrowseEpisode

    @Immutable
    data class Success(
        override val episode: BrowseEpisode,
        val video: WebVideo,
    ) : ApiTestVideoResult(), RefreshResult.Success

    @Immutable
    data class ApiError(
        override val episode: BrowseEpisode,
        override val exception: RepositoryException,
    ) : ApiTestVideoResult(), RefreshResult.ApiError

    @Immutable
    data class UnknownError(
        override val episode: BrowseEpisode,
        override val exception: Throwable,
    ) : ApiTestVideoResult(), RefreshResult.UnknownError
}

/**
 * 条目搜索的输入. 关键词与配置任一变化都重新搜索.
 */
@Immutable
data class ApiTestData(
    val keyword: String,
    val arguments: ApiMediaSourceArguments?,
)

@Immutable
data class ApiTestPresentation(
    val isSearchingSubject: Boolean,
    val subjectResult: ApiTestSubjectResult?,
    val selectedSubjectIndex: Int,
    val selectedSubject: BrowseSubject?,
    val isSearchingEpisode: Boolean,
    val episodeResult: ApiTestEpisodeResult?,
    val selectedChannelIndex: Int,
    val isResolvingVideo: Boolean,
    val videoResult: ApiTestVideoResult?,
    val isPlaceholder: Boolean = false,
) {
    val channels: List<BrowseChannel>
        get() = (episodeResult as? ApiTestEpisodeResult.Success)?.channels.orEmpty()

    val selectedChannel: BrowseChannel?
        get() = channels.getOrNull(selectedChannelIndex)

    @Stable
    companion object {
        @Stable
        val Placeholder = ApiTestPresentation(
            isSearchingSubject = false,
            subjectResult = null,
            selectedSubjectIndex = 0,
            selectedSubject = null,
            isSearchingEpisode = false,
            episodeResult = null,
            selectedChannelIndex = 0,
            isResolvingVideo = false,
            videoResult = null,
            isPlaceholder = true,
        )
    }
}

/**
 * 测试功能的状态.
 *
 * 关键词或已保存的配置变化后自动重新搜索条目; 选中的条目变化后自动请求线路与剧集; 点击剧集时解析视频地址.
 * 三步分别对应 [ApiMediaSource.searchSubjects], [ApiMediaSource.browseSubject] 与 [ApiMediaSource.resolveVideo].
 * 后两步总是使用产生上一步结果的那份配置, 所以配置改动只会经由 (有防抖的) 条目搜索传播下去.
 *
 * @see EditApiMediaSourceState
 */
@Stable
class ApiTestState(
    private val argumentsState: State<ApiMediaSourceArguments?>,
    private val createSource: (ApiMediaSourceArguments) -> ApiMediaSource,
    backgroundScope: CoroutineScope,
) : AbstractMediaSourceTestState() {
    // 持有 state 以保证切换页面时仍然有滚动状态
    val gridState = LazyGridState()

    private val subjectTasker = MonoTasker(backgroundScope)
    private val episodeTasker = MonoTasker(backgroundScope)
    private val videoTasker = MonoTasker(backgroundScope)

    private val subjectResultFlow = MutableStateFlow<ApiTestSubjectResult?>(null)
    private val episodeResultFlow = MutableStateFlow<ApiTestEpisodeResult?>(null)
    private val videoResultFlow = MutableStateFlow<ApiTestVideoResult?>(null)
    private val selectedSubjectIndexFlow = MutableStateFlow(0)
    private val selectedChannelIndexFlow = MutableStateFlow(0)

    private val testDataState = derivedStateOf {
        ApiTestData(
            keyword = searchKeyword.ifEmpty { searchKeywordPlaceholder },
            arguments = argumentsState.value,
        )
    }

    // 以下三个只在 UI 线程读写: 记录每一步最近一次请求的输入, 供 "刷新" 按钮和下一步使用
    private var currentTestData: ApiTestData? = null
    private var currentEpisodeQuery: Pair<BrowseSubject, ApiMediaSourceArguments>? = null
    private var currentVideoQuery: Triple<BrowseSubject, BrowseChannel, BrowseEpisode>? = null

    private fun ApiTestSubjectResult?.subjectAt(index: Int): BrowseSubject? =
        (this as? ApiTestSubjectResult.Success)?.subjects?.getOrNull(index)

    private val selectedSubjectFlow = combine(subjectResultFlow, selectedSubjectIndexFlow) { result, index ->
        result.subjectAt(index)
    }

    private class SubjectPart(
        val isSearching: Boolean,
        val result: ApiTestSubjectResult?,
        val selectedIndex: Int,
        val selected: BrowseSubject?,
    )

    private class EpisodePart(
        val isSearching: Boolean,
        val result: ApiTestEpisodeResult?,
        val selectedChannelIndex: Int,
    )

    private class VideoPart(
        val isResolving: Boolean,
        val result: ApiTestVideoResult?,
    )

    val presentation = combine(
        // selectedSubject 直接由 result 与 index 算出, 不经过 selectedSubjectFlow, 避免同一次发射里两者不一致
        combine(subjectTasker.isRunning, subjectResultFlow, selectedSubjectIndexFlow) { running, result, index ->
            SubjectPart(running, result, index, result.subjectAt(index))
        },
        combine(episodeTasker.isRunning, episodeResultFlow, selectedChannelIndexFlow, ::EpisodePart),
        combine(videoTasker.isRunning, videoResultFlow, ::VideoPart),
    ) { subject, episode, video ->
        ApiTestPresentation(
            isSearchingSubject = subject.isSearching,
            subjectResult = subject.result,
            selectedSubjectIndex = subject.selectedIndex,
            selectedSubject = subject.selected,
            isSearchingEpisode = episode.isSearching,
            episodeResult = episode.result,
            selectedChannelIndex = episode.selectedChannelIndex,
            isResolvingVideo = video.isResolving,
            videoResult = video.result,
        )
    }.shareIn(backgroundScope, SharingStarted.WhileSubscribed(5000), replay = 1)

    @UiThread
    fun selectSubject(index: Int) {
        if (selectedSubjectIndexFlow.value == index) return
        // 先清掉旧条目的线路, 让每一次状态发射都是一致的; 新条目的线路由 observeChanges 请求
        clearEpisodeResult()
        selectedSubjectIndexFlow.value = index
    }

    @UiThread
    fun selectChannel(index: Int) {
        clearVideoResult()
        selectedChannelIndexFlow.value = index
    }

    /**
     * 在 UI 中启动: 关键词或配置变化时重新搜索条目 (有防抖), 选中的条目变化时重新请求线路与剧集.
     */
    @UiThread
    suspend fun observeChanges() {
        try {
            coroutineScope {
                launch {
                    snapshotFlow { testDataState.value }
                        .distinctUntilChanged()
                        .debounce(0.5.seconds)
                        .collect { restartSubjectSearch(it) }
                }
                launch {
                    // 只跟随选中的条目. 配置变化先重新搜索条目 (结果会先清空再出现), 再经由这里传播到线路与剧集
                    selectedSubjectFlow
                        .distinctUntilChanged()
                        .collect { subject -> restartEpisodeSearch(subject, currentTestData?.arguments) }
                }
            }
        } catch (e: CancellationException) {
            subjectTasker.cancel()
            episodeTasker.cancel()
            videoTasker.cancel()
            throw e
        }
    }

    @UiThread
    fun restartSubjectSearch() {
        currentTestData?.let { restartSubjectSearch(it) }
    }

    @UiThread
    fun restartEpisodeSearch() {
        currentEpisodeQuery?.let { (subject, arguments) -> restartEpisodeSearch(subject, arguments) }
    }

    @UiThread
    fun restartVideoResolution() {
        currentVideoQuery?.let { (subject, channel, episode) -> resolveVideo(subject, channel, episode) }
    }

    @UiThread
    private fun restartSubjectSearch(testData: ApiTestData) {
        // 只改配置时保留用户选中的条目, 换关键词才回到第一个
        if (testData.keyword != currentTestData?.keyword) selectedSubjectIndexFlow.value = 0
        currentTestData = testData
        subjectResultFlow.value = null
        clearEpisodeResult()
        val arguments = testData.arguments
        if (arguments == null || runCatching { arguments.validate() }.isFailure) {
            subjectTasker.cancel()
            subjectResultFlow.value = ApiTestSubjectResult.InvalidConfig
            return
        }
        subjectTasker.launch {
            subjectResultFlow.value = try {
                ApiTestSubjectResult.Success(createSource(arguments).searchSubjects(testData.keyword))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                classify(e, ApiTestSubjectResult::ApiError, ApiTestSubjectResult::UnknownError)
            }
        }
    }

    @UiThread
    private fun restartEpisodeSearch(subject: BrowseSubject?, arguments: ApiMediaSourceArguments?) {
        clearEpisodeResult()
        if (subject == null || arguments == null) return
        currentEpisodeQuery = subject to arguments
        episodeTasker.launch {
            episodeResultFlow.value = try {
                ApiTestEpisodeResult.Success(createSource(arguments).browseSubject(subject))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                classify(e, ApiTestEpisodeResult::ApiError, ApiTestEpisodeResult::UnknownError)
            }
        }
    }

    /**
     * 像播放时那样: 先用浏览结果构造 [me.him188.ani.datasources.api.Media], 再向站点请求新的视频地址.
     * 使用的是产生当前线路列表的那份配置.
     */
    @UiThread
    fun resolveVideo(subject: BrowseSubject, channel: BrowseChannel, episode: BrowseEpisode) {
        val arguments = currentEpisodeQuery?.second ?: return
        clearVideoResult()
        currentVideoQuery = Triple(subject, channel, episode)
        videoTasker.launch {
            videoResultFlow.value = try {
                val source = createSource(arguments)
                val media = source.createMedia(subject, channel.name, episode, episode.episodeSort)
                ApiTestVideoResult.Success(episode, source.resolveVideo(media))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                classify(
                    e,
                    { ApiTestVideoResult.ApiError(episode, it) },
                    { ApiTestVideoResult.UnknownError(episode, it) },
                )
            }
        }
    }

    @UiThread
    private fun clearEpisodeResult() {
        episodeTasker.cancel()
        currentEpisodeQuery = null
        episodeResultFlow.value = null
        selectedChannelIndexFlow.value = 0
        clearVideoResult()
    }

    @UiThread
    private fun clearVideoResult() {
        videoTasker.cancel()
        currentVideoQuery = null
        videoResultFlow.value = null
    }
}

/**
 * 按 [RefreshResult] 的分类表达测试请求的异常: 网络、鉴权、限流等已知错误显示对应提示, 其余作为未知错误并可查看堆栈.
 * [ApiMediaSource.resolveVideo] 已把网络错误包装成 [MediaResolutionException], 这里取出原因再归类.
 */
private inline fun <R> classify(
    e: Exception,
    apiError: (RepositoryException) -> R,
    unknownError: (Throwable) -> R,
): R {
    val cause = if (e is MediaResolutionException && e.reason == ResolutionFailures.NETWORK_ERROR) {
        e.cause ?: e
    } else {
        e
    }
    return when (val wrapped = RepositoryException.wrapOrThrowCancellation(cause)) {
        is RepositoryUnknownException -> unknownError(e)
        else -> apiError(wrapped)
    }
}
