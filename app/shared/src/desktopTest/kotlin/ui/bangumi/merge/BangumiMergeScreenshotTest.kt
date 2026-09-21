/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.bangumi.merge

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import me.him188.ani.app.data.models.bangumi.BangumiMergeSide
import me.him188.ani.app.data.models.preference.DarkMode
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * 合并收藏界面在设计稿四个断点下的截图, 输出到 `build/screenshots` 供人工/Figma 对照:
 * - 412dp 移动
 * - 320dp 小屏 (双行单元格)
 * - 960dp 桌面表格
 * - 1680dp 超宽双栏
 * - 412dp 已同步空态 (含自动合并明细)
 * - 412dp 首次绑定同步中空态 (专用的 "正在同步收藏" 标题与说明, 没有提示条 / 底栏)
 * - 412dp 其他状态: 尚未选择、全部确认 (可应用)、提交中、自动合并明细展开、同步进行中提示、加载中、加载失败
 * - 深色主题 (412 / 960 / 已同步空态)
 * - 1280dp 桌面 (表格封顶 1080dp 居中)
 * - 主界面的冲突提示条
 */
@OptIn(TestOnly::class, ExperimentalTestApi::class)
class BangumiMergeScreenshotTest {
    private val outDir: File =
        File(System.getProperty("ani.screenshot.out") ?: "build/screenshots").also { it.mkdirs() }

    private val now = Instant.fromEpochMilliseconds(1_753_000_000_000)

    private fun capture(
        widthDp: Int,
        heightDp: Int,
        name: String,
        state: BangumiMergeUiState = createTestBangumiMergeUiState(now),
        darkMode: DarkMode = DarkMode.LIGHT,
        beforeCapture: SkikoComposeUiTest.() -> Unit = {},
    ) = captureContent(widthDp, heightDp, name, darkMode, beforeCapture) {
        BangumiMergeScreen(
            state = state,
            onSelect = { _, _ -> },
            onAdoptNewer = {},
            onSelectAll = {},
            onApply = {},
            onRetry = {},
            onNavigateBack = {},
            getTimeNow = { now },
        )
    }

    private fun captureContent(
        widthDp: Int,
        heightDp: Int,
        name: String,
        darkMode: DarkMode = DarkMode.LIGHT,
        beforeCapture: SkikoComposeUiTest.() -> Unit = {},
        content: @androidx.compose.runtime.Composable () -> Unit,
    ) {
        val defaultLocale = Locale.getDefault()
        Locale.setDefault(Locale.SIMPLIFIED_CHINESE)
        try {
            runSkikoComposeUiTest(Size(widthDp.toFloat(), heightDp.toFloat()), density = Density(1f)) {
                setContent {
                    ProvideCompositionLocalsForPreview(darkMode = darkMode) {
                        CompositionLocalProvider(LocalDensity provides Density(1f)) {
                            content()
                        }
                    }
                }
                waitForIdle()
                beforeCapture()
                waitForIdle()
                val png = Image.makeFromBitmap(captureToImage().asSkiaBitmap())
                    .encodeToData(EncodedImageFormat.PNG)?.bytes ?: error("Failed to encode screenshot $name")
                val file = File(outDir, "$name.png")
                file.writeBytes(png)
                assertTrue(file.length() > 0, "Screenshot $name should not be empty")
            }
        } finally {
            Locale.setDefault(defaultLocale)
        }
    }

    @Test
    fun mobile412() = capture(412, 780, "bangumi-merge-mobile-412")

    @Test
    fun mobileSmall320() = capture(320, 780, "bangumi-merge-mobile-320")

    @Test
    fun desktop960() = capture(960, 720, "bangumi-merge-desktop-960")

    @Test
    fun ultraWide1680() = capture(1680, 620, "bangumi-merge-ultrawide-1680")

    @Test
    fun synced412() = capture(
        412, 780, "bangumi-merge-synced-412",
        state = createTestBangumiMergeUiState(
            now,
            choices = emptyMap(),
            mergeState = createTestBangumiMergeSyncedState(now),
        ),
    )

    @Test
    fun syncing412() = capture(
        412, 780, "bangumi-merge-syncing-412",
        state = createTestBangumiMergeUiState(
            now,
            choices = emptyMap(),
            // 首次绑定: 服务端尚未完成同步, 空状态展示 "正在同步收藏" 标题与说明而不是 "已同步"
            mergeState = createTestBangumiMergeSyncedState(now).copy(lastSyncedAt = null),
        ),
    )

