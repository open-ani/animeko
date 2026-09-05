/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.exploration

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.models.subject.SubjectCollectionInfo
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.tv.ui.foundation.widgets.TvBackdropDefaults
import me.him188.ani.tv.ui.foundation.widgets.TvHeroButton
import me.him188.ani.tv.ui.foundation.widgets.TvHeroDefaults
import me.him188.ani.tv.ui.foundation.widgets.tvBackdropFadeFromBlackStops
import me.him188.ani.tv.ui.foundation.widgets.tvBackdropFadeToBlackStops
import me.him188.ani.tv.ui.foundation.widgets.tvHeroContentColor
import me.him188.ani.tv.ui.foundation.widgets.tvHeroSecondaryContentColor
import me.him188.ani.tv.ui.foundation.widgets.tvShellBackgroundColor

/**
 * 探索页常驻 hero (纯视图; 状态与焦点接线在 [TvExplorationScreen]), 双态 (Prime Video 实测):
 * - **展开** (焦点在 hero): 最高热度轮播条目, 左侧标题/评分连载行/简介 + 「更多详细内容」按钮,
 *   轮播指示器在整个 hero 底部水平居中;
 * - **收缩** (焦点在下方卡片行, [collapsed]): 展示聚焦条目信息, 按钮与指示器淡出, 高度由外层缩减.
 *
 * [bottomFadeStart] 用 lambda 传入: 值随焦点两态插值动画逐帧变化, 必须只在 draw 阶段读取,
 * 直接传值会让整个 hero 每帧重组.
 *
 * [buttonModifier] 由调用方注入焦点锚点与按键处理 (页面私有的 TvFocusKey 不外泄).
 */
