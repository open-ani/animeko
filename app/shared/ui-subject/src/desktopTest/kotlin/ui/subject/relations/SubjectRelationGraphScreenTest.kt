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
import androidx.compose.ui.semantics.SemanticsActions
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
import androidx.compose.ui.test.performSemanticsAction
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
        onNodeWithText("7 in main story · 8 related").assertExists()
        onNodeWithText("Part 2 · Current").assertExists()
        onNodeWithText("Part 1").assertExists()
        onAllNodesWithText("Part 1 · Current").assertCountEquals(0)
    }

    @Test
    fun `compact - clicking a main node or a branch opens that subject`() = runAniComposeUiTest {
        setContent(TestSubjectRelationGraphs.ReZero)
        onNodeWithText("Re：从零开始的休息时间2").performClick()
        // 第 0 项是标题, 第 3 项是第三部
        onNodeWithTag(SUBJECT_RELATION_GRAPH_TEST_TAG).performScrollToIndex(3)
        onNodeWithText("Re：从零开始的异世界生活 第二季 后半部分").performClick()
        assertEquals(listOf(310194, 316247), clicked.map { it.subjectId })
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
    fun `wide - branches beyond four are collapsed until expanded`() = runAniComposeUiTest {
        setContent(TestSubjectRelationGraphs.manyBranches(5), width = 1280.dp)
        onNodeWithText("番外 4").assertExists()
        onNodeWithText("番外 5").assertDoesNotExist()
        // 按钮在测试窗口的可视范围之下, 而 performScrollTo 只会滚动最近的 (横向) 容器, 因此直接触发点击语义
        onNodeWithText("1 more").performSemanticsAction(SemanticsActions.OnClick)
        onNodeWithText("番外 5").assertExists()
        onNodeWithText("第二季").performScrollTo().performClick()
        assertEquals(listOf(2), clicked.map { it.subjectId })
    }

    @Test
    fun `series name is removed from branch names`() = runAniComposeUiTest {
        setContent(TestSubjectRelationGraphs.ReZero)
        onNodeWithText("雪之回忆").assertExists()
        // 不是以完整的系列名开头, 保持原样
        onNodeWithText("Re：从零开始的休息时间").assertExists()
    }

    @Test
    fun `compact - long series opens at the current subject`() = runAniComposeUiTest {
        setContent(TestSubjectRelationGraphs.Kimetsu)
        onNodeWithText("Part 2 · Current").assertExists()
        // 当前条目的前一部显示在顶部, 更早的条目需要向上滚动
        onNodeWithText("剧场版 鬼灭之刃 无限列车篇").assertExists()
        onNodeWithText("兄妹的羁绊").assertDoesNotExist()
    }

    @Test
    fun `wide - vertical mouse wheel scrolls the timeline horizontally`() = runAniComposeUiTest {
        // 从第四部进入, 打开时时间线已经滚动到后面
        setContent(TestSubjectRelationGraphs.Kimetsu.copy(subjectId = 441939), width = 1280.dp)
        onNodeWithText("兄妹的羁绊").assertIsNotDisplayed()
        onNodeWithTag(SUBJECT_RELATION_GRAPH_TEST_TAG).performMouseInput {
            moveTo(center)
            repeat(60) { scroll(-3f) }
        }
        onNodeWithText("兄妹的羁绊").assertIsDisplayed()
    }

    @Test
    fun `movies on the mainline have no ordinal and compilations are listed under seasons`() = runAniComposeUiTest {
        setContent(TestSubjectRelationGraphs.Kimetsu)
        onNodeWithTag(SUBJECT_RELATION_GRAPH_TEST_TAG).performScrollToIndex(0)
        onNodeWithText("Part 1").assertExists()
        // 剧场版形式的总集篇不在主线上, 而是第一部下的一行
        onNodeWithText("兄妹的羁绊").assertExists()
        onNodeWithText("Compilation · 2019").assertExists()
        // 主线有 4 部正片和 4 部剧场版, 剧场版不计入 "第几部"
        onNodeWithText("4 in main story", substring = true).assertDoesNotExist()
        onNodeWithText("8 in main story · 11 related").assertExists()
    }

    @Test
    fun `error can be retried`() = runAniComposeUiTest {
        setContent(SubjectRelationGraphUiState(graph = null, error = LoadError.NetworkError))
        onNodeWithText("Retry").performClick()
        assertEquals(1, retryCount)
    }
}
