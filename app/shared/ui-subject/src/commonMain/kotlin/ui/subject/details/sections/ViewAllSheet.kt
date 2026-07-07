/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.details.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import me.him188.ani.app.ui.foundation.layout.desktopTitleBar
import me.him188.ani.app.ui.foundation.layout.desktopTitleBarPadding
import me.him188.ani.app.ui.foundation.layout.plus

/**
 * "查看全部" 的全量列表 sheet: 标题 + 自适应网格 (按 [cellMinWidth] 自动分列).
 *
 * 用于角色/制作人员 (全量 pager, 行卡) 与关联作品 (封面卡, 传较小 [cellMinWidth]).
 */
@Composable
internal fun <T : Any> ViewAllSheet(
    title: String,
    items: LazyPagingItems<T>,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    cellMinWidth: Dp = 240.dp,
    itemContent: @Composable (T) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest,
        modifier = modifier.desktopTitleBarPadding().statusBarsPadding(),
        contentWindowInsets = {
            BottomSheetDefaults.windowInsets
                .add(WindowInsets.desktopTitleBar())
                .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
        },
    ) {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            LazyVerticalGrid(
                GridCells.Adaptive(minSize = cellMinWidth),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = WindowInsets.navigationBars.asPaddingValues()
                    .plus(PaddingValues(bottom = 16.dp)),
            ) {
                items(
                    items.itemCount,
                    items.itemKey(),
                    contentType = items.itemContentType(),
                ) { index ->
                    items[index]?.let { itemContent(it) }
                }
            }
        }
    }
}
