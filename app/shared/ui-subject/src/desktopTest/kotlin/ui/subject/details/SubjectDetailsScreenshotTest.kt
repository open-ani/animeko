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
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import me.him188.ani.app.data.models.subject.TestSelfRatingInfo
import me.him188.ani.app.data.models.subject.TestSubjectCollections
import me.him188.ani.app.data.models.subject.TestSubjectInfo
import me.him188.ani.app.ui.comment.createTestCommentState
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.stateOf
import me.him188.ani.app.ui.rating.createTestEditableRatingState
import me.him188.ani.app.ui.search.createTestPager
import me.him188.ani.app.ui.subject.collection.components.createTestEditableSubjectCollectionTypeState
import me.him188.ani.app.ui.subject.collection.progress.createTestSubjectProgressState
import me.him188.ani.app.ui.subject.createTestAiringLabelState
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
 * 无后端的确定性截图: 用测试数据在三个断点渲染新的自适应条目详情页, 导出 PNG 供人工对照 Figma.
 *
 * 断点由 Skiko 场景像素尺寸 (density=1 => px==dp) 控制:
 * 360 => Compact(手机, 复用 Header+TabRow, 新详情 tab) / 1400 => Medium(双栏) / 1600 => Expanded(三栏).
 *
 * 输出目录默认为模块 build/screenshots.
 */
@OptIn(TestOnly::class, ExperimentalTestApi::class)
class SubjectDetailsScreenshotTest {
    private val outDir: File =
        File(System.getProperty("ani.screenshot.out") ?: "build/screenshots").also { it.mkdirs() }

    /** 比 createTestSubjectDetailsState 更丰富: 带真实剧集列表与制作人员, 便于截图核对选集网格/右栏卡. */
    private fun richTestState(scope: CoroutineScope): SubjectDetailsState {
        val subjectInfo = TestSubjectCollections.first().subjectInfo
        return SubjectDetailsState(
            subjectId = TestSubjectInfo.subjectId,
            info = TestSubjectInfo,
            selfCollectionTypeState = stateOf(UnifiedCollectionType.DOING),
            airingLabelState = createTestAiringLabelState(),
            charactersPager = createTestPager(TestSubjectCharacterList),
            exposedCharactersPager = createTestPager(TestSubjectCharacterList.take(8)),
            totalCharactersCountState = stateOf(TestSubjectCharacterList.size),
            staffPager = createTestPager(TestSubjectStaffInfo),
            exposedStaffPager = createTestPager(TestSubjectStaffInfo.take(10)),
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
                    subjectId = TestSubjectInfo.subjectId,
                    displayName = TestSubjectInfo.displayName,
                    episodeListUiState = TestEpisodeListUiState,
                    isPlaceholder = false,
                ),
            ),
        )
    }

    private fun capture(widthDp: Int, heightDp: Int, name: String, scrolled: Boolean = false) {
        runSkikoComposeUiTest(Size(widthDp.toFloat(), heightDp.toFloat()), density = Density(1f)) {
            setContent {
                ProvideCompositionLocalsForPreview {
                    CompositionLocalProvider(LocalDensity provides Density(1f)) {
                        val scope = rememberCoroutineScope()
                        val state = remember {
                            richTestState(scope).let { SubjectDetailsUIState.Ok(it.subjectId, it) }
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
            if (scrolled) {
                // 慢速上滑 (低速度 ⇒ 几乎无 fling), 验证整页统一滚动与粘性标题栏.
                onRoot().performTouchInput { swipeUp(durationMillis = 1500) }
                waitForIdle()
            }
            val png = Image.makeFromBitmap(captureToImage().asSkiaBitmap())
                .encodeToData(EncodedImageFormat.PNG)
                ?.bytes
                ?: error("Failed to encode screenshot $name")
            File(outDir, "$name.png").writeBytes(png)
        }
    }

    // 高度对齐 Figma 定稿画板, 便于逐屏对照: 手机 1515:334 / 双栏 1505:335 / 三栏 1515:336.

    @Test
    fun compact() = capture(360, 1728, "detail-compact-360")

    /** 窄双栏 (600~1000): 播放页 ModalBottomSheet (640) / 手机横屏等场景. 无对应画板. */
    @Test
    fun mediumNarrow() = capture(700, 1400, "detail-medium-narrow-700")

    @Test
    fun medium() = capture(1400, 1464, "detail-medium-1400")

    @Test
    fun expanded() = capture(1600, 1213, "detail-expanded-1600")

    /** 滚动后: 顶部留白滚出, 顶栏显示条目名 (M3 粘性标题栏). 矮视口保证有足够滚动范围. */
    @Test
    fun expandedScrolled() = capture(1600, 800, "detail-expanded-1600-scrolled", scrolled = true)
}
