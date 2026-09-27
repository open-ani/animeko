/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.mediaselect

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.serialization.Serializable
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.data.models.episode.displayName

/**
 * 选择器的展示模式. 会话内保持 (放在 EpisodeViewModel), 不持久化, 不改 `MediaSelectorSettings.preferKind`.
 */
@Serializable
enum class MediaSelectorMode {
    AUTO,
    MANUAL,
    BT,
}

/**
 * 「正在观看」提示. [sort] 是已格式化的集号 (如 "25"), [name] 是剧集名, 可为空串 (只显示集号).
 */
@Immutable
data class WatchingEpisode(
    val sort: String,
    val name: String,
)

/**
 * `EpisodeSort.Normal.toString()` 已补零 ("01").
 */
fun EpisodeInfo.toWatchingEpisode(): WatchingEpisode = WatchingEpisode(sort.toString(), displayName)

object MediaSelectorLayoutDefaults {
    /**
     * 容器宽度达到此值: BT 页用表格, 手动查找用双栏.
     */
    val WideContentMinWidth: Dp = 720.dp

    /**
     * 容器可用高度低于此值时铺满.
     */
    val CompactDialogMaxHeight: Dp = 480.dp

    val DialogMaxWidth: Dp = 960.dp
    val DialogMaxHeight: Dp = 700.dp
    val DialogMargin: Dp = 64.dp
}
