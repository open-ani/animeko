/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.mediaFetch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.app.domain.media.TestMediaList
import me.him188.ani.app.domain.media.selector.DefaultMediaSelector
import me.him188.ani.app.domain.media.selector.MaybeExcludedMedia
import me.him188.ani.app.domain.media.selector.MediaPreferenceItem
import me.him188.ani.app.domain.media.selector.MediaSelector
import me.him188.ani.app.domain.media.selector.MediaSelectorContext
import me.him188.ani.app.tools.MonoTasker
import me.him188.ani.app.ui.foundation.rememberBackgroundScope
import me.him188.ani.datasources.api.Media
import me.him188.ani.utils.platform.annotations.TestOnly

@Composable
fun rememberMediaSelectorState(
    mediaSourceInfoProvider: MediaSourceInfoProvider,
    mediaSelector: () -> MediaSelector,// lambda remembered
): MediaSelectorState {
    val scope = rememberBackgroundScope()
    val selector by remember {
        derivedStateOf(mediaSelector)
    }
    return remember {
        MediaSelectorState(selector, mediaSourceInfoProvider, scope.backgroundScope)
    }
}

@Stable
class MediaPreferenceItemState<T : Any>(
    @PublishedApi internal val item: MediaPreferenceItem<T>,
    backgroundScope: CoroutineScope,
) {
    data class Presentation<T : Any>(
        val available: List<T>,
        val finalSelected: T?,
        val isWorking: Boolean,
        val isPlaceholder: Boolean = false,
    ) {
        companion object {
            private val Placeholder = Presentation(emptyList(), null, isWorking = false, isPlaceholder = true)

            @Suppress("UNCHECKED_CAST")
            fun <T : Any> placeholder(): Presentation<T> = Placeholder as Presentation<T>
        }
    }

    private val tasker = MonoTasker(backgroundScope)

    val presentationFlow = combine(
        item.available,
        item.finalSelected,
        tasker.isRunning,
        transform = ::Presentation,
    ).stateIn(
        backgroundScope, started = SharingStarted.WhileSubscribed(),
        Presentation<T>(emptyList(), null, isWorking = false, isPlaceholder = true),
    )

    /**
     * 用户选择
     */
    fun prefer(value: T): Job {
        return tasker.launch(start = CoroutineStart.UNDISPATCHED) { item.prefer(value) }
    }

    /**
     * 删除已有的选择
     */
    fun removePreference(): Job {
        return tasker.launch(start = CoroutineStart.UNDISPATCHED) { item.removePreference() }
    }
}

fun <T : Any> MediaPreferenceItemState<T>.preferOrRemove(value: T?): Job {
    return if (value == null || value == presentationFlow.value.finalSelected) {
        removePreference()
    } else {
        prefer(value)
    }
}


/**
 * Wraps [MediaSelector] to provide states for UI.
 */
class MediaSelectorState(
    private val mediaSelector: MediaSelector,
    val mediaSourceInfoProvider: MediaSourceInfoProvider,
    private val backgroundScope: CoroutineScope,
) {
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
    ) { filteredCandidatesMedia, preferredCandidates, selected, alliance, resolution, subtitleLanguageId, mediaSource ->
        val (groupsExcluded, groupsIncluded) = MediaGrouper.buildGroups(preferredCandidates).partition { it.isExcluded }
        Presentation(
            filteredCandidatesMedia,
            preferredCandidates.mapNotNull { it.result },
            groupsIncluded,
            groupsExcluded,
            selected,
            alliance, resolution, subtitleLanguageId, mediaSource,
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
            isPlaceholder = true,
        ),
    )

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
fun rememberTestMediaSelectorPresentation(): MediaSelectorState {
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
        createTestMediaSourceInfoProvider(),
        backgroundScope,
    )

