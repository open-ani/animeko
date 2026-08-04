/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import me.him188.ani.app.data.network.BatchSubjectDetails
import me.him188.ani.tv.ui.foundation.focus.TvFocusKey
import me.him188.ani.tv.ui.foundation.focus.rememberTvFocusScope
import me.him188.ani.tv.ui.foundation.focus.tvFocusAnchor
import me.him188.ani.tv.ui.foundation.focus.tvFocusNavSignal
import me.him188.ani.tv.ui.foundation.widgets.TvPageDefaults
import me.him188.ani.tv.ui.foundation.widgets.TvPosterCard

/** 搜索页焦点锚点 (统一焦点框架, 见 ui-foundation-tv/focus). */
private enum class TvSearchFocus : TvFocusKey {
    /** 搜索输入框 (进页初始焦点). */
    Field,
}

/**
 * TV 搜索页 (atv-architecture.md §7.3, M2 精简版):
 * 顶部输入框 (系统软键盘, ImeAction.Search 提交) + 结果网格. 历史/补全/筛选 M3 补.
 */
@Composable
fun TvSearchScreen(
    viewModel: TvSearchViewModel,
    onClickSubject: (BatchSubjectDetails) -> Unit,
    modifier: Modifier = Modifier,
) {
    val keywords by viewModel.keywords.collectAsState()
    val submitted by viewModel.hasSearched.collectAsState()
    val results = viewModel.results.collectAsLazyPagingItems()

    // 统一焦点框架: 进页初始焦点落输入框 (聚焦后按确认弹软键盘)
    val focus = rememberTvFocusScope()
    focus.Resolver()
    focus.InitialFocus(TvSearchFocus.Field)

    Column(modifier.fillMaxSize().tvFocusNavSignal(focus).padding(top = TvSearchDefaults.TopPadding)) {
        // TvTextField 精简版: BasicTextField + tv Surface 壳 (§5.3)
        Surface(
            modifier = Modifier
                .padding(start = TvSearchDefaults.FieldStartPadding)
                .fillMaxWidth(TvSearchDefaults.FieldWidthFraction),
            colors = SurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
            BasicTextField(
                value = keywords,
                onValueChange = viewModel::setKeywords,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
                    .tvFocusAnchor(focus, TvSearchFocus.Field),
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = MaterialTheme.typography.titleMedium.fontSize,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.search() }),
                singleLine = true,
                decorationBox = { innerTextField ->
                    Box {
                        if (keywords.isEmpty()) {
                            Text(
                                "搜索番剧…",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            )
                        }
                        innerTextField()
                    }
                },
            )
        }

        when {
            submitted == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "输入关键词, 按软键盘搜索键开始",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            results.itemCount == 0 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "没有找到相关番剧",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(TvPageDefaults.PosterGridCellMinWidth),
                modifier = Modifier.fillMaxSize(),
                contentPadding = TvPageDefaults.PosterGridContentPadding,
                horizontalArrangement = Arrangement.spacedBy(TvPageDefaults.CardSpacing),
                verticalArrangement = Arrangement.spacedBy(TvPageDefaults.CardSpacing),
            ) {
                items(results.itemCount, key = { results.peek(it)?.subjectInfo?.subjectId ?: it }) { index ->
                    val details = results[index] ?: return@items
                    TvPosterCard(
                        imageUrl = details.subjectInfo.imageLarge,
                        title = details.subjectInfo.displayName,
                        onClick = { onClickSubject(details) },
                    )
                }
            }
        }
    }
}

/** 搜索页默认值/调参. */
private object TvSearchDefaults {
    /** 页面顶部留白 (输入框上方). */
    val TopPadding = 32.dp

    /** 输入框左侧留白 (= overscan 安全边距 48). */
    val FieldStartPadding = 48.dp

    /** 输入框占屏宽比例. */
    const val FieldWidthFraction = 0.55f
}
