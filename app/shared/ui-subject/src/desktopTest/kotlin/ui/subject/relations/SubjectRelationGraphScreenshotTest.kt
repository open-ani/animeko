/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.relations

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import me.him188.ani.app.data.models.preference.DarkMode
import me.him188.ani.app.data.models.subject.SubjectRelationGraph
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * 无后端的确定性截图: 用真实系列的关系图数据渲染手机和宽屏两种布局, 导出 PNG 供人工核对.
 *
 * 输出目录默认为模块 build/screenshots.
 */
@OptIn(TestOnly::class, ExperimentalTestApi::class)
class SubjectRelationGraphScreenshotTest {
    private val outDir: File =
        File(System.getProperty("ani.screenshot.out") ?: "build/screenshots").also { it.mkdirs() }
    private val originalLocale = Locale.getDefault()

    // 测试数据是中文条目, 因此用中文界面截图
    @BeforeTest
    fun setLocale() = Locale.setDefault(Locale.SIMPLIFIED_CHINESE)

    @AfterTest
    fun restoreLocale() = Locale.setDefault(originalLocale)

    private fun capture(graph: SubjectRelationGraph, widthDp: Int, heightDp: Int, name: String, isDark: Boolean = false) {
        // density=2 让导出的图片在 PR 中清晰
        val density = Density(2f)
        runSkikoComposeUiTest(Size(widthDp * density.density, heightDp * density.density), density = density) {
            setContent {
                ProvideCompositionLocalsForPreview(if (isDark) DarkMode.DARK else DarkMode.LIGHT) {
                    CompositionLocalProvider(LocalDensity provides density) {
                        SubjectRelationGraphScreen(
                            SubjectRelationGraphUiState(graph, error = null),
                            onRetry = {},
                            onClickSubject = {},
                        )
                    }
                }
            }
            waitForIdle()
            val png = Image.makeFromBitmap(captureToImage().asSkiaBitmap())
                .encodeToData(EncodedImageFormat.PNG)
                ?.bytes
                ?: error("Failed to encode screenshot $name")
            File(outDir, "$name.png").writeBytes(png)
        }
    }

    @Test
    fun compactReZero() = capture(TestSubjectRelationGraphs.ReZero, 390, 1500, "relation-graph-compact-rezero")

    @Test
    fun compactFromOva() =
        capture(TestSubjectRelationGraphs.ReZeroFromOva, 390, 900, "relation-graph-compact-rezero-from-ova")

    @Test
    fun compactKimetsuDark() =
        capture(TestSubjectRelationGraphs.Kimetsu, 390, 1700, "relation-graph-compact-kimetsu-dark", isDark = true)

    @Test
    fun compactRailgun() = capture(TestSubjectRelationGraphs.Railgun, 390, 900, "relation-graph-compact-railgun")

    @Test
    fun compactCollapsed() =
        capture(TestSubjectRelationGraphs.manyBranches(6), 390, 800, "relation-graph-compact-collapsed")

    @Test
    fun wideReZero() = capture(TestSubjectRelationGraphs.ReZero, 1280, 720, "relation-graph-wide-rezero")

    @Test
    fun wideKimetsuDark() =
        capture(TestSubjectRelationGraphs.Kimetsu, 1280, 600, "relation-graph-wide-kimetsu-dark", isDark = true)
}
