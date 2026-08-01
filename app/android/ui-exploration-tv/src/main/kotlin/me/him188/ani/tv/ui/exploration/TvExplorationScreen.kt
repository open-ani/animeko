/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.exploration

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import me.him188.ani.app.data.models.recommend.RecommendedSubjectInfo
import me.him188.ani.app.data.models.subject.SubjectCollectionInfo
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.tv.ui.foundation.widgets.TvPosterCard

/** 探索页 hero 区当前展示的条目 (聚焦卡驱动). */
data class TvHeroSubject(
    val subjectId: Int,
    val title: String,
    val imageUrl: String,
)

/**
 * TV 探索页 (atv-architecture.md §7.1):
 * 层叠 = backdrop (右半屏全高, 左缘渐隐) -> hero 信息块 (标题/元信息行/简介/纵向按钮组)
 * -> 轮播指示器 -> 卡片行 (纯图海报) -> 底部按键提示.
 *
 * 布局对齐参考实现 (第三方 TV 版实机效果).
 */
@Composable
fun TvExplorationScreen(
    viewModel: TvExplorationViewModel,
    onClickSubject: (TvHeroSubject) -> Unit,
    modifier: Modifier = Modifier,
) {
    val trends by viewModel.trends.collectAsState()
    val recommendations = viewModel.recommendations.collectAsLazyPagingItems()
    val details by viewModel.focusedDetails.collectAsState()

    var focusedHero by remember { mutableStateOf<TvHeroSubject?>(null) }
    var focusedIndexInTrends by remember { mutableStateOf(0) }
    val hero = focusedHero
        ?: trends.firstOrNull()?.let { TvHeroSubject(it.bangumiId, it.nameCn, it.imageLarge) }

    fun focus(subject: TvHeroSubject, indexInTrends: Int = -1) {
        focusedHero = subject
        if (indexInTrends >= 0) focusedIndexInTrends = indexInTrends
        viewModel.setFocusedSubject(subject.subjectId)
    }

    // 首帧 hero 取自趋势首项, 同样需要拉详情 (评分/连载/简介)
    LaunchedEffect(hero?.subjectId) {
        hero?.let { viewModel.setFocusedSubject(it.subjectId) }
    }

    val surfaceColor = MaterialTheme.colorScheme.surface

    Box(modifier.fillMaxSize()) {
        // Backdrop: 右半屏全高 + 左缘/下缘渐隐 (参考版布局), 600ms crossfade (附录 A)
        Crossfade(hero?.imageUrl, animationSpec = tween(600), label = "backdrop") { url ->
            if (url != null) {
                Box(Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxWidth(0.56f)
                            .fillMaxHeight(),
                        contentScale = ContentScale.Crop,
                    )
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    0.0f to surfaceColor,
                                    0.36f to surfaceColor,
                                    0.62f to surfaceColor.copy(alpha = 0.25f),
                                    1.0f to surfaceColor.copy(alpha = 0f),
                                ),
                            ),
                    )
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0.0f to surfaceColor.copy(alpha = 0f),
                                    0.42f to surfaceColor.copy(alpha = 0f),
                                    0.78f to surfaceColor.copy(alpha = 0.9f),
                                    1.0f to surfaceColor,
                                ),
                            ),
                    )
                }
            }
        }

        // hero 常驻 (不随卡片行滚动), 卡片区独立滚动 —— 对齐参考版
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxWidth().weight(0.52f).padding(top = 18.dp)) {
                TvExplorationHero(
                    hero = hero,
                    details = details,
                    onPlay = { hero?.let(onClickSubject) },
                    onDetails = { hero?.let(onClickSubject) },
                    modifier = Modifier.weight(1f),
                )
                if (trends.isNotEmpty()) {
                    TvCarouselIndicator(
                        count = trends.size.coerceAtMost(12),
                        selectedIndex = focusedIndexInTrends.coerceAtMost(11),
                        modifier = Modifier.padding(start = 48.dp, bottom = 6.dp),
                    )
                }
            }

            LazyColumn(
                Modifier.fillMaxWidth().weight(0.48f),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                item("trends") {
                TvSubjectRow(
                    title = "热门趋势",
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    itemsIndexed(trends, key = { _, it -> it.bangumiId }) { index, subject ->
                        val item = TvHeroSubject(subject.bangumiId, subject.nameCn, subject.imageLarge)
                        TvPosterCard(
                            imageUrl = subject.imageLarge,
                            title = subject.nameCn,
                            onClick = { onClickSubject(item) },
                            onFocused = { focus(item, index) },
                            width = 124.dp,
                            showTitle = false,
                        )
                    }
                }
            }

            item("recommendations") {
                TvSubjectRow(
                    title = "推荐",
                    modifier = Modifier.padding(top = 20.dp),
                ) {
                    items(recommendations.itemCount) { index ->
                        when (val info = recommendations[index]) {
                            is RecommendedSubjectInfo -> {
                                val item = TvHeroSubject(info.bangumiId, info.nameCn, info.imageLarge)
                                TvPosterCard(
                                    imageUrl = info.imageLarge,
                                    title = info.nameCn,
                                    onClick = { onClickSubject(item) },
                                    onFocused = { focus(item) },
                                    width = 124.dp,
                                    showTitle = false,
                                )
                            }

                            else -> {}
                        }
                    }
                }
                }
            }
        }

        // 底部按键提示 (参考版右下角)
        Text(
            "▶ 播放键继续播放 · 长按选择键编辑收藏",
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 48.dp, bottom = 10.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
        )
    }
}

