/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.io.files.Path
import me.him188.ani.app.data.models.preference.DarkMode
import me.him188.ani.app.ui.foundation.DragAndDropContent
import me.him188.ani.app.ui.foundation.LocalWindowDropHandlerRegistry
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.WindowDropHandlerEffect
import me.him188.ani.app.ui.foundation.WindowDropHandlerRegistry
import me.him188.ani.app.ui.foundation.WindowDropHost
import me.him188.ani.app.ui.foundation.WindowDropHostState
import me.him188.ani.app.ui.foundation.WindowDropTestTags
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.media_selector_summary_dropped_file
import me.him188.ani.app.ui.mediaselect.summary.MediaSelectorSourceSummary
import me.him188.ani.app.ui.mediaselect.summary.MediaSelectorSummary
import me.him188.ani.app.ui.mediaselect.summary.MediaSelectorSummaryCard
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import java.util.Locale
import kotlin.test.Test

/**
 * 无后端的确定性截图: 渲染播放页「拖入本地视频文件以播放」的各个状态, 导出 PNG.
 *
 * 提示层由真实的 [EpisodeVideoDropHandler] 通过 [WindowDropHandlerEffect] 注册后产生, 背景是播放页的示意布局.
 * 输出目录默认为模块 build/screenshots.
 */
@OptIn(TestOnly::class, ExperimentalTestApi::class)
class EpisodeVideoDropScreenshotTest {
    private val outDir: File =
        File(System.getProperty("ani.screenshot.out") ?: "build/screenshots").also { it.mkdirs() }

    private fun capture(
        name: String,
        darkMode: DarkMode,
        width: Int = 1280,
        height: Int = 720,
        content: @Composable () -> Unit,
        body: SkikoComposeUiTest.() -> Unit = {},
    ) {
        val previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.SIMPLIFIED_CHINESE)
        try {
            runSkikoComposeUiTest(Size(width.toFloat(), height.toFloat()), density = Density(1f)) {
                setContent {
                    ProvideCompositionLocalsForPreview(darkMode = darkMode) {
                        CompositionLocalProvider(LocalDensity provides Density(1f)) {
                            Surface(color = MaterialTheme.colorScheme.surface) {
                                content()
                            }
                        }
                    }
                }
                waitForIdle()
                body()
                waitForIdle()
                val png = Image.makeFromBitmap(captureToImage().asSkiaBitmap())
                    .encodeToData(EncodedImageFormat.PNG)
                    ?.bytes
                    ?: error("Failed to encode screenshot $name")
                File(outDir, "$name.png").writeBytes(png)
            }
        } finally {
            Locale.setDefault(previousLocale)
        }
    }

    /**
     * 在播放页的示意布局上拖入 [dragged], 处理者经由 [WindowDropHandlerEffect] 注册.
     */
    private fun captureDrop(name: String, darkMode: DarkMode, dragged: String, expectedText: String) {
        val state = WindowDropHostState()
        var registry: WindowDropHandlerRegistry? = null
        capture(
            name, darkMode,
            content = {
                WindowDropHost(emptyList(), Modifier.fillMaxSize(), state) {
                    registry = LocalWindowDropHandlerRegistry.current
                    WindowDropHandlerEffect(rememberEpisodeVideoDropHandler {})
                    PlayerPageBackdrop()
                }
            },
        ) {
            state.onDragStarted(DragAndDropContent.FileList(listOf(Path("/Users/ani/Movies/$dragged"))), registry!!.handlers)
            waitForIdle()
            onNodeWithTag(WindowDropTestTags.OVERLAY).assertIsDisplayed()
            onNodeWithText(expectedText, substring = true).assertIsDisplayed()
        }
    }

    @Test
    fun dropVideoDark() = captureDrop(
        "episode-drop-video-dark", DarkMode.DARK,
        dragged = "[Sakurato] Sousou no Frieren [01][AVC-8bit 1080p AAC][CHS].mp4",
        expectedText = "松手以播放",
    )

    @Test
    fun dropVideoLight() = captureDrop(
        "episode-drop-video-light", DarkMode.LIGHT,
        dragged = "[Sakurato] Sousou no Frieren [01][AVC-8bit 1080p AAC][CHS].mp4",
        expectedText = "松手以播放",
    )

    @Test
    fun dropUnsupportedDark() = captureDrop(
        "episode-drop-unsupported-dark", DarkMode.DARK,
        dragged = "字幕.ass",
        expectedText = "视频文件",
    )

    @Test
    fun dropUnsupportedLight() = captureDrop(
        "episode-drop-unsupported-light", DarkMode.LIGHT,
        dragged = "字幕.ass",
        expectedText = "视频文件",
    )

    private fun captureSummary(name: String, darkMode: DarkMode) = capture(name, darkMode, width = 420, height = 200, content = {
        Box(Modifier.padding(16.dp)) {
            MediaSelectorSummaryCard(
                MediaSelectorSummary.Selected(
                    source = MediaSelectorSourceSummary(
                        sourceName = stringResource(Lang.media_selector_summary_dropped_file),
                        sourceIconUrl = "",
                    ),
                    mediaTitle = "[Sakurato] Sousou no Frieren [01][AVC-8bit 1080p AAC][CHS].mp4",
                    isPerfectMatch = false,
                ),
                onClickManualSelect = {},
                Modifier.width(388.dp),
            )
        }
    })

    @Test
    fun summaryDark() = captureSummary("episode-drop-summary-dark", DarkMode.DARK)

    @Test
    fun summaryLight() = captureSummary("episode-drop-summary-light", DarkMode.LIGHT)
}

/**
 * 播放页的示意布局: 上方 16:9 的视频区域, 下方是详情区域的底色.
 */
@Composable
private fun PlayerPageBackdrop() {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth(0.72f).aspectRatio(16f / 9f).background(Color.Black))
    }
}
