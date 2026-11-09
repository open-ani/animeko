/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.comment.thread

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import me.him188.ani.app.navigation.CommentThreadTarget
import me.him188.ani.app.ui.comment.CommentItemTestFixtures
import me.him188.ani.app.ui.comment.createTestCommentThreadState
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.navigation.LocalOnBackPressedDispatcherOwner
import me.him188.ani.app.ui.foundation.navigation.OnBackPressedDispatcher
import me.him188.ani.app.ui.foundation.navigation.OnBackPressedDispatcherOwner
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 原地打开的 thread 与系统返回的关系: 返回先关 thread, 再轮到宿主; 宿主让出 (全屏) 时不拦截.
 */
@OptIn(TestOnly::class)
class CommentThreadHostContentTest {
    private val key = CommentThreadKey(CommentThreadTarget.Episode(subjectId = 1, episodeId = 2), "ani:1")

    private class Harness(val host: CommentThreadHost) {
        var backHandlerEnabled by mutableStateOf(true)
    }

    private fun ComposeUiTest.setHost(dispatcher: OnBackPressedDispatcher): Harness {
        lateinit var harness: Harness
        setContent {
            ProvideCompositionLocalsForPreview {
                val scope = rememberCoroutineScope()
                harness = remember {
                    Harness(
                        CommentThreadHost { key, snapshot ->
                            createTestCommentThreadState(scope, key = key, root = snapshot, snapshot = snapshot)
                        },
                    )
                }
                val lifecycleOwner = LocalLifecycleOwner.current
                val owner = remember {
                    object : OnBackPressedDispatcherOwner {
                        override val onBackPressedDispatcher: OnBackPressedDispatcher get() = dispatcher
                        override val lifecycle: Lifecycle get() = lifecycleOwner.lifecycle
                    }
                }
                CompositionLocalProvider(LocalOnBackPressedDispatcherOwner provides owner) {
                    CommentThreadHostContent(
                        host = harness.host,
                        backHandlerEnabled = harness.backHandlerEnabled,
                        list = { Text("list", Modifier.testTag(LIST)) },
                        thread = { Text("thread", Modifier.testTag(THREAD)) },
                    )
                }
            }
        }
        return harness
    }

    private fun ComposeUiTest.awaitTag(tag: String) {
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun ComposeUiTest.awaitNoTag(tag: String) {
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun `back closes the thread first and only then leaves to the host`() = runAniComposeUiTest {
        var hostBack = 0
        val dispatcher = OnBackPressedDispatcher(fallbackOnBackPressed = { hostBack++ })
        val harness = setHost(dispatcher)
        onNodeWithTag(LIST).assertExists()

        runOnIdle { harness.host.open(key, snapshot = CommentItemTestFixtures.aniComment()) }
        awaitTag(THREAD)
        awaitNoTag(LIST)

        runOnIdle { dispatcher.onBackPressed() }
        awaitTag(LIST)
        awaitNoTag(THREAD)
        runOnIdle {
            assertNull(harness.host.current)
            assertEquals(0, hostBack, "thread 打开时返回只关 thread, 不交给宿主")
        }

        runOnIdle { dispatcher.onBackPressed() }
        runOnIdle { assertEquals(1, hostBack, "列表状态下返回交给宿主") }
    }

    @Test
    fun `disabled back handling leaves back to the host while the thread stays open`() = runAniComposeUiTest {
        var hostBack = 0
        val dispatcher = OnBackPressedDispatcher(fallbackOnBackPressed = { hostBack++ })
        val harness = setHost(dispatcher)
        runOnIdle { harness.host.open(key, snapshot = CommentItemTestFixtures.aniComment()) }
        awaitTag(THREAD)

        // 例如全屏播放时, 返回应先退出全屏, thread 不动
        runOnIdle { harness.backHandlerEnabled = false }
        runOnIdle { dispatcher.onBackPressed() }
        runOnIdle {
            assertNotNull(harness.host.current)
            assertEquals(1, hostBack)
        }

        runOnIdle { harness.backHandlerEnabled = true }
        runOnIdle { dispatcher.onBackPressed() }
        awaitTag(LIST)
        runOnIdle {
            assertNull(harness.host.current)
            assertEquals(1, hostBack)
        }
    }

    private companion object {
        const val LIST = "CommentThreadHostContentTest:list"
        const val THREAD = "CommentThreadHostContentTest:thread"
    }
}
