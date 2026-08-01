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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import me.him188.ani.app.data.models.recommend.RecommendedSubjectInfo
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.tv.ui.foundation.widgets.TvPosterCard

/** 探索页 hero 区当前展示的条目 (聚焦卡驱动). */
data class TvHeroSubject(
    val subjectId: Int,
    val title: String,
    val imageUrl: String,
)

/**
 * TV 探索页 (atv-architecture.md §7.1, M1 精简版):
 * 层叠 = backdrop (聚焦条目, 右上 16:9, 左/下缘渐隐) -> hero 信息块 + 按钮 -> 热门趋势行 + 推荐行.
 * M1 用海报图作 backdrop, TMDB 横版图 (R3) 后补.
 */
@Composable
fun TvExplorationScreen(
    viewModel: TvExplorationViewModel,
    onClickSubject: (TvHeroSubject) -> Unit,
    modifier: Modifier = Modifier,
) {
    val trends by viewModel.trends.collectAsState()
    val recommendations = viewModel.recommendations.collectAsLazyPagingItems()

    var focusedHero by remember { mutableStateOf<TvHeroSubject?>(null) }
    val hero = focusedHero
        ?: trends.firstOrNull()?.let { TvHeroSubject(it.bangumiId, it.nameCn, it.imageLarge) }

    val surfaceColor = MaterialTheme.colorScheme.surface

    Box(modifier.fillMaxSize()) {
        // Backdrop 层: 右上 16:9, 600ms crossfade (附录 A)
        Crossfade(hero?.imageUrl, animationSpec = tween(600), label = "backdrop") { url ->
            if (url != null) {
                Box(Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .fillMaxWidth(0.66f)
                            .aspectRatio(16f / 9f),
                        contentScale = ContentScale.Crop,
                    )
                    // 左缘渐隐
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    0.0f to surfaceColor,
                                    0.45f to surfaceColor,
                                    0.75f to surfaceColor.copy(alpha = 0.2f),
                                    1.0f to surfaceColor.copy(alpha = 0f),
                                ),
                            ),
                    )
                    // 下缘渐隐
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0.0f to surfaceColor.copy(alpha = 0f),
                                    0.45f to surfaceColor.copy(alpha = 0f),
                                    0.8f to surfaceColor,
                                ),
                            ),
                    )
                }
            }
        }

        // 手机横屏仅 ~400dp 高, 内容必须可滚动; 焦点驱动 LazyColumn 滚动 (M2 换 pivot 吸顶, §5.4)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 32.dp, bottom = 24.dp),
        ) {
            item("hero") {
                Column {
                    Column(
                        Modifier
                            .padding(start = 48.dp)
                            .fillMaxWidth(0.5f)
                            .height(120.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            hero?.title.orEmpty(),
                            style = MaterialTheme.typography.displaySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(
                        Modifier.padding(start = 48.dp, top = 8.dp, bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(onClick = { hero?.let(onClickSubject) }) {
                            Text("立即观看")
                        }
                        Button(onClick = { hero?.let(onClickSubject) }) {
                            Text("更多详情")
                        }
                    }
                }
            }

            item("trends") {
                Column {
                    Text(
                        "热门趋势",
                        Modifier.padding(start = 48.dp, bottom = 8.dp),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(start = 48.dp, end = 48.dp),
                    ) {
                        items(trends, key = { it.bangumiId }) { subject ->
                            TvPosterCard(
                                imageUrl = subject.imageLarge,
                                title = subject.nameCn,
                                onClick = {
                                    onClickSubject(
                                        TvHeroSubject(subject.bangumiId, subject.nameCn, subject.imageLarge),
                                    )
                                },
                                onFocused = {
                                    focusedHero =
                                        TvHeroSubject(subject.bangumiId, subject.nameCn, subject.imageLarge)
                                },
                            )
                        }
                    }
                }
            }

            item("recommendations") {
                Column(Modifier.padding(top = 16.dp)) {
                    Text(
                        "为你推荐",
                        Modifier.padding(start = 48.dp, bottom = 8.dp),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(start = 48.dp, end = 48.dp),
                    ) {
                        items(recommendations.itemCount) { index ->
                            when (val item = recommendations[index]) {
                                is RecommendedSubjectInfo -> TvPosterCard(
                                    imageUrl = item.imageLarge,
                                    title = item.nameCn,
                                    onClick = {
                                        onClickSubject(
                                            TvHeroSubject(item.bangumiId, item.nameCn, item.imageLarge),
                                        )
                                    },
                                    onFocused = {
                                        focusedHero =
                                            TvHeroSubject(item.bangumiId, item.nameCn, item.imageLarge)
                                    },
                                )

                                else -> {}
                            }
                        }
                    }
                }
            }
        }
    }
}
