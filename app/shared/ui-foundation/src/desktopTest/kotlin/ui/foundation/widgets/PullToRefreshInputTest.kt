/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.widgets

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import me.him188.ani.app.ui.foundation.input.ActiveInputSourceState
import me.him188.ani.app.ui.foundation.input.LocalActiveInputSource
import me.him188.ani.app.ui.foundation.input.trackActiveInputSource
import me.him188.ani.app.ui.framework.runAniComposeUiTest

@OptIn(ExperimentalMaterial3Api::class)
class PullToRefreshInputTest {
    @Test
    fun `touchOnly ignores mouse wheel overscroll but accepts touch pull`() = runAniComposeUiTest {
        val inputSource = ActiveInputSourceState()
        lateinit var pullState: PullToRefreshState
        var refreshes by mutableIntStateOf(0)

        setContent {
            CompositionLocalProvider(LocalActiveInputSource provides inputSource) {
                pullState = rememberPullToRefreshState()
                PullToRefreshBox(
                    isRefreshing = false,
                    onRefresh = { refreshes++ },
                    modifier = Modifier
                        .size(320.dp)
                        .trackActiveInputSource(inputSource)
                        .testTag("pull"),
                    state = pullState,
                    touchOnly = true,
                    indicator = {},
                ) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Spacer(Modifier.height(1.dp))
                    }
                }
            }
        }

        val pull = onNodeWithTag("pull")
        pull.performMouseInput {
            moveTo(center)
            scroll(100f)
        }
        runOnIdle {
            assertEquals(0, refreshes)
            assertEquals(0f, pullState.distanceFraction)
        }

        pull.performTouchInput {
            swipe(
                start = topCenter + Offset(0f, 20f),
                end = bottomCenter - Offset(0f, 20f),
                durationMillis = 1_000,
            )
        }
        runOnIdle {
            assertEquals(1, refreshes)
        }
    }

    @Test
    fun `touchOnly keeps normal mouse scrolling`() = runAniComposeUiTest {
        val inputSource = ActiveInputSourceState()
        lateinit var scrollState: ScrollState

        setContent {
            CompositionLocalProvider(LocalActiveInputSource provides inputSource) {
                scrollState = rememberScrollState(initial = 200)
                PullToRefreshBox(
                    isRefreshing = false,
                    onRefresh = {},
                    modifier = Modifier
                        .size(320.dp)
                        .trackActiveInputSource(inputSource)
                        .testTag("pull"),
                    touchOnly = true,
                    indicator = {},
                ) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState),
                    ) {
                        Spacer(Modifier.height(1_000.dp))
                    }
                }
            }
        }

        onNodeWithTag("pull").performMouseInput {
            moveTo(center)
            scroll(20f)
        }
        runOnIdle {
            assertNotEquals(200, scrollState.value)
        }
    }

    @Test
    fun `touchOnly does not block nested mouse drag`() = runAniComposeUiTest {
        val inputSource = ActiveInputSourceState()
        var dragged by mutableFloatStateOf(0f)

        setContent {
            CompositionLocalProvider(LocalActiveInputSource provides inputSource) {
                PullToRefreshBox(
                    isRefreshing = false,
                    onRefresh = {},
                    modifier = Modifier
                        .size(320.dp)
                        .trackActiveInputSource(inputSource),
                    touchOnly = true,
                    indicator = {},
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .testTag("child")
                            .draggable(
                                rememberDraggableState { dragged += it },
                                Orientation.Horizontal,
                            ),
                    )
                }
            }
        }

        onNodeWithTag("child").performMouseInput {
            moveTo(centerLeft)
            press()
            moveTo(centerRight)
            release()
        }
        runOnIdle {
            assertNotEquals(0f, dragged)
        }
    }
}
