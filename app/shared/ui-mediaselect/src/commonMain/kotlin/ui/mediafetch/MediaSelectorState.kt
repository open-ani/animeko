/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.mediafetch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.app.domain.media.TestMediaList
import me.him188.ani.app.domain.media.fetch.MediaSourceFetchResult
import me.him188.ani.app.domain.media.fetch.MediaSourceFetchState
import me.him188.ani.app.domain.media.fetch.isFailedOrAbandoned
import me.him188.ani.app.domain.media.fetch.isWorking
import me.him188.ani.app.domain.media.selector.DefaultMediaSelector
import me.him188.ani.app.domain.media.selector.MaybeExcludedMedia
import me.him188.ani.app.domain.media.selector.MediaPreferenceItem
import me.him188.ani.app.domain.media.selector.MediaSelector
import me.him188.ani.app.domain.media.selector.MediaSelectorContext
import me.him188.ani.app.domain.media.selector.isPerfectMatch
import me.him188.ani.app.ui.foundation.rememberBackgroundScope
import me.him188.ani.app.ui.mediaselect.selector.WebSource
import me.him188.ani.app.ui.mediaselect.selector.WebSourceChannel
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.utils.coroutines.flows.flowOfEmptyList
import me.him188.ani.utils.platform.annotations.TestOnly
import me.him188.ani.utils.platform.collections.tupleOf

// todo: shit
@Composable
fun rememberMediaSelectorState(
    mediaSourceInfoProvider: MediaSourceInfoProvider,
    filteredResults: Flow<List<MediaSourceFetchResult>>,
    mediaSelector: () -> MediaSelector,// lambda remembered
): MediaSelectorState {
    val scope = rememberBackgroundScope()
    val selector by remember {
        derivedStateOf(mediaSelector)
    }
    return remember {
        MediaSelectorState(
            selector,
            filteredResults,
            mediaSourceInfoProvider,
            flowOf(null),
            scope.backgroundScope,
        )
    }
}

/**
 * @param backgroundScope only used for flow stateIn (with SharingStarted.WhileSubscribed)
 */
@Stable
class MediaPreferenceItemState<T : Any>(
    @PublishedApi internal val item: MediaPreferenceItem<T>,
    backgroundScope: CoroutineScope,
) {
    data class Presentation<T : Any>(
        val available: List<T>,
        val finalSelected: T?,
        val isWorking: Boolean = false,
        val isPlaceholder: Boolean = false,
    ) {
        companion object {
            private val Placeholder = Presentation(emptyList(), null, isWorking = false, isPlaceholder = true)

            @Suppress("UNCHECKED_CAST")
            fun <T : Any> placeholder(): Presentation<T> = Placeholder as Presentation<T>
        }
    }

    val presentationFlow = combine(
        item.available,
        item.finalSelected,
        transform = ::Presentation,
    ).stateIn(
        backgroundScope, started = SharingStarted.WhileSubscribed(),
        Presentation.placeholder(),
    )

    /**
     * 用户选择
     */
    suspend fun prefer(value: T) {
        item.prefer(value)
    }

    /**
     * 删除已有的选择
     */
    suspend fun removePreference() {
        item.removePreference()
    }
}

suspend fun <T : Any> MediaPreferenceItemState<T>.preferOrRemove(value: T?) {
    return if (value == null || value == presentationFlow.value.finalSelected) {
        removePreference()
    } else {
        prefer(value)
    }
}


/**
 * Wraps [MediaSelector] to provide states for UI.
 */
