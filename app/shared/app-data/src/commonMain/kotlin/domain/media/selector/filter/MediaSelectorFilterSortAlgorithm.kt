/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.selector.filter

import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.data.models.preference.MediaPreference.Companion.ANY_FILTER
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.domain.media.selector.MatchMetadata
import me.him188.ani.app.domain.media.selector.MaybeExcludedMedia
import me.him188.ani.app.domain.media.selector.MediaExclusionReason
import me.him188.ani.app.domain.media.selector.MediaSelectorContext
import me.him188.ani.app.domain.media.selector.SubtitleKindPreference
import me.him188.ani.app.domain.media.selector.UnsafeOriginalMediaAccess
import me.him188.ani.app.domain.mediasource.MediaListFilter
import me.him188.ani.app.domain.mediasource.MediaListFilterContext
import me.him188.ani.app.domain.mediasource.MediaListFilters
import me.him188.ani.app.domain.mediasource.StringMatcher
import me.him188.ani.app.domain.mediasource.codec.MediaSourceTier
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.isLocalCache
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.source.MediaSourceLocation
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.datasources.api.topic.contains
import me.him188.ani.datasources.api.topic.isSingleEpisode
import me.him188.ani.utils.coroutines.flows.sequenceOfEmptyString

/**
 * [me.him188.ani.app.domain.media.selector.MediaSelector] 的过滤和排序算法实现
 */
class MediaSelectorFilterSortAlgorithm {
    ///////////////////////////////////////////////////////////////////////////
    // 过滤
    ///////////////////////////////////////////////////////////////////////////

    /**
     * 过滤掉 [MediaSelectorSettings] 指定的内容. 例如过滤生肉, 对于完结番过滤掉单集
     */
    fun filterMediaList(
        list: List<Media>,
        preference: MediaPreference,
        settings: MediaSelectorSettings,
        context: MediaSelectorContext,
    ): List<MaybeExcludedMedia> {
        val subjectInfo = context.subjectInfo?.takeIf { info ->
            info != SubjectInfo.Empty && info.allNames.any { it.isNotBlank() }
        }
        val episodeInfo = context.episodeInfo.takeIf { it != EpisodeInfo.Empty }

        val mediaListFilterContext = if (subjectInfo != null && episodeInfo != null) {
            MediaListFilterContext(
                subjectNames = subjectInfo.allNames.toSet(),
                episodeSort = episodeInfo.sort,
                episodeEp = episodeInfo.ep,
                episodeName = episodeInfo.name,
            )
        } else null

        return list.map { media ->
            filterMedia(media, preference, settings, context, mediaListFilterContext)
        }
    }

