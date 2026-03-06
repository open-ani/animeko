/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.cache.components

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.DelicateCoroutinesApi
import me.him188.ani.app.domain.media.cache.engine.MediaCacheEngineKey
import me.him188.ani.app.domain.media.cache.engine.MediaStats
import me.him188.ani.app.tools.Progress
import me.him188.ani.app.tools.toPercentageOrZero
import me.him188.ani.app.tools.toProgress
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.topic.FileSize
import me.him188.ani.datasources.api.topic.FileSize.Companion.megaBytes
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.platform.annotations.TestOnly
import me.him188.ani.utils.platform.format1f
import kotlin.random.Random

@Immutable
class CacheEpisodeState(
    val groupId: String,
    val subjectId: Int,
    val episodeId: Int,
    val cacheId: String,
    val sort: EpisodeSort,
    val subjectName: String,
    val displayName: String,
    val creationTime: Long?,
    val screenShots: List<String>, // url
    val stats: Stats,
    val state: CacheEpisodePaused,
    val engineKey: MediaCacheEngineKey?,
    val subjectCollectionType: UnifiedCollectionType?,
    val playability: Playability = Playability.PLAYABLE,
) {
    enum class Playability {
        PLAYABLE,
        INVALID_SUBJECT_EPISODE_ID,
        STREAMING_NOT_SUPPORTED,
    }

    @Immutable
    data class Stats(
        val downloadSpeed: FileSize,
        val progress: Progress,
        val totalSize: FileSize,
    ) {
        companion object {
            val Unspecified =
                Stats(FileSize.Companion.Unspecified, Progress.Companion.Unspecified, FileSize.Companion.Unspecified)
        }
    }

    val listItemKey = "$subjectId-$groupId-$episodeId-$cacheId"

    val progress get() = stats.progress

    val isPaused get() = state == CacheEpisodePaused.PAUSED
    val isFinished get() = stats.progress.isFinished

    val totalSize: FileSize get() = stats.totalSize

    val sizeText: String? = run {
        // 原本打算展示 "888.88 MB / 888.88 MB" 的格式, 感觉比较啰嗦, 还是省略了
        // 这个函数有正确的 testing, 应该切换就能用
//        calculateSizeText(totalSize.value, progress.value)

        val value = this.totalSize
        return@run if (value == FileSize.Companion.Unspecified) {
            null
        } else {
            "$value"
        }
    }

    val progressText: String? = run {
        val value = stats.progress
        if (value.isUnspecified || this.isFinished) {
            null
        } else {
            "${String.Companion.format1f(value.toPercentageOrZero())}%"
        }
    }

    val speedText = run {
        val progressValue = stats.progress
        val speed = stats.downloadSpeed
        if (!progressValue.isUnspecified) {
            val showSpeed = !progressValue.isFinished && speed != FileSize.Companion.Unspecified
            if (showSpeed) {
                return@run "${speed}/s"
            }
        }
        null
    }

    val isProgressUnspecified get() = stats.progress.isUnspecified

    val status: CacheStatusFilter
        get() = if (isFinished) CacheStatusFilter.Finished else CacheStatusFilter.Downloading

    companion object {
        fun calculateSizeText(
            totalSize: FileSize,
            progress: Float?,
        ): String? {
            if (progress == null && totalSize == FileSize.Companion.Unspecified) {
                return null
            }
            return when {
                progress == null -> {
                    if (totalSize != FileSize.Companion.Unspecified) {
                        "$totalSize"
                    } else null
                }

                totalSize == FileSize.Companion.Unspecified -> null

                else -> {
                    "${totalSize * progress} / $totalSize"
                }
            }
        }
    }
}

@TestOnly
fun createTestMediaStats(): MediaStats = MediaStats.Unspecified

@TestOnly
val TestCacheEpisodes
    get() = listOf(
        createTestCacheEpisode(1, "孤独摇滚", "翻转孤独", 1),
        createTestCacheEpisode(2, "孤独摇滚", "明天见", 1, initialState = CacheEpisodePaused.PAUSED),
        createTestCacheEpisode(3, "孤独摇滚", "火速增员", 1, progress = 1f.toProgress()),
    )

@OptIn(DelicateCoroutinesApi::class)
@Suppress("SameParameterValue")
@TestOnly
fun createTestCacheEpisode(
    sort: Int,
    subjectName: String = "孤独摇滚",
    displayName: String = "第 $sort 话",
    subjectId: Int = 1,
    episodeId: Int = sort,
    initialState: CacheEpisodePaused = when (sort % 2) {
        0 -> CacheEpisodePaused.PAUSED
        else -> CacheEpisodePaused.IN_PROGRESS
    },
    downloadSpeed: FileSize = 233.megaBytes,
    progress: Progress = 0.3f.toProgress(),
    totalSize: FileSize = 888.megaBytes,
): CacheEpisodeState {
    val cacheId = Random.nextInt(10000, 99999).toString()
    return CacheEpisodeState(
        groupId = subjectId.toString(),
        subjectId = subjectId,
        episodeId = episodeId,
        cacheId = cacheId,
        sort = EpisodeSort(sort),
        subjectName = subjectName,
        displayName = displayName,
        creationTime = 100,
        screenShots = emptyList(),
        stats = CacheEpisodeState.Stats(
            downloadSpeed = downloadSpeed,
            progress = progress,
            totalSize = totalSize,
        ),
        state = initialState,
        engineKey = MediaCacheEngineKey.Anitorrent,
        subjectCollectionType = UnifiedCollectionType.DOING,
    )
}