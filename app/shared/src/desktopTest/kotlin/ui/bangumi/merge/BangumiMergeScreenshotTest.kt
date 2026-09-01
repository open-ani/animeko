/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.bangumi.merge

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import java.util.Locale
import kotlin.test.Test
import kotlin.time.Instant

/**
 * 合并收藏界面在设计稿四个断点下的截图, 输出到 `build/screenshots` 供人工/Figma 对照:
 * - 412dp 移动
 * - 320dp 小屏 (双行单元格)
 * - 960dp 桌面表格
 * - 1680dp 超宽双栏
 */
@OptIn(TestOnly::class, ExperimentalTestApi::class)
class BangumiMergeScreenshotTest {
    private val outDir: File =
        File(System.getProperty("ani.screenshot.out") ?: "build/screenshots").also { it.mkdirs() }

    private val now = Instant.fromEpochMilliseconds(1_753_000_000_000)

    private fun capture(widthDp: Int, heightDp: Int, name: String) {
        val defaultLocale = Locale.getDefault()
        Locale.setDefault(Locale.SIMPLIFIED_CHINESE)
        try {
            runSkikoComposeUiTest(Size(widthDp.toFloat(), heightDp.toFloat()), density = Density(1f)) {
                setContent {
                    ProvideCompositionLocalsForPreview {
                        CompositionLocalProvider(LocalDensity provides Density(1f)) {
                            BangumiMergeScreen(
                                state = createTestBangumiMergeUiState(now),
                                onSelect = { _, _ -> },
                                onAdoptNewer = {},
                                onSelectAll = {},
                                onApply = {},
                                onRetry = {},
                                onNavigateBack = {},
                                getTimeNow = { now },
                            )
                        }
                    }
                }
                waitForIdle()
                val png = Image.makeFromBitmap(captureToImage().asSkiaBitmap())
                    .encodeToData(EncodedImageFormat.PNG)?.bytes ?: error("Failed to encode screenshot $name")
                File(outDir, "$name.png").writeBytes(png)
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
}
