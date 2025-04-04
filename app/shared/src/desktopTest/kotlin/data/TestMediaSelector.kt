/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:Suppress("CanBeParameter", "MemberVisibilityCanBePrivate")

package me.him188.ani.app.domain.media.framework

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.domain.media.selector.MaybeExcludedMedia
import me.him188.ani.app.domain.media.selector.MediaPreferenceItem
import me.him188.ani.app.domain.media.selector.MediaSelector
import me.him188.ani.app.domain.media.selector.MediaSelectorEvents
import me.him188.ani.app.domain.media.selector.MutableMediaSelectorEvents
import me.him188.ani.app.domain.media.selector.OptionalPreference
import me.him188.ani.app.domain.media.selector.filter.MediaSelectorFilterSortAlgorithm
import me.him188.ani.app.domain.media.selector.orElse
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.topic.Resolution
import me.him188.ani.datasources.api.topic.SubtitleLanguage.ChineseSimplified
import me.him188.ani.datasources.api.topic.SubtitleLanguage.ChineseTraditional

class TestMediaPreferenceItem<T : Any>(
    override val available: MutableStateFlow<List<T>> = MutableStateFlow(emptyList()),
    override val userSelected: MutableStateFlow<OptionalPreference<T>> = MutableStateFlow(OptionalPreference.noPreference()),
    override val defaultSelected: MutableStateFlow<T?> = MutableStateFlow(null),
) : MediaPreferenceItem<T> {
    override val finalSelected: Flow<T?> = combine(userSelected, defaultSelected) { user, default ->
        user.orElse { default }
    }

    override suspend fun prefer(value: T) {
        userSelected.value = OptionalPreference.prefer(value)
    }

    override suspend fun removePreference() {
        userSelected.value = OptionalPreference.noPreference()
    }
}

open class TestMediaSelector(
    override val filteredCandidates: Flow<List<MaybeExcludedMedia>>,
    val defaultPreference: MutableStateFlow<MediaPreference> = MutableStateFlow(
        MediaPreference.Empty.copy(
            fallbackResolutions = listOf(
                Resolution.R2160P,
                Resolution.R1440P,
                Resolution.R1080P,
                Resolution.R720P,
            ).map { it.id },
            fallbackSubtitleLanguageIds = listOf(
                ChineseSimplified,
                ChineseTraditional,
            ).map { it.id },
        ),
    ),
    private val algorithm: MediaSelectorFilterSortAlgorithm = MediaSelectorFilterSortAlgorithm(),
) : MediaSelector {
    final override val filteredCandidatesMedia: Flow<List<Media>> =
        filteredCandidates.map { list -> list.mapNotNull { it.result } }
    final override val alliance: TestMediaPreferenceItem<String> = TestMediaPreferenceItem()
    final override val resolution: TestMediaPreferenceItem<String> = TestMediaPreferenceItem()
    final override val subtitleLanguageId: TestMediaPreferenceItem<String> = TestMediaPreferenceItem()
    final override val mediaSourceId: TestMediaPreferenceItem<String> = TestMediaPreferenceItem()

    private val mergedPreference = combine(
        defaultPreference,
        alliance.finalSelected,
        resolution.finalSelected,
        subtitleLanguageId.finalSelected,
        mediaSourceId.finalSelected,
    ) { default, alliance, resolution, subtitleLanguage, mediaSourceId ->
        default.copy(
            alliance = alliance,
            resolution = resolution,
            subtitleLanguageId = subtitleLanguage,
            mediaSourceId = mediaSourceId,
        )
    }
    final override val preferredCandidates: Flow<List<MaybeExcludedMedia>> =
        combine(filteredCandidates, mergedPreference) { mediaList, preference ->
            algorithm.filterByPreference(mediaList, preference)
        }

    final override val preferredCandidatesMedia: Flow<List<Media>> =
        preferredCandidates.map { list -> list.mapNotNull { it.result } }

    final override val selected: MutableStateFlow<Media?> = MutableStateFlow(null)
    final override val events: MediaSelectorEvents = MutableMediaSelectorEvents()

    override suspend fun select(candidate: Media): Boolean {
        if (this.selected.value == candidate) return false
        this.selected.value = candidate
        return true
    }

    override fun unselect() {
        this.selected.value = null
    }

    override suspend fun trySelectDefault(): Media? {
        throw UnsupportedOperationException()
    }

    override suspend fun trySelectFromMediaSources(
        mediaSourceOrder: List<String>,
        overrideUserSelection: Boolean,
        blacklistMediaIds: Set<String>,
        allowNonPreferred: Boolean
    ): Media? {
        throw UnsupportedOperationException()
    }

    override suspend fun trySelectCached(): Media? {
        throw UnsupportedOperationException()
    }

    override suspend fun removePreferencesUntilFirstCandidate() {
        throw UnsupportedOperationException()
    }
}
