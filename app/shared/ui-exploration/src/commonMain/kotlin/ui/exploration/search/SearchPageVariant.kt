/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.exploration.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow

/**
 * 搜索页变体: 应用入口可提供一个替代布局 (如遥控器形态的沉浸式布局).
 *
 * 与沉浸式探索页共用同一运行时开关
 * [ThemeSettings.tvImmersiveExploration][me.him188.ani.app.data.models.preference.ThemeSettings.tvImmersiveExploration],
 * 关闭则回退默认布局.
 */
fun interface SearchPageVariant {
    @Composable
    fun Page(
        state: SearchPageState,
        onIntent: (SearchPageIntent) -> Unit,
        suggestionsPager: (String) -> Flow<PagingData<String>>,
        modifier: Modifier,
    )
}

val LocalSearchPageVariant = staticCompositionLocalOf<SearchPageVariant?> { null }
