/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.selector

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.data.models.subject.SubjectSeriesInfo
import me.him188.ani.app.data.models.subject.SubjectSeriesInfoBuilder
import me.him188.ani.app.data.models.subject.toBuilder
import me.him188.ani.app.domain.media.createTestDefaultMedia
import me.him188.ani.app.domain.media.createTestMediaProperties
import me.him188.ani.datasources.api.DefaultMedia
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.MediaExtraFiles
import me.him188.ani.datasources.api.SubtitleKind
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.source.MediaSourceLocation
import me.him188.ani.datasources.api.topic.*
import me.him188.ani.datasources.api.topic.FileSize.Companion.megaBytes
import me.him188.ani.test.DynamicTestsBuilder
import me.him188.ani.utils.platform.collections.copyPut
import me.him188.ani.utils.platform.collections.toImmutable
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * DSL for testing [DefaultMediaSelector].
 */
class MediaSelectorTestSuite {
    val initApi = InitApi()
    val mediaApi = MediaApi()
    val preferenceApi = PreferenceApi()

    val selector = DefaultMediaSelector(
        mediaSelectorContextNotCached = preferenceApi.mediaSelectorContext,
        mediaListNotCached = mediaApi.mediaList,
        savedUserPreference = preferenceApi.savedUserPreference,
        savedDefaultPreference = preferenceApi.savedDefaultPreference,
        enableCaching = false,
        mediaSelectorSettings = preferenceApi.mediaSelectorSettings,
    )

    /**
     * initializes [mediaSelectorContext].
     */
    inner class InitApi {
        lateinit var subjectName: String
        var aliases: MutableList<String> = mutableListOf()
        var seriesInfo: SubjectSeriesInfo = SubjectSeriesInfo.Fallback
        var episodeSort: EpisodeSort = EpisodeSort(1)

        fun aliases(vararg aliases: String) {
            this.aliases.addAll(aliases)
        }

        var seasonSort: Int
            get() = seriesInfo.seasonSort
            set(value) {
                seriesInfo = seriesInfo.copy(seasonSort = value)
            }

        fun seriesInfo(
            seasonSort: Int = this.seasonSort, block: SubjectSeriesInfoBuilder.() -> Unit
        ) {
            seriesInfo = this.seriesInfo.toBuilder().also { it.seasonSort = seasonSort }.apply(block).build()
        }
    }

    inline fun initSubject(
        subjectName: String,
        block: InitApi.() -> Unit
    ) {
        val init = initApi.apply(block)
        init.subjectName = subjectName

        preferenceApi.mediaSelectorContext.value = createMediaSelectorContextFromEmpty(
            true,
            subjectInfo = SubjectInfo.Empty.copy(
                nameCn = init.subjectName,
                aliases = init.aliases.toList(),
            ),
            episodeInfo = EpisodeInfo.Empty.copy(sort = init.episodeSort),
            subjectSeriesInfo = init.seriesInfo,
        )
        preferenceApi.mediaSelectorSettings.value = MediaSelectorSettings.Default
        preferenceApi.savedUserPreference.value = DEFAULT_PREFERENCE
        preferenceApi.savedDefaultPreference.value = DEFAULT_PREFERENCE
    }

    inner class MediaApi {
        val mediaList: MutableStateFlow<MutableList<DefaultMedia>> = MutableStateFlow(mutableListOf())

        fun addMedia(vararg media: DefaultMedia) {
            mediaList.value.addAll(media)
        }

        fun addSimpleWebMedia(
            subjectName: String,
            episodeSort: EpisodeSort = EpisodeSort(1),
        ) {
            addMedia(
                media(
                    // kept
                    alliance = "简中",
                    episodeRange = EpisodeRange.single(episodeSort), kind = MediaSourceKind.WEB,
                    subjectName = subjectName,
                    originalTitle = "$subjectName $episodeSort",
                    subtitleLanguages = listOf(SubtitleLanguage.ChineseSimplified.id),
                ),
            )
        }
    }

    inner class PreferenceApi {
        val savedUserPreference = MutableStateFlow(DEFAULT_PREFERENCE)
        val savedDefaultPreference = MutableStateFlow(DEFAULT_PREFERENCE)
        val mediaSelectorSettings = MutableStateFlow(MediaSelectorSettings.Default)
        val mediaSelectorContext = MutableStateFlow(
            createMediaSelectorContextFromEmpty(),
        )

        fun setSubtitlePreferences(
            preferences: MediaSelectorSubtitlePreferences = getCurrentSubtitlePreferences()
        ) {
            mediaSelectorContext.value = mediaSelectorContext.value.run {
                copy(subtitlePreferences = preferences)
            }
        }

        private fun getCurrentSubtitlePreferences() = (mediaSelectorContext.value.subtitlePreferences
            ?: MediaSelectorSubtitlePreferences.AllNormal)

        fun setSubtitlePreference(
            key: SubtitleKind,
            value: SubtitleKindPreference
        ) {
            setSubtitlePreferences(
                MediaSelectorSubtitlePreferences(
                    getCurrentSubtitlePreferences().values.copyPut(key, value).toImmutable(),
                ),
            )
        }
    }

