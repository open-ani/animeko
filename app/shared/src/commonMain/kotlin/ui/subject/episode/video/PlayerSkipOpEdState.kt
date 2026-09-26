/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.video

import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import me.him188.ani.app.data.models.preference.VideoScaffoldConfig
import me.him188.ani.app.domain.media.player.prefetch.MediaPrefetchRequest
import me.him188.ani.app.domain.media.player.prefetch.MediaTimeRange
import org.openani.mediamp.metadata.Chapter

internal val DEFAULT_OP_ED_SKIP_DURATION = VideoScaffoldConfig.Default.opEdSkipDuration

@Stable
class PlayerSkipOpEdState(
    private val chapters: State<List<Chapter>>,
    private val onSkip: (targetMillis: Long) -> Unit,
) {
    private var currentChapter: CurrentChapter? by mutableStateOf(null)
    private var opEdChapters = emptyList<CurrentChapter>()

    val skipped: Boolean by derivedStateOf {
        currentChapter?.skipped ?: false
    }

    val pendingChapter: Chapter? by derivedStateOf { currentChapter?.takeUnless { it.skipped }?.chapter }

    val showSkipTips: Boolean by derivedStateOf {
        currentChapter != null && !skipped
    }

    /**
     * 为即将被自动跳过的 OP/ED 准备的预缓存请求; 当前没有需要预缓存的章节时为 `null`.
     *
     * 从章节开始前 [PREFETCH_LEAD_MILLIS] 起到章节结束为止有效. 请求缓存章节结束后的 [PREFETCH_DURATION_MILLIS],
     * 这样跳过后播放器能立即从已缓存的数据续播; 前提是章节开头之前的内容已经缓冲好, 见 [MediaPrefetchRequest]. 由 [update] 维护.
     */
    var prefetchRequest: MediaPrefetchRequest? by mutableStateOf(null)
        private set

    fun cancelSkipOpEd() {
        currentChapter?.skipped = true
    }


    /**
     * 每秒调用一次update
     * 根据[currentPos]感知[currentPos]到5秒后这个区间是否会有章节开头，
     * 根据当前秒的位置显示/隐藏tips，
     * 并且如果[currentPos]在章节开头的位置，根据[skipped]跳过该章节
     */
    fun update(currentPos: Long) {
        // 同一章节保留跳过状态；离开候选列表的章节不保留状态。
        opEdChapters = chapters.value.map { chapter ->
            opEdChapters.find {
                it.chapter.name == chapter.name &&
                        it.chapter.offsetMillis == chapter.offsetMillis &&
                        it.chapter.durationMillis == chapter.durationMillis
            } ?: CurrentChapter(chapter, false)
        }
        prefetchRequest = computePrefetchRequest(currentPos)
        // 在显示跳过提示范围
        val candidate = opEdChapters.find {
            it.chapter.offsetMillis in currentPos - 1000..currentPos + 5000
        }
        if (candidate == null) {
            currentChapter?.skipped = true
        }
        currentChapter = candidate
        // 在跳过 OP/ED 范围
        currentChapter?.takeIf { it.chapter.offsetMillis in currentPos - 1000..currentPos }?.run {
            if (skipped) return@run
            onSkip(chapter.offsetMillis + chapter.durationMillis)
            skipped = true
            currentChapter = null
        }
    }

    private fun computePrefetchRequest(currentPos: Long): MediaPrefetchRequest? {
        val upcoming = opEdChapters.firstOrNull {
            val start = it.chapter.offsetMillis
            val end = start + it.chapter.durationMillis
            // 已经跳过过, 或用户取消了跳过的章节不会再自动跳过, 不必为它预缓存
            !it.skipped && currentPos >= start - PREFETCH_LEAD_MILLIS && currentPos < end
        } ?: return null
        val start = upcoming.chapter.offsetMillis
        val end = start + upcoming.chapter.durationMillis
        return MediaPrefetchRequest(
            range = MediaTimeRange(end, end + PREFETCH_DURATION_MILLIS),
            requireBufferedUntilMillis = start,
        )
    }

    companion object {
        /**
         * 最早提前多久开始预缓存跳过目标. 真正开始还要等章节开头之前的内容缓冲好 ([MediaPrefetchRequest.requireBufferedUntilMillis]),
         * 因此这里不必卡得很紧: 网络快时提前 30 秒开始, 网络慢时自动推迟甚至不做.
         */
        const val PREFETCH_LEAD_MILLIS: Long = 30_000

        /** 预缓存跳过目标之后多长的内容. */
        const val PREFETCH_DURATION_MILLIS: Long = 30_000
    }
}

@Stable
class CurrentChapter(val chapter: Chapter, skipped: Boolean) {
    var skipped by mutableStateOf(skipped)
}
