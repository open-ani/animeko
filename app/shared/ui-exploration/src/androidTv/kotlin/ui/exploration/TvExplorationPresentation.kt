/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */
package me.him188.ani.leanback.ui.exploration

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.him188.ani.app.data.models.subject.SubjectCollectionInfo
import me.him188.ani.datasources.api.EpisodeType
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.leanback.ui.subject.components.TvSubjectDetailsDefaults

internal enum class TvExplorationArea { Featured, ContinueWatching, Recommendations }

/** Only the hero's foreground slides; zero direction is a first-row crossfade. */
internal fun explorationHeroContentTransform(direction: Int): ContentTransform = ContentTransform(
    targetContentEnter = fadeIn(tween(TvExplorationDefaults.BackdropFadeMillis)) +
            slideInHorizontally(tween(TvExplorationDefaults.BackdropFadeMillis)) { direction * it / 8 },
    initialContentExit = fadeOut(tween(TvExplorationDefaults.BackdropFadeMillis)) +
            slideOutHorizontally(tween(TvExplorationDefaults.BackdropFadeMillis)) { -direction * it / 8 },
    sizeTransform = null,
)

/** The carousel keeps a business identity when a new trending page arrives. */
internal fun nextFeaturedSubjectId(ids: List<Int>, currentId: Int?, direction: Int): Int? {
    if (ids.isEmpty()) return null
    val current = ids.indexOf(currentId).coerceAtLeast(0)
    return ids[(current + direction.mod(ids.size)).mod(ids.size)]
}

internal data class TvWatchedEpisodeProgress(val watched: Int, val total: Int) {
    val fraction: Float get() = if (total > 0) (watched.toFloat() / total).coerceIn(0f, 1f) else 0f
}

/** Count completed main episodes, rather than treating the last episode number as a count. */
internal fun watchedEpisodeProgress(collection: SubjectCollectionInfo): TvWatchedEpisodeProgress {
    val episodes = collection.episodes.filter { it.episodeInfo.type == EpisodeType.MainStory }
    return TvWatchedEpisodeProgress(
        watched = episodes.count { it.collectionType == UnifiedCollectionType.DONE },
        total = maxOf(collection.airingInfo.mainEpisodeCount, episodes.size),
    )
}

internal object TvExplorationDefaults {
    val Background = TvSubjectDetailsDefaults.Background
    val Content = TvSubjectDetailsDefaults.Content
    val SecondaryContent = TvSubjectDetailsDefaults.SecondaryContent

    // The main shell already reserves 48dp for its collapsed navigation rail.
    val StartPadding = 24.dp
    val EndPadding = 32.dp
    val HeroTopPadding = 48.dp
    val HeroCompactTopPadding = 28.dp
    val CompactTitleSize = 30.sp
    val RowHeaderHeight = 38.dp
    val RowAnchorInset = 76.dp
    val RowGap = 20.dp
    val ContinueCardWidth = TvSubjectDetailsDefaults.RelatedCardWidth
    val CardSpacing = 18.dp
    val FadingEdgeHeight = 24.dp
    const val HeroExpandedFraction = .66f
    const val HeroCollapsedFraction = .52f
    const val HeroTransitionMillis = 350
    const val BackdropFadeMillis = 400
    const val BackdropDebounceMillis = 180L
    const val CarouselMaxItems = 10
    const val CarouselAutoAdvanceMillis = 6000
}
