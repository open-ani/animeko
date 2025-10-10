/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.cache.components

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import me.him188.ani.app.domain.media.TestMediaList
import me.him188.ani.datasources.api.topic.FileSize
import me.him188.ani.datasources.api.topic.FileSize.Companion.megaBytes
import me.him188.ani.utils.platform.annotations.TestOnly

/**
 * 表示一个合并的缓存组.
 * @see CacheGroupCommonInfo
 */
@Stable
class CacheGroupState(
    val id: String,
    val commonInfo: CacheGroupCommonInfo?, // null means loading
    episodes: List<CacheEpisodeState>,
    stats: Stats,
) {
    @Immutable
    data class Stats(
        val downloadSpeed: FileSize,
        val downloadedSize: FileSize,
        /**
         * 上传速度, 每秒. 对于不支持上传的缓存, 该值为 [FileSize.Companion.Zero].
         *
         * - 若 emit [FileSize.Companion.Unspecified], 表示上传速度未知. 这只会在该缓存正在上传, 但无法知道具体速度时出现.
         * - 若 emit [FileSize.Companion.Zero], 表示上传速度真的是零.
         */
        val uploadSpeed: FileSize,
    )

    val episodes = episodes.sortedBy { it.sort }
    val cacheId = episodes.firstOrNull()?.cacheId

    val latestCreationTime = episodes.asSequence().mapNotNull { it.creationTime }.maxOfOrNull { it }

    private val allEpisodesFinished = run {
        episodes.all { it.isFinished }
    }
    val downloadSpeedText = computeSpeedText(
        speed = if (allEpisodesFinished) FileSize.Companion.Unspecified else stats.downloadSpeed,
        size = stats.downloadedSize,
    )

    val uploadSpeedText = computeSpeedText(
        speed = stats.uploadSpeed,
        size = FileSize.Companion.Unspecified,
    )

    val subjectId = commonInfo?.subjectId?.takeIf { it != 0 }

    /**
     * null means loading
     */
    val cardTitle get() = this.commonInfo?.subjectDisplayName

    companion object {
        fun computeSpeedText(speed: FileSize, size: FileSize): String {
            return when {
                size == FileSize.Companion.Unspecified && speed == FileSize.Companion.Unspecified -> ""
                size == FileSize.Companion.Unspecified -> "$speed/s"
                speed == FileSize.Companion.Unspecified -> "$size"
                else -> "$size ($speed/s)"
            }
        }
    }
}


@TestOnly
internal val TestCacheGroupSates = listOf(
    CacheGroupState(
        id = TestMediaList[0].mediaId,
        commonInfo = CacheGroupCommonInfo(
            subjectId = 1,
            "孤独摇滚",
            mediaSourceId = "mikan-mikanime-tv",
            allianceName = "某某字幕组",
        ),
        episodes = TestCacheEpisodes,
        stats = CacheGroupState.Stats(
            downloadSpeed = 233.megaBytes,
            downloadedSize = 233.megaBytes,
            uploadSpeed = 233.megaBytes,
        ),
    ),
)
