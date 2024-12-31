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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.him188.ani.app.ui.doesNotExist
import me.him188.ani.app.ui.exists
import me.him188.ani.app.ui.framework.runAniComposeUiTest
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
        itemCount: Int,
    ) {
        val scope = rememberCoroutineScope()
        val density = LocalDensity.current

        HorizontalScrollControlScaffold(
            rememberHorizontalScrollControlState(
                scrollableState = listState,
                scrollStep = { HorizontalScrollNavigatorDefaults.ScrollStep },
                onClickScroll = { scope.launch { listState.scrollBy(with(density) { it.toPx() }) } },
            ),
            modifier = Modifier.width(500.dp),
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
            LazyRow(
                state = listState,
                modifier = Modifier.testTag(TAG_LAZY_LIST).fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(itemCount) {
                    Surface(
                        color = MaterialTheme.colors.secondary,
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Spacer(Modifier.size(120.dp, 200.dp))
                    }
                }
            }
        }
    }

    @Test
    fun `too many items - left button - invisible when can't scroll forward`() = runAniComposeUiTest {
        val listState = LazyListState()

        setContent {
            View(listState, 10)
        }

        runOnIdle {
            waitUntil { scrollControlLeftButton.doesNotExist() }
            waitUntil { scrollControlRightButton.doesNotExist() }
        }

        runOnIdle {
            lazyList.performMouseInput { // Move 事件才能触发
                moveTo(centerLeft + Offset(10f, 0f))
            }
        }

        // 目前在最左边, 所以左边按钮应该不可见
        runOnIdle {
            waitUntil { scrollControlLeftButton.doesNotExist() }
            waitUntil { scrollControlRightButton.doesNotExist() }
        }
    }

    @Test
    fun `too many items - left button - visible when can scroll forward`() = runAniComposeUiTest {
        val listState = LazyListState()

        setContent {
            View(listState, 10)
        }

        runOnIdle {
            waitUntil { scrollControlLeftButton.doesNotExist() }
            waitUntil { scrollControlRightButton.doesNotExist() }
        }

        runOnIdle {
            lazyList.performTouchInput {
                swipeLeft(centerX, centerX - 100)
            }
            lazyList.performMouseInput { // Move 事件才能触发
                moveTo(centerLeft + Offset(10f, 0f))
            }
        }

        // 目前列表在最左边, 所以左边按钮应该不可见
        runOnIdle {
            waitUntil { scrollControlLeftButton.exists() }
            waitUntil { scrollControlRightButton.doesNotExist() }
        }
    }

    @Test
    fun `too many items - right button - invisible when already at the right end`() = runAniComposeUiTest {
        val listState = LazyListState()

        setContent {
            View(listState, 10)
        }

        runOnIdle {
            waitUntil { scrollControlLeftButton.doesNotExist() }
            waitUntil { scrollControlRightButton.doesNotExist() }
        }

        runOnIdle {
            lazyList.performTouchInput {
                repeat(10) { swipeLeft() } // 保证滑动到最右边
            }
            lazyList.performMouseInput { // Move 事件才能触发
                moveTo(centerRight - Offset(10f, 0f))
            }
        }

        // 目前列表在最右边, 所以左边按钮应该不可见
        runOnIdle {
            waitUntil { scrollControlLeftButton.doesNotExist() }
            waitUntil { scrollControlRightButton.doesNotExist() }
        }
    }

    @Test
    fun `too many items - right button - visible when can scroll forward`() = runAniComposeUiTest {
        val listState = LazyListState()

        setContent {
            View(listState, 10)
        }

        runOnIdle {
            waitUntil { scrollControlLeftButton.doesNotExist() }
            waitUntil { scrollControlRightButton.doesNotExist() }
        }

        runOnIdle {
            lazyList.performTouchInput {
                repeat(10) { swipeLeft() } // 保证滑动到最右边
                swipeRight(centerX, centerX + 100)
            }
            lazyList.performMouseInput { // Move 事件才能触发
                moveTo(centerRight - Offset(10f, 0f))
            }
        }

        // 目前列表在最右边, 所以左边按钮应该不可见
        runOnIdle {
            waitUntil { scrollControlLeftButton.doesNotExist() }
            waitUntil { scrollControlRightButton.exists() }
        }
    }

    @Test
    fun `too many items - both buttons - composite test`() = runAniComposeUiTest {
        val listState = LazyListState()

        setContent {
            View(listState, itemCount = 10)
        }

        // 初始在最左侧；左按钮不可见，右按钮也不可见（鼠标不在两边）
        runOnIdle {
            waitUntil { scrollControlLeftButton.doesNotExist() }
            waitUntil { scrollControlRightButton.doesNotExist() }
        }

        // 移动鼠标到左边，虽然在最左侧无法再向左滚动，但仍测试一下：左按钮不应出现
        runOnIdle {
            lazyList.performMouseInput {
                moveTo(centerLeft + Offset(10f, 0f))
            }
        }
        runOnIdle {
            waitUntil { scrollControlLeftButton.doesNotExist() }
            // 还没滚动过，右边也不出现
            waitUntil { scrollControlRightButton.doesNotExist() }
        }

        // 往右滑一点，让列表可以向左滚动
        runOnIdle {
            lazyList.performTouchInput {
                swipeLeft(centerX, centerX - 100) // 往左滑，向右滚动一些
            }
        }

        // 鼠标移动到左侧，按钮应出现
        runOnIdle {
            lazyList.performMouseInput {
                moveTo(centerLeft + Offset(10f, 0f))
            }
        }
        runOnIdle {
            waitUntil { scrollControlLeftButton.exists() }
            // 此时由于鼠标在左侧，右侧按钮不一定出现
            waitUntil { scrollControlRightButton.doesNotExist() }
        }

        // 再把鼠标移到右侧，左边按钮应该消失，右侧按钮出现
        runOnIdle {
            lazyList.performMouseInput {
                moveTo(centerRight - Offset(10f, 0f))
            }
        }
        runOnIdle {
            waitUntil { scrollControlLeftButton.doesNotExist() }
            // 若还能继续往右滚，就会显示右侧按钮
            waitUntil { scrollControlRightButton.exists() }
        }

        // 再滑动到最右边
        runOnIdle {
            lazyList.performTouchInput {
                repeat(10) { swipeLeft() } // 保证滑动到最右边
            }
        }
        runOnIdle {
            lazyList.performMouseInput {
                moveTo(centerRight - Offset(10f, 0f))
            }
        }

        runOnIdle {
            waitUntil { scrollControlLeftButton.doesNotExist() }
            waitUntil { scrollControlLeftButton.doesNotExist() }
        }

        runOnIdle {
            lazyList.performTouchInput {
                swipeRight(centerX, centerX + 100)
            }
        }
        runOnIdle {
            lazyList.performMouseInput {
                moveTo(centerRight - Offset(10f, 0f))
            }
        }

        runOnIdle {
            waitUntil { scrollControlLeftButton.doesNotExist() }
            waitUntil { scrollControlRightButton.exists() }
        }
    }

    @Test
    fun `too less items - both buttons - composite test`() = runAniComposeUiTest {
        val listState = LazyListState()

        setContent {
            View(listState, itemCount = 2)
        }

        // 初始在最左侧；左按钮不可见，右按钮也不可见（鼠标不在两边）
        runOnIdle {
            waitUntil { scrollControlLeftButton.doesNotExist() }
            waitUntil { scrollControlRightButton.doesNotExist() }
        }

        // 移动鼠标到左边，虽然在最左侧无法再向左滚动，但仍测试一下：左按钮不应出现
        runOnIdle {
            lazyList.performMouseInput {
                moveTo(centerLeft + Offset(10f, 0f))
            }
        }
        runOnIdle {
            waitUntil { scrollControlLeftButton.doesNotExist() }
            // 还没滚动过，右边也不出现
            waitUntil { scrollControlRightButton.doesNotExist() }
        }

        // 往右滑一点，让列表可以向左滚动
        runOnIdle {
            lazyList.performTouchInput {
                swipeLeft(centerX, centerX - 100) // 往左滑，向右滚动一些
            }
        }

        // 鼠标移动到左侧，按钮应出现
        runOnIdle {
            lazyList.performMouseInput {
                moveTo(centerLeft + Offset(10f, 0f))
            }
        }
        runOnIdle {
            waitUntil { scrollControlLeftButton.doesNotExist() }
            // 此时由于鼠标在左侧，右侧按钮不一定出现
            waitUntil { scrollControlRightButton.doesNotExist() }
        }

        // 再把鼠标移到右侧，左边按钮应该消失，右侧按钮出现
        runOnIdle {
            lazyList.performMouseInput {
                moveTo(centerRight - Offset(10f, 0f))
            }
        }
        runOnIdle {
            waitUntil { scrollControlLeftButton.doesNotExist() }
            // 若还能继续往右滚，就会显示右侧按钮
            waitUntil { scrollControlRightButton.doesNotExist() }
        }

        // 再滑动到最右边
        runOnIdle {
            lazyList.performTouchInput {
                repeat(10) { swipeLeft() } // 保证滑动到最右边
            }
        }
        runOnIdle {
            lazyList.performMouseInput {
                moveTo(centerRight - Offset(10f, 0f))
            }
        }

        runOnIdle {
            waitUntil { scrollControlLeftButton.doesNotExist() }
            waitUntil { scrollControlLeftButton.doesNotExist() }
        }

        runOnIdle {
            lazyList.performTouchInput {
                swipeRight(centerX, centerX + 100)
            }
        }
        runOnIdle {
            lazyList.performMouseInput {
                moveTo(centerRight - Offset(10f, 0f))
            }
        }

        runOnIdle {
            waitUntil { scrollControlLeftButton.doesNotExist() }
            waitUntil { scrollControlRightButton.doesNotExist() }
        }
    }
}