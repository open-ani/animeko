/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.details

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.resetMain
import me.him188.ani.app.data.models.subject.TestCoverImage
import me.him188.ani.app.data.models.subject.TestSelfRatingInfo
import me.him188.ani.app.data.models.subject.TestSubjectCollections
import me.him188.ani.app.data.models.subject.TestSubjectInfo
import me.him188.ani.app.ui.comment.createTestCommentState
import me.him188.ani.app.ui.foundation.IMAGE_VIEWER_TEST_TAG
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.stateOf
import me.him188.ani.app.ui.framework.runOnSwingEdt
import me.him188.ani.app.ui.rating.createTestEditableRatingState
import me.him188.ani.app.ui.search.createTestPager
import me.him188.ani.app.ui.subject.collection.components.createTestEditableSubjectCollectionTypeState
import me.him188.ani.app.ui.subject.collection.progress.createTestSubjectProgressState
import me.him188.ani.app.ui.subject.createTestAiringLabelState
import me.him188.ani.app.ui.subject.details.components.SUBJECT_COVER_IMAGE_TEST_TAG
import me.him188.ani.app.ui.subject.details.state.SubjectDetailsPresentation
import me.him188.ani.app.ui.subject.details.state.SubjectDetailsState
import me.him188.ani.app.ui.subject.episode.list.TestEpisodeListUiState
import me.him188.ani.app.ui.user.TestSelfInfoUiState
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test

/**
 * 交互测试: 条目详情页点击封面打开页面级图片查看器, 再点击查看器关闭.
 *
 * 断点由 Skiko 场景像素尺寸控制 (density=1 => px==dp):
 * 360 => Compact(手机单栏) / 1400 => Medium(双栏).
 */
@OptIn(TestOnly::class, ExperimentalTestApi::class)
class SubjectDetailsImageViewerTest {
    private val outDir: File =
        File(System.getProperty("ani.screenshot.out") ?: "build/screenshots").also { it.mkdirs() }

    /** 与 [SubjectDetailsScreenshotTest] 一致, 导出 PNG 供人工核对交互结果. */
    private fun SkikoComposeUiTest.capture(name: String) {
        val png = Image.makeFromBitmap(captureToImage().asSkiaBitmap())
            .encodeToData(EncodedImageFormat.PNG)
            ?.bytes
            ?: error("Failed to encode screenshot $name")
        File(outDir, "$name.png").writeBytes(png)
    }

    /** 与截图测试的数据类似, 但封面为非空 URL (空 URL 不启用点击放大). */
    private fun testStateWithImages(scope: CoroutineScope): SubjectDetailsState {
        val subjectInfo = TestSubjectCollections.first().subjectInfo
        val characters = TestSubjectCharacterList
        val info = TestSubjectInfo.copy(imageLarge = TestCoverImage)
        return SubjectDetailsState(
            subjectId = info.subjectId,
            info = info,
            selfCollectionTypeState = stateOf(UnifiedCollectionType.DOING),
            airingLabelState = createTestAiringLabelState(),
            charactersPager = createTestPager(characters),
            exposedCharactersPager = createTestPager(characters),
            totalCharactersCountState = stateOf(characters.size),
            staffPager = createTestPager(TestSubjectStaffInfo),
            exposedStaffPager = createTestPager(TestSubjectStaffInfo),
            totalStaffCountState = stateOf(TestSubjectStaffInfo.size),
            relatedSubjectsPager = createTestPager(TestRelatedSubjects),
            editableSubjectCollectionTypeState = createTestEditableSubjectCollectionTypeState(
                MutableStateFlow(UnifiedCollectionType.DOING),
                scope,
            ),
            editableRatingState = createTestEditableRatingState(
                subjectInfo,
                selfRatingInfo = TestSelfRatingInfo,
                backgroundScope = scope,
            ),
            subjectProgressState = createTestSubjectProgressState(),
            subjectCommentState = createTestCommentState(scope),
            presentation = MutableStateFlow(
                SubjectDetailsPresentation(
                    subjectId = info.subjectId,
                    displayName = info.displayName,
                    episodeListUiState = TestEpisodeListUiState,
                    isPlaceholder = false,
                ),
            ),
        )
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun runPageTest(widthDp: Int, heightDp: Int, block: SkikoComposeUiTest.() -> Unit) = runOnSwingEdt {
        // 查看器的单击关闭经 Dispatchers.Main 真实 delay 触发; 确保 Main 未被其他测试替换为虚拟时钟.
        Dispatchers.resetMain()
        // 查看器 (zoomimage) 要求手势在 Swing EDT 上处理, 因此整个测试在 EDT 上跑.
        runSkikoComposeUiTest(Size(widthDp.toFloat(), heightDp.toFloat()), density = Density(1f)) {
            setContent {
                ProvideCompositionLocalsForPreview {
                    CompositionLocalProvider(LocalDensity provides Density(1f)) {
                        val scope = rememberCoroutineScope()
                        val state = remember {
                            testStateWithImages(scope).let { SubjectDetailsUIState.Ok(it.subjectId, it) }
                        }
                        SubjectDetailsScreen(
                            state,
                            TestSelfInfoUiState,
                            onPlay = {},
                            onLoadErrorRetry = {},
                            onClickTag = {},
                            onEpisodeCollectionUpdate = {},
                        )
                    }
                }
            }
            waitForIdle()
            block()
        }
    }

    @Test
    fun `compact - click cover opens image viewer`() = runPageTest(360, 800) {
        onNodeWithTag(IMAGE_VIEWER_TEST_TAG).assertDoesNotExist()
        onNodeWithTag(SUBJECT_COVER_IMAGE_TEST_TAG).performClick()
        waitForIdle()
        onNodeWithTag(IMAGE_VIEWER_TEST_TAG).assertIsDisplayed()
        capture("image-viewer-compact-cover-open")
    }

    @Test
    fun `multi column - click cover opens image viewer, tap closes it`() = runPageTest(1400, 1400) {
        onNodeWithTag(IMAGE_VIEWER_TEST_TAG).assertDoesNotExist()
        capture("image-viewer-multicolumn-before")
        onNodeWithTag(SUBJECT_COVER_IMAGE_TEST_TAG).performClick()
        waitForIdle()
        onNodeWithTag(IMAGE_VIEWER_TEST_TAG).assertIsDisplayed()
        capture("image-viewer-multicolumn-cover-open")

        // 单击查看器关闭. 注意: 查看器的 tap 检测要求手势期间没有 Move 事件
        // (performClick 会注入 down-move-up), 因此只注入 down+up;
        // 其单击回调经真实墙钟 delay(272ms) 触发 (区分双击), 不受测试虚拟时钟控制,
        // 因此用实时轮询等待节点消失.
        onNodeWithTag(IMAGE_VIEWER_TEST_TAG).performTouchInput {
            down(center)
            up()
        }
        waitUntil(timeoutMillis = 5000) {
            onAllNodesWithTag(IMAGE_VIEWER_TEST_TAG).fetchSemanticsNodes().isEmpty()
        }
    }
}
