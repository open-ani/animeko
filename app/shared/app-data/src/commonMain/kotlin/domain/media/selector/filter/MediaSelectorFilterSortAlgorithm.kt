/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
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
import me.him188.ani.app.domain.mediasource.asCandidate
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

    @Suppress("PrivatePropertyName")
    private val SEASON_TAILING = Regex("""第\s*(?<season>.+)\s*[部季]""")

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
        val mediaSubjectName = media.properties.subjectName
        val mediaSubjectNameOrOriginalTitle = mediaSubjectName ?: media.originalTitle
        val contextSubjectNames = context.subjectInfo?.allNames.orEmpty().asSequence()

        // 由下面实现调用, 方便创建 MaybeExcludedMedia
        fun include(): MaybeExcludedMedia {
            return MaybeExcludedMedia.Included(
                media,
                metadata = calculateMatchMetadata(
                    contextSubjectNames,
                    mediaSubjectNameOrOriginalTitle,
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

        if (mediaSubjectName != null) {
            // 数据源可以准确拿到条目名称, 我们采用 specialEquals

            // 首先检查数据源条目名是否与当前条目名称相同.
            // 只有在条目名称不相同的情况下, 才可以考虑续集, 因为续集可能只比前传多一个特殊字符, 能通过 specialEquals.
            if (contextSubjectNames.any { contextSubjectNames ->
                    MediaListFilters.specialEquals(mediaSubjectName, contextSubjectNames)
                }) {
                // contextSubjectNames 与条目名称相同, 肯定不能排除它
            } else {
                // 简化数据源结果的季度名称，例如从 "Re：从零开始的休息时间 第2季" 变成 "Re：从零开始的休息时间 2"
                // 条目名称可能是上述后者简化的形式, 但数据源的结果是前者完整版的形式
                // 额外判断一次简化的名称可以正确地排除掉类似这种情况的其他季度的资源.
                val mediaSubjectNameSeasonSimplified = mediaSubjectName.replace(SEASON_TAILING, $$"${season}")
                context.subjectSeriesInfo?.seriesSubjectNamesWithoutSelf?.forEach { name ->
                    if (MediaListFilters.specialEquals(mediaSubjectName, name) ||
                        MediaListFilters.specialEquals(mediaSubjectNameSeasonSimplified, name)
                    ) {
                        // 排除特殊字符后精确匹配到了是其他季度的名称. 
                        // 
                        // 注意: 这里也不可以改成用 edit-distance 模糊匹配, 因为
                        // 有些条目可能就只差距一个字母, 例如 "天降之物" 和 "天降之物f", 非常容易满足模糊匹配.

                        // 优化一下类型, 以通过一些现有 test. 在实际选择效果上这两个原因是没什么区别的, 都是排除.
                        return if (name in context.subjectSeriesInfo.sequelSubjectNames) {
                            exclude(MediaExclusionReason.FromSequelSeason)
                        } else {
                            exclude(MediaExclusionReason.FromSeriesSeason)
                        }
                    }
                }
                // seriesSubjectNamesWithoutSelf 包括了 sequel, 所以我们不需要再考虑 sequel 了. 
            }
        } else {
            // 不可以拿到条目名称, 只做保守排除

            // 如果续集名称与当前条目名称相同, 说明是续集
            context.subjectSeriesInfo?.sequelSubjectNames?.forEach { sequelName ->
                if (sequelName.isNotBlank() &&
                    // 注意: 这里不可以使用 specialContains, 因为续集可能比前传只多一个特殊字符. See #1912
                    mediaSubjectNameOrOriginalTitle.contains(sequelName, ignoreCase = true)
                ) {
                    // 是其他季度
                    return exclude(MediaExclusionReason.FromSeriesSeason)
                }
            }
        }

        if (mediaListFilterContext != null) {
            val allow = when (media.kind) {
                MediaSourceKind.WEB -> {
                    with(MediaListFilters.ContainsSubjectName) {
                        val baseContains = mediaListFilterContext.applyOn(
                            object : MediaListFilter.Candidate by media.asCandidate() {
                                override val subjectName: String get() = mediaSubjectNameOrOriginalTitle
                            },
                        )
                        if (media.episodeRange?.contains(EpisodeSort("OVA")) == true) {
                            // 如果数据源搜到了 OVA 剧集, 那就额外匹配一次 subject name 加上 "OVA" 的情况.
                            val propName = media.properties.subjectName ?: return@with false
                            val ovaContains = mediaListFilterContext.applyOn(
                                object : MediaListFilter.Candidate by media.asCandidate() {
                                    override val subjectName: String get() = "$propName OVA"
                                },
                            )
                            baseContains || ovaContains
                        } else {
                            baseContains
                        }
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