@Stable
class MediaSelectorState(
    private val mediaSelector: MediaSelector,
    private val mediaSourceFetchResults: Flow<List<MediaSourceFetchResult>>,
    val mediaSourceInfoProvider: MediaSourceInfoProvider,
    private val preferredWebMediaSource: Flow<String?>,
    private val backgroundScope: CoroutineScope,
) {
    @Immutable
    data class Presentation(
        val filteredCandidates: List<MaybeExcludedMedia>,
        val preferredCandidates: List<Media>,
        val groupedMediaListIncluded: List<MediaGroup>,
        val groupedMediaListExcluded: List<MediaGroup>,
        val selected: Media?,
        val alliance: MediaPreferenceItemState.Presentation<String>,
        val resolution: MediaPreferenceItemState.Presentation<String>,
        val subtitleLanguageId: MediaPreferenceItemState.Presentation<String>,
        val mediaSource: MediaPreferenceItemState.Presentation<String>,
        // New MS
        val webSources: List<WebSource>,
        val selectedWebSource: WebSource?,
        val selectedWebSourceChannel: WebSourceChannel?,
        val isPlaceholder: Boolean = false,
    )

    private val groupStates: SnapshotStateMap<MediaGroupId, MediaGroupState> = SnapshotStateMap()

    fun getGroupState(groupId: MediaGroupId): MediaGroupState {
        return groupStates.getOrPut(groupId) {
            MediaGroupState(groupId)
        }
    }

    val alliance: MediaPreferenceItemState<String> =
        MediaPreferenceItemState(mediaSelector.alliance, backgroundScope)
    val resolution: MediaPreferenceItemState<String> =
        MediaPreferenceItemState(mediaSelector.resolution, backgroundScope)
    val subtitleLanguageId: MediaPreferenceItemState<String> =
        MediaPreferenceItemState(mediaSelector.subtitleLanguageId, backgroundScope)
    val mediaSource: MediaPreferenceItemState<String> =
        MediaPreferenceItemState(mediaSelector.mediaSourceId, backgroundScope)

    val presentationFlow = me.him188.ani.utils.coroutines.flows.combine(
        mediaSelector.filteredCandidates,
        mediaSelector.preferredCandidates,
        mediaSelector.selected,
        alliance.presentationFlow,
        resolution.presentationFlow,
        subtitleLanguageId.presentationFlow,
        mediaSource.presentationFlow,
        createWebSourcesFlow(),
    ) { filteredCandidatesMedia, preferredCandidates, selected, alliance, resolution, subtitleLanguageId, mediaSource, webSources ->
        val (groupsExcluded, groupsIncluded) = MediaGrouper.buildGroups(preferredCandidates).partition { it.isExcluded }
        Presentation(
            filteredCandidatesMedia,
            preferredCandidates.mapNotNull { it.result },
            groupsIncluded,
            groupsExcluded,
            selected,
            alliance, resolution, subtitleLanguageId, mediaSource,
            webSources,
            selectedWebSource = webSources.find { source -> source.channels.any { it.original == selected } },
            selectedWebSourceChannel = webSources.firstNotNullOfOrNull { source -> source.channels.find { it.original == selected } },
        )
    }.stateIn(
        backgroundScope,
        started = SharingStarted.WhileSubscribed(),
        Presentation(
            emptyList(), emptyList(), emptyList(), emptyList(), null,
            alliance = MediaPreferenceItemState.Presentation.placeholder(),
            resolution = MediaPreferenceItemState.Presentation.placeholder(),
            subtitleLanguageId = MediaPreferenceItemState.Presentation.placeholder(),
            mediaSource = MediaPreferenceItemState.Presentation.placeholder(),
            webSources = emptyList(),
            selectedWebSource = null,
            selectedWebSourceChannel = null,
            isPlaceholder = true,
        ),
    )

    private fun createWebSourcesFlow(): Flow<List<WebSource>> {
        // 第一次 collect 时不 delay, 尽快 emit, 否则 UI 会一直是 placeholder.
        // 见 createWebSourceFlow 里的注释.
        var isFirstCollect = true

        val sortedResultsFlow = mediaSourceFetchResults.flatMapLatest { results ->
            if (results.isEmpty()) return@flatMapLatest flowOfEmptyList()

            // 按顺序排序
            val sorted = results.filter { it.kind == MediaSourceKind.WEB } // 只使用 WEB

            // 监控状态, 把错误的放到最后
            combine(results.map { it.state }) { states ->
                sorted.sortedBy {
                    val state = states.getOrNull(results.indexOf(it))
                        ?: return@sortedBy 0 // should not happen. Just defensive
                    if (state is MediaSourceFetchState.Failed) {
                        1 // 错误的放后面
                    } else {
                        -1
                    }
                }
            }
        }

        return combine(
            sortedResultsFlow.distinctUntilChanged(),
            mediaSelector.filteredCandidates,
        ) { sources, mediaList ->
            tupleOf(sources, mediaList)
        }.flatMapLatest { (sources, allMediaList) ->
            val showWebSources = sources.map { source ->

                // 属于这个数据源的 medias
                val myMediaList = allMediaList
                    .asSequence()
                    .filter {
                        // Filter medias that are from this source
                        it.result?.mediaSourceId == source.mediaSourceId // null result gives `false` and is hence excluded
                    }
                    .filter {
                        // Take only exact matches
                        it.isPerfectMatch()
                    }
                    .mapNotNull { it.result }

                createWebSourceFlow(source, myMediaList, delayToOvercomeCacheIssue = isFirstCollect).also {
                    isFirstCollect = false
                }
            }
            if (showWebSources.isEmpty()) {
                flowOfEmptyList()
            } else {
                combine(
                    showWebSources,
                ) {
                    it.filterNotNull()
                }
            }
        }
    }

    private fun createWebSourceFlow(
        source: MediaSourceFetchResult,
        myMediaList: Sequence<Media>,
        delayToOvercomeCacheIssue: Boolean,
    ) = source.state.combine(preferredWebMediaSource) { a, b -> a to b }.map { (state, preferred) ->
        val channels = myMediaList.map { media ->
            WebSourceChannel(media.properties.alliance, original = media)
        }.toList()

        when {
            state is MediaSourceFetchState.Disabled -> {
                // 禁用的数据源一直排除.
                null
            }

            channels.isEmpty() && state is MediaSourceFetchState.Succeed -> {
                // MediaSelector 的 filteredCandidates 有 cache, 而 MediaSourceFetchResult.state 没有.
                // 当 state 为 Succeed 后, 我们可能仍然看到的是旧的 filteredCandidates, 导致 channels 为 empty.
                // 这里延迟一下可以非常轻易地解决问题.
                if (delayToOvercomeCacheIssue) {
                    delay(1000)
                }
                null // 查询成功, 0 条, 隐藏
            }

            else -> {
                WebSource(
                    instanceId = source.instanceId,
                    mediaSourceId = source.mediaSourceId,
                    iconUrl = source.sourceInfo.iconUrl ?: "",
                    name = source.sourceInfo.displayName,
                    channels = channels,
                    isLoading = state.isWorking,
                    isError = state.isFailedOrAbandoned,
                    isPreferred = source.mediaSourceId == preferred,
                )
            }
        }
    }

    /**
     * @see MediaSelector.select
     */
    fun select(candidate: Media) {
        backgroundScope.launch {
            mediaSelector.select(candidate)
        }
    }

    fun removePreferencesUntilFirstCandidate() {
        backgroundScope.launch {
            mediaSelector.removePreferencesUntilFirstCandidate()
        }
    }
}