    // ─── 其他状态 (412dp) ────────────────────────────────────────────────

    private fun allResolvedChoices() = createTestBangumiMergeState(now).conflicts
        .flatMap { it.conflictKeys }
        .withIndex()
        .associate { (i, key) -> key to if (i % 2 == 0) BangumiMergeSide.BANGUMI else BangumiMergeSide.ANIMEKO }

    @Test
    fun mobile412Unselected() = capture(
        412, 780, "bangumi-merge-mobile-412-unselected",
        state = createTestBangumiMergeUiState(now, choices = emptyMap()),
    )

    @Test
    fun mobile412AllResolved() = capture(
        412, 780, "bangumi-merge-mobile-412-all-resolved",
        state = createTestBangumiMergeUiState(now, choices = allResolvedChoices()),
    )

    @Test
    fun mobile412Applying() = capture(
        412, 780, "bangumi-merge-mobile-412-applying",
        state = createTestBangumiMergeUiState(now, choices = allResolvedChoices()).copy(isApplying = true),
    )

    @Test
    fun mobile412AutoMergedExpanded() = capture(
        412, 780, "bangumi-merge-mobile-412-auto-merged-expanded",
        beforeCapture = {
            onNodeWithTag(BangumiMergeTestTags.AUTO_MERGED_TOGGLE).performScrollTo().performClick()
            waitForIdle()
            onNodeWithTag(BangumiMergeTestTags.AUTO_MERGED_MORE).performScrollTo()
        },
    )

    @Test
    fun mobile412SyncInProgressNotice() = capture(
        412, 780, "bangumi-merge-mobile-412-sync-in-progress",
        state = createTestBangumiMergeUiState(
            now,
            mergeState = createTestBangumiMergeState(now).copy(syncInProgress = true),
        ),
    )

    @Test
    fun mobile412Loading() = capture(
        412, 780, "bangumi-merge-mobile-412-loading",
        state = BangumiMergeUiState(
            isLoading = true,
            loadError = null,
            mergeState = null,
            groups = emptyList(),
            choices = emptyMap(),
            isApplying = false,
        ),
    )

    @Test
    fun mobile412LoadError() = capture(
        412, 780, "bangumi-merge-mobile-412-load-error",
        state = BangumiMergeUiState(
            isLoading = false,
            loadError = LoadError.NetworkError,
            mergeState = null,
            groups = emptyList(),
            choices = emptyMap(),
            isApplying = false,
        ),
    )

    @Test
    fun synced412Expanded() = capture(
        412, 780, "bangumi-merge-synced-412-expanded",
        state = createTestBangumiMergeUiState(
            now,
            choices = emptyMap(),
            mergeState = createTestBangumiMergeSyncedState(now),
        ),
        beforeCapture = {
            onNodeWithTag(BangumiMergeTestTags.AUTO_MERGED_TOGGLE).performClick()
        },
    )

    // ─── 深色主题 ────────────────────────────────────────────────────────

    @Test
    fun mobile412Dark() = capture(412, 780, "bangumi-merge-mobile-412-dark", darkMode = DarkMode.DARK)

    @Test
    fun desktop960Dark() = capture(960, 720, "bangumi-merge-desktop-960-dark", darkMode = DarkMode.DARK)

    @Test
    fun synced412Dark() = capture(
        412, 780, "bangumi-merge-synced-412-dark",
        state = createTestBangumiMergeUiState(
            now,
            choices = emptyMap(),
            mergeState = createTestBangumiMergeSyncedState(now),
        ),
        darkMode = DarkMode.DARK,
        beforeCapture = {
            onNodeWithTag(BangumiMergeTestTags.AUTO_MERGED_TOGGLE).performClick()
        },
    )

    // ─── 桌面 1280dp: 表格封顶 1080dp 居中 ───────────────────────────────

    @Test
    fun desktop1280() = capture(1280, 720, "bangumi-merge-desktop-1280")

    // ─── 主界面的冲突提示条 ──────────────────────────────────────────────

    @Test
    fun notifier412() = captureContent(412, 200, "bangumi-merge-notifier-412") {
        Box(Modifier.fillMaxSize()) {
            BangumiConflictNotifierContent(conflictCount = 6, onResolveClick = {})
        }
    }
}
