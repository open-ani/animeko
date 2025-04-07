/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.selector.legacy

import kotlinx.coroutines.flow.MutableStateFlow
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.data.models.subject.SubjectSeriesInfo
import me.him188.ani.app.domain.media.createTestDefaultMedia
import me.him188.ani.app.domain.media.createTestMediaProperties
import me.him188.ani.app.domain.media.selector.DefaultMediaSelector
import me.him188.ani.app.domain.media.selector.MediaSelectorContext
import me.him188.ani.app.domain.media.selector.MediaSelectorSourceTiers
import me.him188.ani.app.domain.media.selector.MediaSelectorSubtitlePreferences
import me.him188.ani.app.domain.media.selector.SubtitleKindPreference
import me.him188.ani.datasources.api.DefaultMedia
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.MediaExtraFiles
import me.him188.ani.datasources.api.SubtitleKind
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.source.MediaSourceLocation
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.datasources.api.topic.FileSize
import me.him188.ani.datasources.api.topic.FileSize.Companion.megaBytes
import me.him188.ani.datasources.api.topic.Resolution
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.datasources.api.topic.SubtitleLanguage
import me.him188.ani.utils.platform.collections.copyPut
import me.him188.ani.utils.platform.collections.toImmutable

/**
 * @suppress 已弃用, 新的 test 使用 [me.him188.ani.app.domain.media.selector.testFramework.TestMediaFetchSessionBuilder].
 * @see me.him188.ani.app.domain.media.selector.MediaSelectorAutoSelect
 */
abstract class AbstractDefaultMediaSelectorTest {
    protected val mediaList: MutableStateFlow<MutableList<DefaultMedia>> = MutableStateFlow(mutableListOf())
    protected fun addMedia(vararg media: DefaultMedia) {
        mediaList.value.addAll(media)
    }

    protected val savedUserPreference = MutableStateFlow(DEFAULT_PREFERENCE)
    protected val savedDefaultPreference = MutableStateFlow(DEFAULT_PREFERENCE)
    protected val mediaSelectorSettings = MutableStateFlow(MediaSelectorSettings.Companion.Default)
    protected val mediaSelectorContext = MutableStateFlow(
        createMediaSelectorContextFromEmpty(),
    )

    protected fun setSubtitlePreferences(
        preferences: MediaSelectorSubtitlePreferences = getCurrentSubtitlePreferences()
    ) {
        mediaSelectorContext.value = mediaSelectorContext.value.run {
            copy(subtitlePreferences = preferences)
        }
    }

    private fun getCurrentSubtitlePreferences() = (mediaSelectorContext.value.subtitlePreferences
        ?: MediaSelectorSubtitlePreferences.Companion.AllNormal)

    protected fun setSubtitlePreference(
        key: SubtitleKind,
        value: SubtitleKindPreference
    ) {
        setSubtitlePreferences(
            MediaSelectorSubtitlePreferences(getCurrentSubtitlePreferences().values.copyPut(key, value).toImmutable()),
        )
    }

    protected val selector = DefaultMediaSelector(
        mediaSelectorContextNotCached = mediaSelectorContext,
        mediaListNotCached = mediaList,
        savedUserPreference = savedUserPreference,
        savedDefaultPreference = savedDefaultPreference,
        enableCaching = false,
        mediaSelectorSettings = mediaSelectorSettings,
    )

