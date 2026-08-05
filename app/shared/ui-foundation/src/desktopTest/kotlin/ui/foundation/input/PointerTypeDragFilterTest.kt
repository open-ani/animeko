/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.input

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import me.him188.ani.app.ui.framework.runAniComposeUiTest

class PointerTypeDragFilterTest {
    @Test
    fun `stylus and eraser use touch gestures`() {
        assertEquals(PointerType.Touch, PointerType.Stylus.asGesturePointerType())
        assertEquals(PointerType.Touch, PointerType.Eraser.asGesturePointerType())

        val inputSource = ActiveInputSourceState()
        inputSource.record(PointerType.Stylus)
        inputSource.commit()
        assertEquals(PointerType.Touch, inputSource.current)
        assertEquals(PointerType.Touch, inputSource.latest)
    }

    @Test
    fun `touchDragOnly accepts first touch drag and rejects mouse drag without blocking clicks`() =
        runAniComposeUiTest {
            var dragged by mutableFloatStateOf(0f)
            var clicks by mutableIntStateOf(0)

            setContent {
                Box(
                    Modifier
                        .size(300.dp)
                        .testTag("target")
                        .touchDragOnly()
                        .draggable(
                            rememberDraggableState { dragged += it },
                            Orientation.Horizontal,
                        )
                        .clickable { clicks++ },
                )
            }

            val target = onNodeWithTag("target")
            target.performMouseInput {
                moveTo(centerLeft)
                press()
                moveTo(centerRight)
                release()
            }
            runOnIdle {
                assertEquals(0f, dragged)
                assertEquals(0, clicks)
            }

            target.performMouseInput { click() }
            runOnIdle {
                assertEquals(1, clicks)
            }

            target.performTouchInput {
                down(centerLeft)
                moveBy(Offset(width / 2f, 0f))
                up()
            }
            runOnIdle {
                assertNotEquals(0f, dragged)
            }
        }

    @Test
    fun `touchHorizontalScrollOnly blocks mouse paging without blocking child mouse drag`() =
        runAniComposeUiTest {
            lateinit var pagerState: PagerState
            var childDragged by mutableFloatStateOf(0f)

            setContent {
                pagerState = rememberPagerState { 2 }
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .size(300.dp)
                        .testTag("pager")
                        .touchHorizontalScrollOnly(),
                ) { page ->
                    Box(
                        Modifier
                            .fillMaxSize()
                            .testTag("child-$page")
                            .draggable(
                                rememberDraggableState { childDragged += it },
                                Orientation.Vertical,
                            ),
                    )
                }
            }

            onNodeWithTag("child-0").performMouseInput {
                moveTo(topCenter)
                press()
                moveTo(bottomCenter)
                release()
            }
            runOnIdle {
                assertNotEquals(0f, childDragged)
            }

            onNodeWithTag("pager").performMouseInput {
                moveTo(centerRight)
                press()
                moveTo(centerLeft)
                release()
            }
            waitForIdle()
            runOnIdle {
                assertEquals(0, pagerState.currentPage)
            }

            onNodeWithTag("pager").performTouchInput {
                down(centerRight)
                moveTo(centerLeft)
                up()
            }
            waitUntil {
                pagerState.currentPage == 1
            }
        }
}
