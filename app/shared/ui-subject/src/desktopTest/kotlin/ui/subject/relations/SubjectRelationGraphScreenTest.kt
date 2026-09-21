/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.relations

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.models.subject.SubjectRelationGraph
import me.him188.ani.app.data.models.subject.SubjectRelationGraphSubject
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.AniComposeUiTest
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.utils.platform.annotations.TestOnly
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(TestOnly::class, ExperimentalTestApi::class)
class SubjectRelationGraphScreenTest {
    private val clicked = mutableListOf<SubjectRelationGraphSubject>()
    private val originalLocale = Locale.getDefault()

    // 断言使用英文文案
    @BeforeTest
    fun setLocale() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restoreLocale() = Locale.setDefault(originalLocale)
    private var retryCount = 0

    private fun AniComposeUiTest.setContent(state: SubjectRelationGraphUiState, width: Dp = 390.dp) {
        setContent {
            ProvideCompositionLocalsForPreview {
                SubjectRelationGraphScreen(
                    state,
                    onRetry = { retryCount++ },
                    onClickSubject = { clicked.add(it) },
                    Modifier.width(width),
                )
            }
        }
    }

    private fun AniComposeUiTest.setContent(graph: SubjectRelationGraph, width: Dp = 390.dp) =
        setContent(SubjectRelationGraphUiState(graph, error = null), width)

    @Test
    fun `compact - shows series summary and marks the current subject`() = runAniComposeUiTest {
        setContent(TestSubjectRelationGraphs.ReZero)
        onNodeWithText("7 in main story · 7 specials and spin-offs").assertExists()
        onNodeWithText("Part 2 · Current").assertExists()
        onNodeWithText("Part 1").assertExists()
        onAllNodesWithText("Part 1 · Current").assertCountEquals(0)
    }

    @Test
    fun `compact - clicking a main node or a branch opens that subject`() = runAniComposeUiTest {
        setContent(TestSubjectRelationGraphs.ReZero)
        onNodeWithText("Re：从零开始的异世界生活 第二季 后半部分").performClick()
        onNodeWithText("Re：从零开始的休息时间2").performClick()
        assertEquals(listOf(316247, 310194), clicked.map { it.subjectId })
    }

    @Test
    fun `compact - branches beyond three are collapsed until expanded`() = runAniComposeUiTest {
        setContent(TestSubjectRelationGraphs.manyBranches(5))
        onNodeWithText("番外 3").assertExists()
        onNodeWithText("番外 4").assertDoesNotExist()
        onNodeWithText("2 more").performClick()
        onNodeWithText("番外 5").assertExists()
        onNodeWithText("Show less").performClick()
        onNodeWithText("番外 4").assertDoesNotExist()
        onNodeWithText("This series is too large. Only part of it is shown.").assertExists()
    }

    @Test
    fun `compact - collapsed part is expanded when it contains the current subject`() = runAniComposeUiTest {
        setContent(TestSubjectRelationGraphs.manyBranches(5).copy(subjectId = 105))
        onNodeWithText("番外 5").assertExists()
        onNodeWithText("Show less").assertExists()
    }

    @Test
    fun `wide - all branches are expanded`() = runAniComposeUiTest {
        setContent(TestSubjectRelationGraphs.manyBranches(5), width = 1280.dp)
        onNodeWithText("番外 5").assertExists()
        onNodeWithText("2 more").assertDoesNotExist()
        onNodeWithText("第二季").performScrollTo().performClick()
        assertEquals(listOf(2), clicked.map { it.subjectId })
    }

    @Test
    fun `compact - long series opens at the current subject`() = runAniComposeUiTest {
        setContent(TestSubjectRelationGraphs.Kimetsu)
        onNodeWithText("Part 2 · Current").assertExists()
        // 当前条目的前一部显示在顶部, 更早的条目需要向上滚动
        onNodeWithText("鬼灭之刃 无限列车篇").assertExists()
        onNodeWithText("鬼灭之刃 兄妹的羁绊").assertDoesNotExist()
    }

    @Test
    fun `wide - vertical mouse wheel scrolls the timeline horizontally`() = runAniComposeUiTest {
        setContent(TestSubjectRelationGraphs.Kimetsu, width = 1280.dp)
        onNodeWithText("鬼灭之刃 兄妹的羁绊").assertIsNotDisplayed() // 打开时已滚动到当前条目
        onNodeWithTag(SUBJECT_RELATION_GRAPH_TEST_TAG).performMouseInput {
            moveTo(center)
            repeat(60) { scroll(-3f) }
        }
        onNodeWithText("鬼灭之刃 兄妹的羁绊").assertIsDisplayed()
    }

    @Test
    fun `minor main nodes have no ordinal`() = runAniComposeUiTest {
        setContent(TestSubjectRelationGraphs.Kimetsu)
        onNodeWithTag(SUBJECT_RELATION_GRAPH_TEST_TAG).performScrollToIndex(0)
        // 第一部之前有一部剧场版, 它不计入 "第几部"
        onNodeWithText("鬼灭之刃 兄妹的羁绊").assertExists()
        onNodeWithText("Part 1").assertExists()
        onAllNodesWithText("Part 5").assertCountEquals(0)
    }

    @Test
    fun `error can be retried`() = runAniComposeUiTest {
        setContent(SubjectRelationGraphUiState(graph = null, error = LoadError.NetworkError))
        onNodeWithText("Retry").performClick()
        assertEquals(1, retryCount)
    }
}