    /**
     * 过滤 media，决定是否包含此它。返回的 [MaybeExcludedMedia] 可以是包含，也可以是排除。排除时会携带原因
     */
    private fun filterMedia(
        media: Media,
        preference: MediaPreference,
        settings: MediaSelectorSettings,
        context: MediaSelectorContext,
        mediaListFilterContext: MediaListFilterContext?
    ): MaybeExcludedMedia {
        val mediaSubjectName = media.properties.subjectName ?: media.originalTitle
        val contextSubjectNames = context.subjectInfo?.allNames.orEmpty().asSequence()

        // 由下面实现调用, 方便创建 MaybeExcludedMedia
        fun include(): MaybeExcludedMedia {
            return MaybeExcludedMedia.Included(
                media,
                metadata = calculateMatchMetadata(
                    contextSubjectNames,
                    mediaSubjectName,
                    media.episodeRange,
                    context.episodeInfo?.sort,
                    context.episodeInfo?.ep,
                ),
            )
        }

        fun exclude(reason: MediaExclusionReason): MaybeExcludedMedia = MaybeExcludedMedia.Excluded(media, reason)

        if (media.isLocalCache()) return include() // 本地缓存总是要显示

        if (settings.hideSingleEpisodeForCompleted
            && context.subjectFinished == true // 还未加载到剧集信息时, 先显示
            && media.kind == MediaSourceKind.BitTorrent
        ) {
            // 完结番隐藏单集资源
            val range = media.episodeRange
                ?: return exclude(MediaExclusionReason.SingleEpisodeForCompleteSubject(episodeRange = null))
            if (range.isSingleEpisode()) return exclude(
                MediaExclusionReason.SingleEpisodeForCompleteSubject(episodeRange = range),
            )
        }

        if (!preference.showWithoutSubtitle &&
            (media.properties.subtitleLanguageIds.isEmpty() && media.extraFiles.subtitles.isEmpty())
        ) {
            // 不显示无字幕的
            return exclude(MediaExclusionReason.MediaWithoutSubtitle)
        }

        val subtitleKind = media.properties.subtitleKind
        if (context.subtitlePreferences != null && subtitleKind != null) {
            if (context.subtitlePreferences[subtitleKind] == SubtitleKindPreference.HIDE) {
                return exclude(MediaExclusionReason.UnsupportedByPlatformPlayer)
            }
        }

        context.subjectSeriesInfo?.sequelSubjectNames?.forEach { name ->
            if (name.isNotBlank() && MediaListFilters.specialContains(media.originalTitle, name)) {
                return exclude(MediaExclusionReason.FromSequelSeason) // 是其他季度
            }
        }

        media.properties.subjectName?.let { subjectName ->
            context.subjectSeriesInfo?.seriesSubjectNamesWithoutSelf?.forEach { name ->
                if (MediaListFilters.specialEquals(subjectName, name)) {
                    // 精确匹配到了是其他季度的名称. 这里只有用精确匹配才安全. 
                    // 有些条目可能就只差距一个字母, 例如 "天降之物" 和 "天降之物f", 非常容易满足模糊匹配.
                    return exclude(MediaExclusionReason.FromSeriesSeason)
                }
            }
        }

        if (mediaListFilterContext != null) {
            val allow = when (media.kind) {
                MediaSourceKind.WEB -> {
                    with(MediaListFilters.ContainsSubjectName) {
                        mediaListFilterContext.applyOn(
                            object : MediaListFilter.Candidate {
                                override val originalTitle: String get() = media.originalTitle
                                override val subjectName: String get() = mediaSubjectName
                                override val episodeRange: EpisodeRange? get() = media.episodeRange
                                override fun toString(): String {
                                    return "Candidate(media=$media)"
                                }
                            },
                        )
                    }
                }

                MediaSourceKind.BitTorrent -> true
                MediaSourceKind.LocalCache -> true
            }

            if (!allow) {
                return exclude(MediaExclusionReason.SubjectNameMismatch)
            }
        }

        return include()
    }

    private fun calculateMatchMetadata(
        contextSubjectNames: Sequence<String>,
        mediaSubjectName: String,
        mediaEpisodeRange: EpisodeRange?,
        contextEpisodeSort: EpisodeSort?,
        contextEpisodeEp: EpisodeSort?
    ) = MatchMetadata(
        subjectMatchKind = if (
            contextSubjectNames.any {
                MediaListFilters.specialEquals(mediaSubjectName, it)
            }
        ) {
            MatchMetadata.SubjectMatchKind.EXACT
        } else {
            MatchMetadata.SubjectMatchKind.FUZZY
        },
        episodeMatchKind = if (mediaEpisodeRange != null) {
            when {
                contextEpisodeSort != null && contextEpisodeSort in mediaEpisodeRange -> {
                    MatchMetadata.EpisodeMatchKind.SORT
                }

                contextEpisodeEp != null && contextEpisodeEp in mediaEpisodeRange -> {
                    MatchMetadata.EpisodeMatchKind.EP
                }

                else -> {
                    MatchMetadata.EpisodeMatchKind.NONE
                }
            }
        } else {
            MatchMetadata.EpisodeMatchKind.NONE
        },
        similarity = (contextSubjectNames + sequenceOfEmptyString())
            .map { StringMatcher.calculateMatchRate(it, mediaSubjectName) }
            .max(),
    )

