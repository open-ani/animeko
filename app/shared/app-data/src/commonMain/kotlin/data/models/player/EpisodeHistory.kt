/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.player

import kotlinx.serialization.Serializable

/**
 * 播放记录
 */
@Serializable
data class EpisodeHistory(
    val episodeId: Int,
    val positionMillis: Long,
    val subjectId: Int? = null,
    val episodeSort: Float? = null,
    val subjectName: String? = null,
    val subjectImageUrl: String? = null,
    val episodeName: String? = null,
    val durationMillis: Long? = null,
    val updatedAtMillis: Long = 0,
    val deletedAtMillis: Long? = null,
    val isDirty: Boolean = true,
) {
    val isDeleted: Boolean get() = deletedAtMillis != null

    val versionMillis: Long get() = maxOf(updatedAtMillis, deletedAtMillis ?: 0L)

    /**
     * 是否已看完: 位置距离结尾不足 [FINISHED_THRESHOLD_MILLIS]. 时长未知时无法判断, 视为未看完.
     *
     * 看完的记录仍然保留 (供历史列表和剧集进度显示), 但下次播放不再恢复到这个位置, 否则会立刻播放结束并触发自动连播.
     */
    val isFinished: Boolean
        get() {
            val duration = durationMillis ?: return false
            return duration > 0L && duration - positionMillis < FINISHED_THRESHOLD_MILLIS
        }

    companion object {
        const val FINISHED_THRESHOLD_MILLIS: Long = 5_000
    }
}

/**
 * 上次播放进度, `0..1`. 没有时长信息 (或时长为 0) 时为 `null`, 表示无法换算成比例.
 */
val EpisodeHistory.playProgress: Float?
    get() = durationMillis?.takeIf { it > 0L }?.let { (positionMillis.toFloat() / it).coerceIn(0f, 1f) }

/**
 * 按剧集 id 索引的上次播放进度, 只包含能换算出比例且大于 0 的记录; 位置为 0 的记录视为没有播放过.
 */
fun List<EpisodeHistory>.playProgressByEpisodeId(): Map<Int, Float> = buildMap {
    for (history in this@playProgressByEpisodeId) {
        val progress = history.playProgress ?: continue
        if (progress > 0f) put(history.episodeId, progress)
    }
}
