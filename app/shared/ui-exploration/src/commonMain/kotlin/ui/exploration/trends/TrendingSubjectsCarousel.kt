/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.exploration.trends

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.carousel.CarouselDefaults
import androidx.compose.material3.carousel.CarouselState
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.models.trending.TrendingSubjectInfo
import me.him188.ani.app.ui.external.placeholder.placeholder
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.app.ui.foundation.layout.CarouselItem
import me.him188.ani.app.ui.foundation.layout.CarouselItemDefaults
import me.him188.ani.app.ui.foundation.stateOf
import me.him188.ani.utils.platform.annotations.TestOnly

@Composable
fun TrendingSubjectsCarousel(
    state: TrendingSubjectsState,
    onClick: (TrendingSubjectInfo) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    itemSpacing: Dp = 8.dp,
    modifier: Modifier = Modifier,
    carouselState: CarouselState = rememberCarouselState(initialItem = 0) {
        state.numItems
    }
) {
    val size = CarouselItemDefaults.itemSize()
    HorizontalMultiBrowseCarousel(
        carouselState,
        preferredItemWidth = size.preferredWidth,
        modifier.padding(contentPadding).fillMaxWidth(),
        itemSpacing = itemSpacing,
        flingBehavior = CarouselDefaults.multiBrowseFlingBehavior(
            carouselState,
            snapAnimationSpec = spring(stiffness = Spring.StiffnessMedium),
        ),
    ) { index ->
        val item = state.subjects.getOrNull(index)
        CarouselItem(
            label = { CarouselItemDefaults.Text(item?.nameCn ?: "") },
            Modifier.placeholder(state.isPlaceholder, shape = CarouselItemDefaults.shape),
        ) {
            if (item != null) {
                Surface({ onClick(item) }) {
                    AsyncImage(
                        item.imageLarge,
                        modifier = Modifier.height(size.imageHeight),
                        contentDescription = item.nameCn,
                        contentScale = ContentScale.Crop,
                    )
                }
            } else {
                Box(Modifier.height(size.imageHeight).fillMaxWidth())
            }
        }
    }
}


@Stable
class TrendingSubjectsState(
    subjectsState: State<List<TrendingSubjectInfo>?>, // null means loading
) {
    val subjects by derivedStateOf {
        subjectsState.value ?: listOf(null, null, null, null, null, null, null, null)
    }
    val isPlaceholder by derivedStateOf {
        subjectsState.value == null
    }
    val numItems by derivedStateOf {
        subjects.size
    }
}

@TestOnly
val TestTrendingSubjectInfos
    get() = listOf(
        TrendingSubjectInfo(
            bangumiId = 467461,
            nameCn = "胆大党",
            imageLarge = "https://lain.bgm.tv/pic/cover/l/44/7d/467461_HHw4K.jpg",
        ),
        TrendingSubjectInfo(
            bangumiId = 425998,
            nameCn = "Re：从零开始的异世界生活 第三季 袭击篇",
            imageLarge = "https://lain.bgm.tv/pic/cover/l/26/d6/425998_dnzr8.jpg",
        ),
        TrendingSubjectInfo(
            bangumiId = 389156,
            nameCn = "地。 ―关于地球的运动―",
            imageLarge = "https://lain.bgm.tv/pic/cover/l/5f/84/389156_J4gqQ.jpg",
        ),
        TrendingSubjectInfo(
            bangumiId = 464376,
            nameCn = "败犬女主太多了！",
            imageLarge = "https://lain.bgm.tv/pic/cover/l/e4/dc/464376_NsZRw.jpg",
        ),
    )

@TestOnly
fun createTestTrendingSubjectsState(): TrendingSubjectsState {
    return TrendingSubjectsState(stateOf(TestTrendingSubjectInfos))
}