    ///////////////////////////////////////////////////////////////////////////
    // 排序
    ///////////////////////////////////////////////////////////////////////////

    /**
     * 将 [list] 排序
     */
    @OptIn(UnsafeOriginalMediaAccess::class)
    fun sortMediaList(
        list: List<MaybeExcludedMedia>,
        settings: MediaSelectorSettings,
        context: MediaSelectorContext,
    ): List<MaybeExcludedMedia> {
        return list.sortedWith(
            // stable sort, 保证相同的元素顺序不变
            compareBy<MaybeExcludedMedia> { 0 } // dummy, to use .then* syntax.
                // 排除的总是在最后
                .thenBy { maybe ->
                    when (maybe) {
                        is MaybeExcludedMedia.Included -> 0
                        is MaybeExcludedMedia.Excluded -> 1
                    }
                }
                // 将不能播放的放到后面
                .thenBy { maybe ->
                    val subtitleKind = maybe.original.properties.subtitleKind
                    if (context.subtitlePreferences != null && subtitleKind != null) {
                        if (context.subtitlePreferences[subtitleKind] != SubtitleKindPreference.NORMAL) {
                            return@thenBy 1
                        }
                    }
                    0
                }
                // 按符合用户选择类型排序. 缓存 > 用户偏好的 > 不偏好的, #1522
                .thenByDescending { maybe ->
                    when (maybe.original.kind) {
                        // Show cache on top
                        MediaSourceKind.LocalCache -> {
                            2
                        }

                        MediaSourceKind.WEB,
                        MediaSourceKind.BitTorrent -> {
                            if (settings.preferKind == null) {
                                0
                            } else {
                                if (maybe.original.kind == settings.preferKind) {
                                    1
                                } else {
                                    0
                                }
                            }
                        }
                    }
                }
                .then(
                    compareBy { it.original.costForDownload },
                )
                .thenBy { maybe ->
                    val tiers = context.mediaSourceTiers
                    tiers?.get(maybe.original.mediaSourceId)
                        ?: MediaSourceTier.MaximumValue // 还没加载出来, 先不排序
                }
                .thenByDescending {
                    it.original.publishedTime
                }
                .thenByDescending {
                    // 相似度越高, 排序越前
                    when (it) {
                        is MaybeExcludedMedia.Excluded -> 0
                        is MaybeExcludedMedia.Included -> it.similarity
                    }
                },
        )
    }

    private val Media.costForDownload
        get() = when (location) {
            MediaSourceLocation.Local -> 0
            MediaSourceLocation.Lan -> 1
            else -> 2
        }

    ///////////////////////////////////////////////////////////////////////////
    // Preference
    ///////////////////////////////////////////////////////////////////////////

    // 只是在本次显示中使用
    fun filterByPreference(
        mediaList: List<MaybeExcludedMedia>,
        mergedPreferences: MediaPreference,
    ): List<MaybeExcludedMedia> {
        infix fun <Pref : Any> Pref?.matches(prop: Pref): Boolean =
            this == null || this == prop || this == ANY_FILTER

        infix fun <Pref : Any> Pref?.matches(prop: List<Pref>): Boolean =
            this == null || this in prop || this == ANY_FILTER

        /**
         * 当 [it] 满足当前筛选条件时返回 `true`.
         */
        fun filterCandidate(it: Media): Boolean {
            if (it.isLocalCache()) {
                return true // always show local, so that [makeDefaultSelection] will select a local one
            }

            return mergedPreferences.alliance matches it.properties.alliance &&
                    mergedPreferences.resolution matches it.properties.resolution &&
                    mergedPreferences.subtitleLanguageId matches it.properties.subtitleLanguageIds &&
                    mergedPreferences.mediaSourceId matches it.mediaSourceId
        }

        return mediaList.filter {
            @OptIn(UnsafeOriginalMediaAccess::class)
            filterCandidate(it.original)
        }
    }
}
