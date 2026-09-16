/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import me.him188.ani.app.ui.foundation.AsyncImage

/**
 * 剧集卡片的剧照背景: 剧照裁切铺满, 上面盖一层自上而下加深的黑色渐变, 让白色文字在任何画面上都可读.
 *
 * 由各处剧集卡片 (详情页网格、播放页横向卡片与选集面板) 共用, 放在卡片 `Box` 的最底层并 `matchParentSize()`.
 * 图片尚未加载或加载失败时只剩渐变遮罩盖在卡片底色上, 白色文字依然可读, 卡片尺寸与外观保持稳定.
 *
 * @param dimmed 已看 (DONE/DROPPED) 时压暗剧照, 对应无图卡片的 60% 文字变暗规则.
 * @param highlighted 播放中时叠加一层 primary 蒙层.
 */
@Composable
fun EpisodeStillBackground(
    imageUrl: String,
    dimmed: Boolean,
    highlighted: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier.testTag(EPISODE_STILL_TAG)) {
        AsyncImage(
            imageUrl,
            contentDescription = null,
            Modifier.matchParentSize(),
            contentScale = ContentScale.Crop,
            alpha = if (dimmed) 0.6f else 1f,
        )
        if (highlighted) {
            Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)))
        }
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.35f),
                    1f to Color.Black.copy(alpha = 0.7f),
                ),
            ),
        )
    }
}

/**
 * 有剧照的剧集卡片上的文字颜色. 剧照上永远用白色系, 不随主题变化.
 */
object EpisodeStillDefaults {
    /** 集号等主要文字. */
    val contentColor: Color = Color.White

    /** 集名等次要文字. */
    val secondaryContentColor: Color = Color.White.copy(alpha = 0.85f)

    /** 已看剧集的全部文字. */
    val watchedContentColor: Color = Color.White.copy(alpha = 0.7f)
}

/** [EpisodeStillBackground] 根节点的 test tag, 用于断言卡片是否显示了剧照. */
const val EPISODE_STILL_TAG: String = "episode_still"