@Composable
private fun TvExplorationHero(
    hero: TvHeroSubject?,
    details: SubjectCollectionInfo?,
    onPlay: () -> Unit,
    onDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 详情与当前 hero 不一致时 (仍在加载) 不显示旧数据
    val info = details?.takeIf { it.subjectId == hero?.subjectId }

    BoxWithConstraints(modifier) {
        // 简介行数按可用高度自适应: TV 1080p 给 4 行, 手机横屏等矮屏优先保证按钮可见
        val summaryMaxLines = when {
            maxHeight >= 380.dp -> 4
            maxHeight >= 300.dp -> 3
            maxHeight >= 250.dp -> 2
            else -> 0
        }
        TvExplorationHeroContent(hero, info, summaryMaxLines, onPlay, onDetails)
    }
}

@Composable
private fun TvExplorationHeroContent(
    hero: TvHeroSubject?,
    info: SubjectCollectionInfo?,
    summaryMaxLines: Int,
    onPlay: () -> Unit,
    onDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(start = 48.dp)) {
        Text(
            hero?.title.orEmpty(),
            Modifier.fillMaxWidth(0.44f),
            style = MaterialTheme.typography.headlineLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        // 元信息行: ★评分 · 连载进度 · 话数 · 年月
        Row(
            Modifier.padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            info?.subjectInfo?.ratingInfo?.score?.takeIf { it.isNotBlank() && it != "0" }?.let { score ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("★", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
                    Text("$score/10", style = MaterialTheme.typography.titleMedium)
                }
            }
            info?.let { collection ->
                val latest = collection.airingInfo.latestSort
                val total = collection.subjectInfo.totalEpisodes.takeIf { it > 0 }
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                collection.subjectInfo.airDate.takeIf { it.isValid }?.let { date ->
                    Text(
                        "${date.year}年${date.month}月",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // 简介 (行数随可用高度自适应, 矮屏优先保证按钮可见)
        if (summaryMaxLines > 0) {
            Text(
                info?.subjectInfo?.summary.orEmpty(),
                Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth(0.42f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = summaryMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // 纵向按钮组 (参考版为竖排, 带图标)
        Column(
            Modifier.padding(top = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = onPlay) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, Modifier.size(20.dp))
                Text("立即观看", Modifier.padding(start = 8.dp))
            }
            Button(onClick = onDetails) {
                Icon(Icons.Rounded.Info, contentDescription = null, Modifier.size(20.dp))
                Text("更多详细内容", Modifier.padding(start = 8.dp))
            }
        }
    }
}

/** hero 轮播指示器: 当前项为拉长胶囊, 其余为小圆点 (参考版视觉). */
@Composable
private fun TvCarouselIndicator(
    count: Int,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(count) { index ->
            val selected = index == selectedIndex
            Box(
                Modifier
                    .height(6.dp)
                    .width(if (selected) 26.dp else 6.dp)
                    .clip(if (selected) RoundedCornerShape(3.dp) else CircleShape)
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f)
                        },
                    ),
            )
        }
    }
}

@Composable
private fun TvSubjectRow(
    title: String,
    modifier: Modifier = Modifier,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    Column(modifier) {
        Text(
            title,
            Modifier.padding(start = 48.dp, bottom = 8.dp),
            style = MaterialTheme.typography.titleMedium,
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(start = 48.dp, end = 48.dp),
            content = content,
        )
    }
}