@Composable
internal fun TvExplorationHero(
    hero: TvHeroSubject?,
    info: SubjectCollectionInfo?,
    summaryFallback: String?,
    backdropUrl: String?,
    bottomFadeStart: () -> Float,
    collapsed: Boolean,
    carouselSize: Int,
    carouselIndex: Int,
    onClickDetails: () -> Unit,
    onButtonFocused: () -> Unit,
    modifier: Modifier = Modifier,
    buttonModifier: Modifier = Modifier,
) {
    val shellBackground = tvShellBackgroundColor()
    Box(modifier) {
        // Backdrop: 16:9 贴右上, 顶缘轻压暗 + 左缘/下缘平滑渐隐 (采样停点无马赫带)
        TvExplorationHeroBackdrop(
            url = backdropUrl,
            fadeColor = shellBackground,
            bottomFadeStart = bottomFadeStart,
            modifier = Modifier.align(Alignment.TopEnd),
        )
        // 左侧信息列: 标题/评分(固定) + 简介(weight=1 弹性, 收缩态自然只剩两三行) + 按钮 (展开态)
        Column(Modifier.fillMaxSize().padding(top = 24.dp, bottom = 10.dp)) {
            // 标题: 定高一行, 长标题跑马灯; 换条目 crossfade
            Crossfade(hero?.title, animationSpec = tween(TvHeroDefaults.TextFadeMillis), label = "title") { title ->
                Text(
                    title.orEmpty(),
                    Modifier.fillMaxWidth(TvHeroDefaults.TitleWidthFraction).basicMarquee(iterations = 3),
                    style = MaterialTheme.typography.headlineLarge,
                    color = tvHeroContentColor(),
                    maxLines = 1,
                )
            }
            TvExplorationHeroStatusRow(
                info,
                Modifier.padding(top = 8.dp).height(TvExplorationDefaults.StatusRowHeight),
            )
            val summary = info?.subjectInfo?.summary?.takeIf { it.isNotBlank() } ?: summaryFallback
            Crossfade(
                summary,
                Modifier.padding(top = 8.dp).weight(1f),
                animationSpec = tween(TvHeroDefaults.TextFadeMillis),
                label = "summary",
            ) { text ->
                Text(
                    text.orEmpty().trim(),
                    Modifier.fillMaxWidth(TvHeroDefaults.SummaryWidthFraction),
                    style = MaterialTheme.typography.bodyMedium,
                    color = tvHeroSecondaryContentColor(),
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // 唯一操作按钮 (展开态): 聚焦时左右键切换轮播、下键进卡片行 (按键处理在 buttonModifier)
            AnimatedVisibility(
                visible = !collapsed,
                enter = fadeIn(tween(TvExplorationDefaults.HeroChromeFadeMillis)),
                exit = fadeOut(tween(TvExplorationDefaults.HeroChromeFadeMillis)),
            ) {
                TvHeroButton(
                    text = "更多详细内容",
                    icon = Icons.Outlined.Info,
                    filled = true,
                    onClick = onClickDetails,
                    onFocused = onButtonFocused,
                    modifier = Modifier.padding(top = 10.dp, bottom = 22.dp).then(buttonModifier),
                )
            }
        }
        // 轮播指示器: 整个 hero 底部水平居中 (Prime 同款; 不可聚焦, 纯展示; 收缩态淡出)
        AnimatedVisibility(
            visible = !collapsed && carouselSize >= 2,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(TvExplorationDefaults.HeroChromeFadeMillis)),
            exit = fadeOut(tween(TvExplorationDefaults.HeroChromeFadeMillis)),
        ) {
            TvExplorationCarouselIndicator(
                size = carouselSize,
                selectedIndex = carouselIndex,
                modifier = Modifier.padding(bottom = 12.dp).fillMaxWidth(),
            )
        }
    }
}

/**
 * hero backdrop 层: 换图 crossfade, 顶缘压暗 + 左缘渐隐固定, 下缘渐隐起点由
 * [bottomFadeStart] 每帧提供 (hero/卡片两态插值).
 */
@Composable
private fun TvExplorationHeroBackdrop(
    url: String?,
    fadeColor: Color,
    bottomFadeStart: () -> Float,
    modifier: Modifier = Modifier,
) {
    Crossfade(
        url,
        modifier,
        animationSpec = tween(TvBackdropDefaults.CrossfadeMillis),
        label = "backdrop",
    ) { current ->
        if (current != null) {
            Box(
                Modifier
                    // 外层 hero Box 已按两态限高, 这里占满它
                    .fillMaxHeight()
                    .aspectRatio(TvBackdropDefaults.AspectRatio, matchHeightConstraintsFirst = true)
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.verticalGradient(
                                *tvBackdropFadeFromBlackStops(
                                    start = 0f, end = TvBackdropDefaults.TopScrimEnd,
                                    maxAlpha = TvBackdropDefaults.TopScrimAlpha,
                                    color = fadeColor,
                                ),
                            ),
                        )
                        drawRect(
                            brush = Brush.horizontalGradient(
                                *tvBackdropFadeFromBlackStops(
                                    start = TvBackdropDefaults.LeftFadeStart,
                                    end = TvBackdropDefaults.LeftFadeEnd,
                                    color = fadeColor,
                                ),
                            ),
                        )
                        drawRect(
                            brush = Brush.verticalGradient(
                                *tvBackdropFadeToBlackStops(
                                    start = bottomFadeStart(),
                                    end = 1f,
                                    color = fadeColor,
                                ),
                            ),
                        )
                    },
            ) {
                AsyncImage(
                    current,
                    contentDescription = null,
                    Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }
}

/** hero "评分 + 连载信息"状态行 (info 未加载时整行留白占位, 防换条目跳动). */
@Composable
private fun TvExplorationHeroStatusRow(
    info: SubjectCollectionInfo?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        info?.subjectInfo?.ratingInfo?.score
            ?.takeIf { it.isNotBlank() && it != "0" && it != "0.0" }
            ?.let { score ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "★",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "$score/10",
                        color = tvHeroContentColor(),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        val latest = info?.airingInfo?.latestSort
        val total = info?.subjectInfo?.totalEpisodes?.takeIf { it > 0 }
        val progressText = buildString {
            if (latest != null) append("连载至 $latest")
            if (total != null) {
                if (isNotEmpty()) append(" · ")
                append("预定全 $total 话")
            }
        }
        if (progressText.isNotEmpty()) {
            Text(
                progressText,
                style = MaterialTheme.typography.titleSmall,
                color = tvHeroSecondaryContentColor(),
            )
        }
        info?.subjectInfo?.airDate?.takeIf { it.isValid }?.let { date ->
            Text(
                "${date.year}年${date.month}月",
                style = MaterialTheme.typography.titleSmall,
                color = tvHeroSecondaryContentColor(),
            )
        }
    }
}

/** 轮播指示器: 选中项拉长胶囊, 其余小圆点. */
@Composable
private fun TvExplorationCarouselIndicator(
    size: Int,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(size) { index ->
            val selected = index == selectedIndex
            Box(
                Modifier
                    .height(5.dp)
                    .width(if (selected) 22.dp else 5.dp)
                    .clip(if (selected) RoundedCornerShape(2.5.dp) else CircleShape)
                    .background(
                        tvHeroContentColor().copy(alpha = if (selected) 0.95f else 0.35f),
                    ),
            )
        }
    }
}
