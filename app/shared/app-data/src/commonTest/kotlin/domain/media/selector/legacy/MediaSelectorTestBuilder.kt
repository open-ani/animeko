/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:Suppress("DEPRECATION")

package me.him188.ani.app.domain.media.selector.legacy

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestScope
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.data.models.subject.SubjectSeriesInfo
import me.him188.ani.app.domain.media.createTestDefaultMedia
import me.him188.ani.app.domain.media.createTestMediaProperties
import me.him188.ani.app.domain.media.fetch.MediaFetchSession
import me.him188.ani.app.domain.media.fetch.MediaFetcher
import me.him188.ani.app.domain.media.fetch.MediaFetcherConfig
import me.him188.ani.app.domain.media.fetch.MediaSourceMediaFetcher
import me.him188.ani.app.domain.media.selector.DefaultMediaSelector
import me.him188.ani.app.domain.media.selector.MediaSelectorContext
import me.him188.ani.app.domain.media.selector.MediaSelectorSourceTiers
import me.him188.ani.app.domain.media.selector.MediaSelectorSubtitlePreferences
import me.him188.ani.app.domain.mediasource.instance.MediaSourceInstance
import me.him188.ani.app.domain.mediasource.instance.createTestMediaSourceInstance
import me.him188.ani.datasources.api.DefaultMedia
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.SubtitleKind
import me.him188.ani.datasources.api.paging.SinglePagePagedSource
import me.him188.ani.datasources.api.source.MatchKind
import me.him188.ani.datasources.api.source.MediaFetchRequest
import me.him188.ani.datasources.api.source.MediaMatch
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.source.MediaSourceLocation
import me.him188.ani.datasources.api.source.TestHttpMediaSource
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.datasources.api.topic.FileSize.Companion.megaBytes
import me.him188.ani.datasources.api.topic.Resolution
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.datasources.api.topic.SubtitleLanguage
import kotlin.coroutines.ContinuationInterceptor

/**
 * @suppress 已弃用, 新的 test 使用 [me.him188.ani.app.domain.media.selector.testFramework.TestMediaFetchSessionBuilder].
 * @see me.him188.ani.app.domain.media.selector.MediaSelectorAutoSelect
 */
@Deprecated(MediaSelectorDeprecationMessage)
class MediaSelectorTestBuilder(
    private val testScope: TestScope,
) {
    val savedUserPreference = MutableStateFlow(DEFAULT_PREFERENCE)
    val savedDefaultPreference = MutableStateFlow(DEFAULT_PREFERENCE)
    val mediaSelectorSettings = MutableStateFlow(MediaSelectorSettings.Companion.Default)

    val mediaSources = mutableListOf<MediaSourceInstance>()

    /**
     * 添加一个 [me.him188.ani.app.domain.media.fetch.MediaSourceMediaFetcher]. 它会一直等待, 直到 `deferred.complete(list)`, 并返回 list 作为查询结果.
     */
    fun delayedMediaSource(
        mediaSourceId: String,
        kind: MediaSourceKind = MediaSourceKind.WEB,
        enabled: Boolean = true,
    ): CompletableDeferred<List<Media>> {
        val deferred = CompletableDeferred<List<Media>>()
        mediaSources.add(
            createTestMediaSourceInstance(
                TestHttpMediaSource(
                    mediaSourceId = mediaSourceId,
                    kind = kind,
                    fetch = {
                        SinglePagePagedSource {
                            deferred.await().map { MediaMatch(it, MatchKind.EXACT) }.asFlow()
                        }
                    },
                ),
                isEnabled = enabled,
            ),
        )
        return deferred
    }

    ///////////////////////////////////////////////////////////////////////////
    // Outputs
    ///////////////////////////////////////////////////////////////////////////

    fun createMedia(
        mediaSourceId: String,
        kind: MediaSourceKind = MediaSourceKind.WEB,
        alliance: String = "XX字幕组",
    ): DefaultMedia = createTestDefaultMedia(
        mediaId = "$mediaSourceId.1",
        mediaSourceId = mediaSourceId,
        originalTitle = "[XX字幕组] 孤独摇滚 ABC ABC ABC ABC ABC ABC ABC ABC ABC ABC",
        download = ResourceLocation.MagnetLink("magnet:?xt=urn:btih:1"),
        originalUrl = "https://example.com/1",
        publishedTime = 1,
        episodeRange = EpisodeRange.Companion.single(EpisodeSort(1)),
        properties = createTestMediaProperties(
            subtitleLanguageIds = listOf(
                SubtitleLanguage.ChineseSimplified,
                SubtitleLanguage.ChineseTraditional,
            ).map { it.id },
            resolution = "1080P",
            alliance = alliance,
            size = 122.megaBytes,
            subtitleKind = SubtitleKind.CLOSED,
        ),
        kind = kind,
        location = MediaSourceLocation.Online,
    )

    fun createMediaFetcher() = MediaSourceMediaFetcher(
        configProvider = { MediaFetcherConfig.Companion.Default },
        mediaSources = mediaSources,
        flowContext = testScope.coroutineContext[ContinuationInterceptor.Key]!!,
    )

    fun createMediaFetchSession(fetcher: MediaFetcher) = fetcher.newSession(
        MediaFetchRequest(
            subjectId = "1",
            episodeId = "1",
            subjectNames = listOf("孤独摇滚"),
            episodeSort = EpisodeSort(1),
            episodeName = "test",
        ),
    )

    fun createMediaSelector(fetchSession: MediaFetchSession) = DefaultMediaSelector(
        mediaSelectorContextNotCached = fetchSession.request.map { createMediaSelectorContext(it) },
        mediaListNotCached = fetchSession.cumulativeResults,
        savedUserPreference = savedUserPreference,
        savedDefaultPreference = savedDefaultPreference,
        enableCaching = false,
        mediaSelectorSettings = mediaSelectorSettings,
        flowCoroutineContext = testScope.coroutineContext[ContinuationInterceptor.Key]!!,
    )

    private fun createMediaSelectorContext(fetchRequest: MediaFetchRequest): MediaSelectorContext {
        return MediaSelectorContext(
            subjectFinished = false,
            mediaSourcePrecedence = emptyList(),
            subtitlePreferences = MediaSelectorSubtitlePreferences.AllNormal,
            subjectSeriesInfo = SubjectSeriesInfo.Fallback,
            subjectInfo = SubjectInfo.Empty.copy(
                subjectId = fetchRequest.subjectId.toInt(),
                name = fetchRequest.subjectNames.firstOrNull() ?: "",
                nameCn = fetchRequest.subjectNameCN ?: "",
            ),
            episodeInfo = EpisodeInfo.Empty.copy(
                episodeId = fetchRequest.episodeId.toInt(),
                name = fetchRequest.episodeName,
                sort = fetchRequest.episodeSort,
                ep = fetchRequest.episodeEp,

                ),
            mediaSourceTiers = MediaSelectorSourceTiers.Empty,
        )
    }

    fun create(): Triple<MediaSourceMediaFetcher, MediaFetchSession, DefaultMediaSelector> {
        val fetcher = createMediaFetcher()
        val session = createMediaFetchSession(fetcher)
        return Triple(
            fetcher,
            session,
            createMediaSelector(session),
        )
    }

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
    }
}