/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.mediasource.test

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.layout.cardHorizontalPadding
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1

/**
 * @see selectedItemIndex `-1` for no selection
 */
@Composable
internal fun <T> TestResultLazyRow(
    items: List<T>,
    selectedItemIndex: Int,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    item: @Composable LazyItemScope.(index: Int, T) -> Unit,
) {
    require(selectedItemIndex == -1 || selectedItemIndex >= 0) {
        "selectedItemIndex must be -1 or in the range of items"
    }
    val density = LocalDensity.current
    val lazyListState = rememberLazyListState()
    LaunchedEffect(selectedItemIndex) {
        if (selectedItemIndex != -1 && !lazyListState.isItemFullyVisible(selectedItemIndex)) {
            // 如果有选择一个目前不可见的项目, 将其滚动到可见区域
            lazyListState.animateScrollToItem(
                selectedItemIndex,
                // 左边显示一点点上个项目, 这样让他知道左边还有东西
                scrollOffset = -with(density) { 36.dp.roundToPx() },
            )
        }
    }
    LazyRow(
        modifier = modifier,
        state = lazyListState,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(currentWindowAdaptiveInfo1().windowSizeClass.cardHorizontalPadding),
    ) {
        for ((index, value) in items.withIndex()) {
            item {
                item(index, value)
            }
        }
    }
}

@Stable
private fun LazyListState.isItemFullyVisible(index: Int): Boolean {
    val layoutInfo = layoutInfo
    val item = layoutInfo.visibleItemsInfo.find { it.index == index }
        ?: return false
    if (item.offset < 0) return false

    // Check if the item is fully visible
    return item.offset + item.size <= layoutInfo.viewportEndOffset
}