@Stable
class MediaGroupState(
    val groupId: MediaGroupId,
) {
    var selectedItem: Media? by mutableStateOf(null)
}

///////////////////////////////////////////////////////////////////////////
// Testing
///////////////////////////////////////////////////////////////////////////

@Composable
@TestOnly
fun rememberTestMediaSelectorState(): MediaSelectorState {
    val backgroundScope = rememberBackgroundScope()
    return remember(backgroundScope) { createTestMediaSelectorState(backgroundScope.backgroundScope) }
}

@TestOnly
fun createTestMediaSelectorState(backgroundScope: CoroutineScope) =
    MediaSelectorState(
        DefaultMediaSelector(
            mediaSelectorContextNotCached = flowOf(MediaSelectorContext.EmptyForPreview),
            mediaListNotCached = MutableStateFlow(TestMediaList),
            savedUserPreference = flowOf(MediaPreference.Empty),
            savedDefaultPreference = flowOf(MediaPreference.Empty),
            mediaSelectorSettings = flowOf(MediaSelectorSettings.Default),
        ),
        mediaSourceFetchResults = createTestMediaSourceResultsFilterer(backgroundScope).filteredSourceResults,
        createTestMediaSourceInfoProvider(),
        preferredWebMediaSource = flowOf(null),
        backgroundScope,
    )

