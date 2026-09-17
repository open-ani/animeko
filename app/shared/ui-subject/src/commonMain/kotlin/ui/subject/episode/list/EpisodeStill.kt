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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.app.ui.foundation.theme.LocalDarkOnSurface
import me.him188.ani.app.ui.foundation.theme.appColorScheme
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.subject_episode_unwatch
import me.him188.ani.app.ui.lang.subject_episode_watched_badge
import org.jetbrains.compose.resources.stringResource

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
 * 画面上半部分不受影响, 只把底部一行文字所在的区域压暗. 剧照亮度不随观看状态变化, 已看由角标表示.
 *
 * 由各处剧集卡片 (详情页网格、播放页横向卡片与选集面板) 共用, 放在卡片 `Box` 的最底层并 `matchParentSize()`.
 * 图片尚未加载或加载失败时只剩遮罩盖在卡片底色上, 文字依然可读, 卡片尺寸与外观保持稳定.
 *
 * @param highlighted 播放中时叠加一层 primary 蒙层.
 */
@Composable
fun EpisodeStillBackground(
    imageUrl: String,
    highlighted: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier.testTag(EPISODE_STILL_TAG)) {
        AsyncImage(
            imageUrl,
            contentDescription = null,
            Modifier.matchParentSize(),
            contentScale = ContentScale.Crop,
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
 * 剧集卡片右上角的「已看完」文字角标, 只在剧集收藏状态为 DONE 时显示.
 * 点击取消已看, 效果与长按卡片相同; 点击在角标上消费, 不会触发卡片本身的点击.
 *
 * @param onStill 是否叠在剧照上: 是则半透明黑底白字, 否则用主题的 secondaryContainer 配色.
 */
@Composable
fun EpisodeWatchedBadge(
    onStill: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (onStill) Color.Black.copy(alpha = 0.55f) else MaterialTheme.colorScheme.secondaryContainer
    val contentColor = if (onStill) Color.White else MaterialTheme.colorScheme.onSecondaryContainer
    Text(
        stringResource(Lang.subject_episode_watched_badge),
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(containerColor)
            .clickable(role = Role.Button, onClickLabel = stringResource(Lang.subject_episode_unwatch), onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .testTag(EPISODE_WATCHED_BADGE_TAG),
        color = contentColor,
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
    )
}

/**
 * 剧集卡片底边的上次播放进度条: 全宽轨道, 按 [progress] (`0..1`) 填充 primary 色.
 * 只在剧集未看完且有播放记录时显示, 见 [EpisodeListItem.playProgress].
 *
 * @param onStill 是否叠在剧照上, 决定轨道颜色.
 */
@Composable
fun EpisodePlayProgressBar(
    progress: Float,
    onStill: Boolean,
    modifier: Modifier = Modifier,
) {
    val trackColor = if (onStill) Color.White.copy(alpha = 0.3f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
    Box(
        modifier
            .fillMaxWidth()
            .height(3.dp)
            .background(trackColor)
            .testTag(EPISODE_PROGRESS_TAG),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

/**
 * 有剧照的剧集卡片上的文字颜色. 与首页轮播一致, 恒定取深色配色的前景色, 不随当前明暗与观看状态变化.
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
}

/** [EpisodeStillBackground] 根节点的 test tag, 用于断言卡片是否显示了剧照. */
const val EPISODE_STILL_TAG: String = "episode_still"

/** [EpisodeWatchedBadge] 的 test tag. */
const val EPISODE_WATCHED_BADGE_TAG: String = "episode_watched_badge"

/** [EpisodePlayProgressBar] 的 test tag. */
const val EPISODE_PROGRESS_TAG: String = "episode_play_progress"