    inner class SubjectExclusionApi {
        private val checks = mutableListOf<Pair<String, MediaExclusionReason?>>()

        fun expect(subjectName: String, exclusionReason: MediaExclusionReason? = null) {
            mediaApi.addSimpleWebMedia(subjectName)
            checks.add(subjectName to exclusionReason)
        }

        fun expect(vararg pairs: Pair<String, MediaExclusionReason?>) {
            pairs.forEach { (subjectName, exclusionReason) ->
                expect(subjectName, exclusionReason)
            }
        }

        /**
         * 特殊标记, 用于标注该值是错误的, 但是目前算法没法做到区分这个.
         * @param current 目前算法的结果
         * @param actual 未来修复算法后, 应当得到的结果
         */
        fun <T> tentatively(current: T, @Suppress("UNUSED_PARAMETER") actual: T): T = current

        @OptIn(UnsafeOriginalMediaAccess::class)
        internal suspend fun checkAll() {
            if (checks.isNotEmpty()) {
                selector.filteredCandidates.first().forEach { media ->
                    val assertion = checks.singleOrNull { it.first == media.original.properties.subjectName }
                        ?: fail("Cannot find assertion for ${media.original.properties.subjectName}")

                    assertEquals(
                        assertion.second, media.exclusionReason,
                        message = "\"${assertion.first}\" should be ${assertion.second}, but was ${media.exclusionReason}",
                    )
                }
            }
        }
    }

    suspend fun checkSubjectExclusion(block: SubjectExclusionApi.() -> Unit) {
        SubjectExclusionApi().apply(block).checkAll()
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
        episodeRange: EpisodeRange = EpisodeRange.single(EpisodeSort(1)),
        subtitleKind: SubtitleKind? = null,
        extraFiles: MediaExtraFiles = MediaExtraFiles.EMPTY,
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

    companion object {
        val DEFAULT_PREFERENCE = MediaPreference.Empty.copy(
            fallbackResolutions = listOf(
                Resolution.R2160P,
                Resolution.R1440P,
                Resolution.R1080P,
                Resolution.R720P,
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
            subtitleKindFilters: MediaSelectorSubtitlePreferences = MediaSelectorSubtitlePreferences.AllNormal,
            subjectSequelNames: Set<String> = emptySet(),
            subjectInfo: SubjectInfo = SubjectInfo.Empty,
            episodeInfo: EpisodeInfo = EpisodeInfo.Empty,
        ) = createMediaSelectorContextFromEmpty(
            subjectCompleted = subjectCompleted,
            mediaSourcePrecedence = mediaSourcePrecedence,
            subtitleKindFilters = subtitleKindFilters,
            subjectSeriesInfo = SubjectSeriesInfo.Fallback.copy(sequelSubjectNames = subjectSequelNames),
            subjectInfo = subjectInfo,
            episodeInfo = episodeInfo,
        )

        @Suppress("SameParameterValue")
        fun createMediaSelectorContextFromEmpty(
            subjectCompleted: Boolean = false,
            mediaSourcePrecedence: List<String> = emptyList(),
            subtitleKindFilters: MediaSelectorSubtitlePreferences = MediaSelectorSubtitlePreferences.AllNormal,
            subjectSeriesInfo: SubjectSeriesInfo = SubjectSeriesInfo.Fallback,
            subjectInfo: SubjectInfo = SubjectInfo.Empty,
            episodeInfo: EpisodeInfo = EpisodeInfo.Empty,
        ) =
            MediaSelectorContext(
                subjectFinished = subjectCompleted,
                mediaSourcePrecedence = mediaSourcePrecedence,
                subtitlePreferences = subtitleKindFilters,
                subjectSeriesInfo = subjectSeriesInfo,
                subjectInfo = subjectInfo,
                episodeInfo = episodeInfo,
            )
    }
}

inline fun buildMediaSelectorTestSuite(block: MediaSelectorTestSuite.() -> Unit): MediaSelectorTestSuite {
    return MediaSelectorTestSuite().apply(block)
}

inline fun DynamicTestsBuilder.addMediaSelectorTest(
    name: String? = null,
    crossinline buildTest: MediaSelectorTestSuite.() -> Unit,
    crossinline runTest: suspend MediaSelectorTestSuite.() -> Unit,
) {
    val suite = buildMediaSelectorTestSuite(buildTest)
    add(name ?: suite.initApi.subjectName) {
        runBlocking {
            runTest(suite)
        }
    }
}
