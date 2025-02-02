/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:OptIn(TestOnly::class)

package me.him188.ani.app.ui.exploration

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.paging.compose.collectAsLazyPagingItemsWithLifecycle
import kotlinx.coroutines.flow.flowOf
import me.him188.ani.app.data.models.subject.TestFollowedSubjectInfos
import me.him188.ani.app.domain.session.TestUserInfo
import me.him188.ani.app.domain.session.createTestAuthState
import me.him188.ani.app.ui.exploration.trends.TestTrendingSubjectInfos
import me.him188.ani.app.ui.foundation.ProvideFoundationCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.preview.PreviewSizeClasses
import me.him188.ani.app.ui.foundation.stateOf
import me.him188.ani.app.ui.search.createTestPager
import me.him188.ani.utils.platform.annotations.TestOnly

@Composable
@PreviewSizeClasses
@PreviewLightDark
internal fun PreviewExplorationPage() {
    ProvideFoundationCompositionLocalsForPreview {
        val scope = rememberCoroutineScope()
        val trendingSubjectInfoPager = createTestPager(TestTrendingSubjectInfos).collectAsLazyPagingItemsWithLifecycle()
        ExplorationScreen(
            remember {
                ExplorationPageState(
                    authState = createTestAuthState(scope),
                    selfInfoState = stateOf(TestUserInfo),
                    trendingSubjectInfoPager,
                    followedSubjectsPager = createTestPager(TestFollowedSubjectInfos),
                    horizontalScrollTipFlow = flowOf(false),
                    onSetDisableHorizontalScrollTip = {},
                )
            },
            {},
            {},
        )
    }
}
