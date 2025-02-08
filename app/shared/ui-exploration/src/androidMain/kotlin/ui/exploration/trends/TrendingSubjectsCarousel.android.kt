/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:OptIn(TestOnly::class)

package me.him188.ani.app.ui.exploration.trends

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.preview.PreviewSizeClasses
import me.him188.ani.app.ui.search.rememberTestLazyPagingItems
import me.him188.ani.utils.platform.annotations.TestOnly

@Composable
@PreviewSizeClasses
@PreviewLightDark
private fun PreviewTrendingSubjectsCarousel() = ProvideCompositionLocalsForPreview {
    PreviewTrendingSubjectsCarouselImpl()
}

@Composable
private fun PreviewTrendingSubjectsCarouselImpl() {
    TrendingSubjectsCarousel(
        items = rememberTestLazyPagingItems(TestTrendingSubjectInfos),
        {},
    )
}
