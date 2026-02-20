/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.exploration.recommend

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import me.him188.ani.app.data.models.recommend.RecommendedItemInfo
import me.him188.ani.app.data.models.recommend.RecommendedSubjectInfo
import me.him188.ani.app.data.models.recommend.TestRecommendedItemInfos
import me.him188.ani.app.data.models.recommend.id
import me.him188.ani.app.data.models.recommend.type
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.animation.LocalAniMotionScheme
import me.him188.ani.app.ui.foundation.layout.CarouselItemDefaults
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.layout.minimumHairlineSize
import me.him188.ani.app.ui.search.LoadErrorCard
import me.him188.ani.app.ui.search.createTestPager
import me.him188.ani.app.ui.search.rememberLoadErrorState
import me.him188.ani.app.ui.subject.SubjectCoverCard
import me.him188.ani.app.ui.subject.SubjectGridDefaults
import me.him188.ani.app.ui.subject.SubjectGridLayoutParams
import me.him188.ani.utils.platform.annotations.TestOnly

fun LazyGridScope.recommendationItems(
    data: LazyPagingItems<RecommendedItemInfo>,
    loadError: LoadError?,
    onClick: (RecommendedItemInfo) -> Unit,
    layoutParams: RecommendationLayoutParams,
) {
    if (loadError != null) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Box(Modifier.minimumHairlineSize()) {
                LoadErrorCard(loadError, { data.refresh() })
            }
        }
    }
    items(
        data.itemCount,
        key = data.itemKey { "recommendation-" + it.id },
        contentType = data.itemContentType { it.type },
    ) { index ->
        val aniMotionScheme = LocalAniMotionScheme.current
        when (val item = data[index]) {
            null,// placeholder
            is RecommendedSubjectInfo -> {
                val animateItem = Modifier
                    .animateItem(
                        fadeInSpec = aniMotionScheme.feedItemFadeInSpec,
                        fadeOutSpec = aniMotionScheme.feedItemFadeOutSpec,
                        placementSpec = aniMotionScheme.feedItemPlacementSpec,
                    )
                RecommendedSubjectCard(
                    item = item,
                    onClick = { item?.let { onClick(it) } },
                    modifier = animateItem,
                    shape = layoutParams.cardShape,
                )
            }
        }
    }
}

@Composable
private fun RecommendedSubjectCard(
    item: RecommendedSubjectInfo?, // null means placeholder
    onClick: () -> Unit,
    shape: Shape = CarouselItemDefaults.shape,
    modifier: Modifier = Modifier,
) {
    SubjectCoverCard(
        name = item?.nameCn,
        image = item?.imageLarge,
        isPlaceholder = item == null,
        onClick = onClick,
        modifier = modifier,
        shape = shape,
        imageModifier = Modifier.sizeIn(maxWidth = 300.dp, maxHeight = (300f / 9 * 16).dp), // 限制最大宽度, 可以让 iOS 不卡一点
    )
}

typealias RecommendationLayoutParams = SubjectGridLayoutParams

@Stable
object RecommendationDefaults {
    @Composable
    fun layoutParameters(windowAdaptiveInfo: WindowAdaptiveInfo = currentWindowAdaptiveInfo1()): RecommendationLayoutParams {
        return SubjectGridDefaults.coverLayoutParameters(windowAdaptiveInfo)
    }
}

@OptIn(TestOnly::class)
@Composable
@PreviewScreenSizes
private fun PreviewRecommendationVerticalGrid() {
    ProvideCompositionLocalsForPreview {
        val layoutParams = RecommendationDefaults.layoutParameters()
        val data = createTestPager(TestRecommendedItemInfos).collectAsLazyPagingItems()
        val loadError by data.rememberLoadErrorState()
        LazyVerticalGrid(
            layoutParams.gridCells,
            contentPadding = PaddingValues(0.dp),
            horizontalArrangement = layoutParams.horizontalArrangement,
            verticalArrangement = layoutParams.verticalArrangement,
        ) {
            recommendationItems(
                data = data,
                loadError = loadError,
                onClick = {},
                layoutParams = layoutParams,
            )
        }
    }
}
