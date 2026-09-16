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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.app.ui.foundation.theme.LocalDarkOnSurface
import me.him188.ani.app.ui.foundation.theme.appColorScheme

/**
 * 与首页轮播 (`CarouselItem`) 相同的遮罩: 上半部分完全透明, 只在底部文字所在区域渐变到深色.
 */
@Stable
private val episodeStillBrush = Brush.verticalGradient(
    listOf(
        Color.Transparent,
        Color.Transparent,
        Color.Black.copy(alpha = 0.612f),
    ),
)

/**
 * 剧集卡片的剧照背景: 剧照裁切铺满, 上面盖一层与首页轮播相同的遮罩 ([episodeStillBrush]),
 * 画面上半部分不受影响, 只把底部一行文字所在的区域压暗.
 *
 * 由各处剧集卡片 (详情页网格、播放页横向卡片与选集面板) 共用, 放在卡片 `Box` 的最底层并 `matchParentSize()`.
 * 图片尚未加载或加载失败时只剩遮罩盖在卡片底色上, 文字依然可读, 卡片尺寸与外观保持稳定.
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
        Box(Modifier.matchParentSize().background(episodeStillBrush))
    }
}

/**
 * 剧集卡片底部的一行文字: 集号与集名并排, 集名占用剩余宽度并在超出时省略.
 *
 * 有无剧照的卡片都用这一行, 调用方把它放在卡片左下角, 使混合覆盖的一排卡片文字基线对齐.
 *
 * @param playingIndicator 播放中时显示在集号之前的指示图标, 非播放中传 null.
 */
@Composable
fun EpisodeCellLabel(
    sort: String,
    name: String,
    sortColor: Color,
    nameColor: Color,
    modifier: Modifier = Modifier,
    playingIndicator: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        playingIndicator?.invoke()
        Text(
            sort,
            color = sortColor,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
        )
        Text(
            name,
            color = nameColor,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 有剧照的剧集卡片上的文字颜色. 与首页轮播一致, 恒定取深色配色的前景色, 不随当前明暗变化.
 */
object EpisodeStillDefaults {
    /** 集号等主要文字. */
    val contentColor: Color
        @Composable
        get() {
            val provided = LocalDarkOnSurface.current
            return if (provided.isSpecified) provided else appColorScheme(isDark = true).onSurface
        }

    /** 集名等次要文字. */
    val secondaryContentColor: Color
        @Composable
        get() = contentColor.copy(alpha = 0.85f)

    /** 已看剧集的全部文字. */
    val watchedContentColor: Color
        @Composable
        get() = contentColor.copy(alpha = 0.7f)
}

/** [EpisodeStillBackground] 根节点的 test tag, 用于断言卡片是否显示了剧照. */
const val EPISODE_STILL_TAG: String = "episode_still"
