/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.subject.TestFollowedSubjectInfos
import me.him188.ani.app.ui.doesNotExist
import me.him188.ani.app.ui.exists
import me.him188.ani.app.ui.exploration.followed.FollowedSubjectsLazyRow
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.search.rememberTestLazyPagingItems
import kotlin.test.Test

private const val TAG_SCROLL_CONTROL_LEFT_BUTTON = "scrollControlLeftButton"
private const val TAG_SCROLL_CONTROL_RIGHT_BUTTON = "scrollControlRightButton"
private const val TAG_LAZY_LIST = "lazyList"

class HorizontalScrollControlScaffoldTest {
    private val SemanticsNodeInteractionsProvider.scrollControlLeftButton
        get() = onNodeWithTag(TAG_SCROLL_CONTROL_LEFT_BUTTON, useUnmergedTree = true)
    private val SemanticsNodeInteractionsProvider.scrollControlRightButton
        get() = onNodeWithTag(TAG_SCROLL_CONTROL_RIGHT_BUTTON, useUnmergedTree = true)
    private val SemanticsNodeInteractionsProvider.lazyList
        get() = onNodeWithTag(TAG_LAZY_LIST, useUnmergedTree = true)

    @Composable
    private fun View(
        listState: LazyListState = rememberLazyListState(),
    ) {
        val scope = rememberCoroutineScope()
        val density = LocalDensity.current

        ProvideFoundationCompositionLocalsForPreview {
            HorizontalScrollControlScaffold(
                rememberHorizontalScrollControlState(
                    scrollableState = listState,
                    scrollStep = { HorizontalScrollNavigatorDefaults.ScrollStep },
                    onClickScroll = { scope.launch { listState.scrollBy(with(density) { it.toPx() }) } },
                ),
                scrollLeftButton = {
                    HorizontalScrollNavigatorDefaults.ScrollLeftButton(
                        Modifier.testTag(TAG_SCROLL_CONTROL_LEFT_BUTTON),
                    )
                },
                scrollRightButton = {
                    HorizontalScrollNavigatorDefaults.ScrollRightButton(
                        Modifier.testTag(TAG_SCROLL_CONTROL_RIGHT_BUTTON),
                    )
                },
            ) {
                FollowedSubjectsLazyRow(
                    items = rememberTestLazyPagingItems(TestFollowedSubjectInfos),
                    lazyListState = listState,
                    modifier = Modifier.testTag(TAG_LAZY_LIST),
                    onClick = {},
                    onPlay = {},
                )
            }
        }
    }

    @Test
    fun `test scroll control button visibility`() = runAniComposeUiTest {
        val listState = LazyListState()

        setContent {
            View(listState)
        }

        runOnIdle {
            waitUntil { scrollControlLeftButton.doesNotExist() }
            waitUntil { scrollControlRightButton.doesNotExist() }
        }

        runOnIdle {
            onRoot().performMouseInput { // Move 事件才能触发 
                moveTo(centerLeft)
            }
        }

        runOnIdle {
            waitUntil { scrollControlLeftButton.exists() }
            waitUntil { scrollControlRightButton.doesNotExist() }
        }
    }
}