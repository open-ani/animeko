/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.selector

import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.domain.mediasource.MediaListFilters
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.utils.platform.annotations.Range
import me.him188.ani.utils.platform.annotations.TestOnly

/**
 * 表示一个可能被排除的资源, 包含其被排除的原因.
 *
 * @see MediaSelector.filteredCandidates
 */
sealed class MaybeExcludedMedia {
    /**
     * 如果没有被排除, 则返回原始的 [Media], 相当于 [original].
     * 如果被排除了, 即 [exclusionReason] 不为 `null`, 则返回 `null`.
     *
     * 这个属性也可以方便在 `when` 表达式中进行安全操作.
     * 对 [MaybeExcludedMedia] 进行 `when` `is` 时, 对于 [Included] 分支, [result] 将变为非可空类型 `Media`.
     */
    abstract val result: Media?

    /**
     * 过滤之前的 Media. 应当尽量避免使用此属性, 因为它很可能在你 refactor 代码后忽略检查.
     * 如果你真的需要使用这个 (例如在 UI 中显示原始资源), 请 opt-in [UnsafeOriginalMediaAccess].
     */
    @UnsafeOriginalMediaAccess
    abstract val original: Media

    /**
     * 为什么被排除. 为 `null` 表示没有被排除
     */
    abstract val exclusionReason: MediaExclusionReason?

    /**
     * 资源需要包含在结果中. 即未被排除.
     */
    data class Included(
        override val result: Media,
        val metadata: MatchMetadata,
    ) : MaybeExcludedMedia() {
        @UnsafeOriginalMediaAccess
        override val original: Media get() = result
        override val exclusionReason: Nothing? get() = null

        val similarity: @Range(from = 0L, to = 100L) Int get() = metadata.similarity
    }

    /**
     * 资源不能包含在结果中. 即被排除.
     */
    @OptIn(UnsafeOriginalMediaAccess::class)
    data class Excluded(
        override val original: Media,
        override val exclusionReason: MediaExclusionReason
    ) : MaybeExcludedMedia() {
        override val result: Nothing? get() = null
    }
}

/**
 * @see MaybeExcludedMedia.original
 */
@RequiresOptIn
annotation class UnsafeOriginalMediaAccess


sealed class MediaExclusionReason {
    /**
     * 完结番隐藏单集资源
     * @see MediaSelectorContext.subjectFinished
     * @see me.him188.ani.app.data.models.preference.MediaSelectorSettings.hideSingleEpisodeForCompleted
     */
    data class SingleEpisodeForCompleteSubject(val episodeRange: EpisodeRange?) : MediaExclusionReason()

    /**
     * 隐藏生肉资源
     * @see me.him188.ani.app.data.models.preference.MediaPreference.showWithoutSubtitle
     */
    data object MediaWithoutSubtitle : MediaExclusionReason()

    /**
     * 当前平台的播放器不支持播放该资源
     * @see
     */
    data object UnsupportedByPlatformPlayer : MediaExclusionReason()

    /**
     * 该资源是续集季度的资源, 而不是当前季度的
     * // no settings
     */
    data object FromSequelSeason : MediaExclusionReason()

    /**
     * 该资源是其他季度的资源, 而不是当前季度的
     */
    data object FromSeriesSeason : MediaExclusionReason()

    /**
     * 资源标题不匹配 (不包含 [SubjectInfo.allNames])
     */
    data object SubjectNameMismatch : MediaExclusionReason()
}

data class MatchMetadata(
    val subjectMatchKind: SubjectMatchKind,
    /**
     * media 识别到了精准的 sort, 并且完全匹配正在观看的 [sort][EpisodeInfo.sort].
     * 注意, 这不包含匹配 [EpisodeInfo.ep], 因为我们无法在第二季时根据 ep 区分是否正确.
     */
    val episodeMatchKind: EpisodeMatchKind,
    val similarity: @Range(from = 0L, to = 100L) Int,
) {
    enum class SubjectMatchKind {
        /**
         * 没有精确匹配.
         */
        FUZZY,

        /**
         * media 识别到了精准的条目名称, 并且完全匹配正在观看的条目名称.
         * @see MediaListFilters.specialEquals
         */
        EXACT,
    }

    enum class EpisodeMatchKind { // Element order has semantics. Check usage before changing.
        NONE,

        /**
         * media 识别到了精准的 sort 或 ep, 并且完全匹配正在观看的 [ep][EpisodeInfo.ep].
         * 注意, 如果能匹配正在观看的 [sort][EpisodeInfo.sort], 则会是 [SORT].
         */
        EP,

        /**
         * media 识别到了精准的 sort 或 ep, 并且完全匹配正在观看的 [sort][EpisodeInfo.sort].
         * 注意, 这不包含匹配 [EpisodeInfo.ep], 因为我们无法在第二季时根据 ep 区分是否正确.
         */
        SORT,
    }
}

@TestOnly
val TestMatchMetadata
    get() = MatchMetadata(
        MatchMetadata.SubjectMatchKind.EXACT,
        MatchMetadata.EpisodeMatchKind.EP,
        90,
    )