    companion object {
        val DEFAULT_PREFERENCE = MediaPreference.Companion.Empty.copy(
            fallbackResolutions = listOf(
                Resolution.Companion.R2160P,
                Resolution.Companion.R1440P,
                Resolution.Companion.R1080P,
                Resolution.Companion.R720P,
            ).map { it.id },
            fallbackSubtitleLanguageIds = listOf(
                SubtitleLanguage.ChineseSimplified,
                SubtitleLanguage.ChineseTraditional,
            ).map { it.id },
        )

        const val SOURCE_DMHY = "dmhy"
        const val SOURCE_MIKAN = "mikan"

        @Suppress("SameParameterValue", "INVISIBLE_REFERENCE")
        @kotlin.internal.LowPriorityInOverloadResolution
        fun createMediaSelectorContextFromEmpty(
            subjectCompleted: Boolean = false,
            mediaSourcePrecedence: List<String> = emptyList(),
            subtitleKindFilters: MediaSelectorSubtitlePreferences = MediaSelectorSubtitlePreferences.Companion.AllNormal,
            subjectSequelNames: Set<String> = emptySet(),
            subjectInfo: SubjectInfo = SubjectInfo.Companion.Empty,
            episodeInfo: EpisodeInfo = EpisodeInfo.Companion.Empty,
        ) = createMediaSelectorContextFromEmpty(
            subjectCompleted = subjectCompleted,
            mediaSourcePrecedence = mediaSourcePrecedence,
            subtitleKindFilters = subtitleKindFilters,
            subjectSeriesInfo = SubjectSeriesInfo.Companion.Fallback.copy(sequelSubjectNames = subjectSequelNames),
            subjectInfo = subjectInfo,
            episodeInfo = episodeInfo,
        )

        @Suppress("SameParameterValue")
        fun createMediaSelectorContextFromEmpty(
            subjectCompleted: Boolean = false,
            mediaSourcePrecedence: List<String> = emptyList(),
            subtitleKindFilters: MediaSelectorSubtitlePreferences = MediaSelectorSubtitlePreferences.Companion.AllNormal,
            subjectSeriesInfo: SubjectSeriesInfo = SubjectSeriesInfo.Companion.Fallback,
            subjectInfo: SubjectInfo = SubjectInfo.Companion.Empty,
            episodeInfo: EpisodeInfo = EpisodeInfo.Companion.Empty,
        ) =
            MediaSelectorContext(
                subjectFinished = subjectCompleted,
                mediaSourcePrecedence = mediaSourcePrecedence,
                subtitlePreferences = subtitleKindFilters,
                subjectSeriesInfo = subjectSeriesInfo,
                subjectInfo = subjectInfo,
                episodeInfo = episodeInfo,
                mediaSourceTiers = MediaSelectorSourceTiers.Companion.Empty,
            )
    }

    private var mediaIdCounter: Int = 0
    fun media(
        sourceId: String = SOURCE_DMHY,
        resolution: String = "1080P",
        alliance: String = "字幕组",
        size: FileSize = 1.megaBytes,
        publishedTime: Long = 0,
        subtitleLanguages: List<String> = listOf(
            SubtitleLanguage.ChineseSimplified,
            SubtitleLanguage.ChineseTraditional,
        ).map { it.id },
        location: MediaSourceLocation = MediaSourceLocation.Online,
        kind: MediaSourceKind = MediaSourceKind.BitTorrent,
        episodeRange: EpisodeRange = EpisodeRange.Companion.single(EpisodeSort(1)),
        subtitleKind: SubtitleKind? = null,
        extraFiles: MediaExtraFiles = MediaExtraFiles.Companion.EMPTY,
        id: Int = mediaIdCounter++,
        originalTitle: String = "[字幕组] 孤独摇滚 $id",
        subjectName: String? = null,
        episodeName: String? = null,
        mediaId: String = "$sourceId.$id",
    ): DefaultMedia {
        return createTestDefaultMedia(
            mediaId = mediaId,
            mediaSourceId = sourceId,
            originalTitle = originalTitle,
            download = ResourceLocation.MagnetLink("magnet:?xt=urn:btih:$id"),
            originalUrl = "https://example.com/$id",
            publishedTime = publishedTime,
            episodeRange = episodeRange,
            properties = createTestMediaProperties(
                subjectName = subjectName,
                episodeName = episodeName,
                subtitleLanguageIds = subtitleLanguages,
                resolution = resolution,
                alliance = alliance,
                size = size,
                subtitleKind = subtitleKind,
            ),
            location = location,
            kind = kind,
            extraFiles = extraFiles,
        )
    }
}