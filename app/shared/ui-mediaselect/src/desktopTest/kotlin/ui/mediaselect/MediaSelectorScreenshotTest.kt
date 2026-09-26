/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:OptIn(TestOnly::class, ExperimentalTestApi::class)

package me.him188.ani.app.ui.mediaselect

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.data.models.preference.DarkMode
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.data.repository.media.ManualBrowseMemory
import me.him188.ani.app.domain.media.createTestDefaultMedia
import me.him188.ani.app.domain.media.createTestMediaProperties
import me.him188.ani.app.domain.media.fetch.MediaSourceFetchResult
import me.him188.ani.app.domain.media.fetch.MediaSourceFetchState
import me.him188.ani.app.domain.media.selector.DefaultMediaSelector
import me.him188.ani.app.domain.media.selector.MediaSelectorContext
import me.him188.ani.app.domain.mediasource.instance.createTestMediaSourceInstance
import me.him188.ani.app.domain.mediasource.web.PageEvaluator
import me.him188.ani.app.domain.mediasource.web.PageExpectation
import me.him188.ani.app.domain.mediasource.web.SolveRequest
import me.him188.ani.app.domain.mediasource.web.WebCaptchaKind
import me.him188.ani.app.domain.mediasource.web.captcha.CaptchaBrowser
import me.him188.ani.app.domain.mediasource.web.captcha.CaptchaBrowserFactory
import me.him188.ani.app.domain.mediasource.web.captcha.WebSessionManager
import me.him188.ani.app.domain.mediasource.web.captcha.WebSourceCookieJar
import me.him188.ani.app.domain.mediasource.web.captcha.WebSourceIdentityRegistry
import me.him188.ani.app.domain.mediasource.web.captcha.createTestWebSessionManager
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.layout.Zero
import me.him188.ani.app.ui.foundation.rememberBackgroundScope
import me.him188.ani.app.ui.framework.assertScreenshot
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.media_selector_mode_manual
import me.him188.ani.app.ui.lang.media_selector_sources
import me.him188.ani.app.ui.lang.subject_episode_close_selector
import me.him188.ani.app.ui.mediafetch.MediaSelectorState
import me.him188.ani.app.ui.mediafetch.MediaSourceResultListPresentation
import me.him188.ani.app.ui.mediafetch.MediaSourceResultPresentation
import me.him188.ani.app.ui.mediafetch.TestBrowsableMediaSource
import me.him188.ani.app.ui.mediafetch.createTestMediaSourceInfoProvider
import me.him188.ani.app.ui.mediaselect.auto.AutoMatchPage
import me.him188.ani.app.ui.mediaselect.auto.AutoMatchPageTestTags
import me.him188.ani.app.ui.mediaselect.bt.BtResourcesPage
import me.him188.ani.app.ui.mediaselect.bt.BtResourcesPageTestTags
import me.him188.ani.app.ui.mediaselect.common.MediaSelectorChromeTestTags
import me.him188.ani.app.ui.mediaselect.common.MediaSelectorModeChip
import me.him188.ani.app.ui.mediaselect.manual.ManualBrowsePage
import me.him188.ani.app.ui.mediaselect.manual.ManualBrowsePageTestTags
import me.him188.ani.app.ui.mediaselect.manual.ManualBrowseState
import me.him188.ani.app.ui.mediaselect.manual.ManualBrowseTarget
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.EpisodeType
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.SubtitleKind
import me.him188.ani.datasources.api.source.MediaFetchRequest
import me.him188.ani.datasources.api.source.MediaSourceInfo
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.source.MediaSourceLocation
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.datasources.api.topic.FileSize
import me.him188.ani.datasources.api.topic.FileSize.Companion.gigaBytes
import me.him188.ani.datasources.api.topic.FileSize.Companion.megaBytes
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.datasources.api.topic.SubtitleLanguage
import me.him188.ani.datasources.mikan.MikanMediaSource
import me.him188.ani.utils.ktor.asScopedHttpClient
import me.him188.ani.utils.ktor.createDefaultHttpClient
import me.him188.ani.utils.platform.annotations.TestOnly
import me.him188.ani.utils.platform.currentPlatform
import me.him188.ani.utils.platform.isMacOS
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 三种模式页面的截图与交互测试.
 *
 * 场景尺寸即容器尺寸 (density 1, px == dp): 模式菜单与底部面板是窗口级 Popup, 按窗口边界定位, 窗口必须和手机画板一样窄它们才按真实位置入镜.
 * 每张截图导出到 `build/screenshots/media-selector` 目录供人工核对; 基线 PNG 放在 `resources/screenshots/media-selector` 目录, 在 macOS 上生成,
 * 也只在 macOS 上逐字节比较 (其他系统的字体与栅格化不同).
 * 文案固定为简体中文, 时区固定 UTC, 夹具不含查询中 / 限流中的源, 主题固定浅色.
 */
class MediaSelectorScreenshotTest {
    private fun SkikoComposeUiTest.awaitTag(tag: String) {
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `auto page lists web sources with the rescue card`() = runMediaSelectorScene(360, 640) {
        var rescueClicks = 0
        var modeChanged: MediaSelectorMode? = null
        lateinit var state: MediaSelectorState
        setMediaSelectorFrame {
            val scope = rememberBackgroundScope()
            state = remember { MediaSelectorScreenshotFixtures.createAutoState(scope.backgroundScope) }
            Column(Modifier.fillMaxWidth()) {
                SheetTitleRow(MediaSelectorMode.AUTO, onModeChange = { modeChanged = it })
                AutoMatchPage(
                    state,
                    onClickItem = {},
                    onRestartSource = {},
                    onRequestManualSearch = { rescueClicks++ },
                    Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                )
            }
        }
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("线路1")).fetchSemanticsNodes().isNotEmpty() }
        runOnIdle { state.select(MediaSelectorScreenshotFixtures.webMediaSelected) }
        waitUntil(timeoutMillis = 10_000) {
            onAllNodes(hasText(MediaSelectorScreenshotFixtures.webMediaSelected.properties.alliance) and isSelected())
                .fetchSemanticsNodes().isNotEmpty()
        }
        assertFrame("auto-page")

        onNodeWithTag(AutoMatchPageTestTags.RESCUE_CARD).performClick()
        runOnIdle { assertEquals(1, rescueClicks) }
        assertNull(modeChanged)
    }

    @Test
    fun `mode menu opens from the title row and reports the chosen mode`() = runMediaSelectorScene(360, 320) {
        var modeChanged: MediaSelectorMode? = null
        setMediaSelectorFrame {
            val scope = rememberBackgroundScope()
            val state = remember { MediaSelectorScreenshotFixtures.createAutoState(scope.backgroundScope) }
            Column(Modifier.fillMaxWidth()) {
                SheetTitleRow(MediaSelectorMode.AUTO, onModeChange = { modeChanged = it })
                AutoMatchPage(
                    state,
                    onClickItem = {},
                    onRestartSource = {},
                    onRequestManualSearch = {},
                    Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                )
            }
        }
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("线路1")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag(MediaSelectorChromeTestTags.MODE_CHIP).performClick()
        for (mode in MediaSelectorMode.entries) {
            onNodeWithTag(MediaSelectorChromeTestTags.modeItem(mode)).assertIsDisplayed()
        }
        assertFrame("mode-menu")

        onNodeWithTag(MediaSelectorChromeTestTags.modeItem(MediaSelectorMode.MANUAL)).performClick()
        runOnIdle { assertEquals(MediaSelectorMode.MANUAL, modeChanged) }
        onNodeWithTag(MediaSelectorChromeTestTags.modeItem(MediaSelectorMode.MANUAL)).assertDoesNotExist()
    }

    @Test
    fun `manual search then episodes in the stacked layout`() = runMediaSelectorScene(360, 640) {
        val plays = mutableListOf<ManualBrowseMemory?>()
        var played = 0
        lateinit var state: ManualBrowseState
        setMediaSelectorFrame {
            val scope = rememberBackgroundScope()
            state = remember {
                MediaSelectorScreenshotFixtures.createManualState(scope.backgroundScope) { _, memory -> plays += memory }
            }
            ManualBrowsePage(
                state,
                MediaSelectorScreenshotFixtures.watching,
                onPlayed = { played++ },
                topBar = { SheetTitleRow(MediaSelectorMode.MANUAL, onModeChange = {}) },
                Modifier.fillMaxSize(),
            )
        }
        // 不经搜索框提交: 聚焦后的光标闪烁会让截图不稳定.
        runOnIdle { state.search() }
        awaitTag(ManualBrowsePageTestTags.result(0))
        assertFrame("manual-search")

        onNodeWithTag(ManualBrowsePageTestTags.result(0)).performClick()
        awaitTag(ManualBrowsePageTestTags.episode(0))
        onNodeWithTag(ManualBrowsePageTestTags.episode(0)).onParent()
            .performScrollToNode(hasTestTag(ManualBrowsePageTestTags.episode(OVA_INDEX)))
        onNodeWithTag(ManualBrowsePageTestTags.episode(OVA_INDEX)).performClick()
        onNodeWithTag(ManualBrowsePageTestTags.episode(OVA_INDEX)).assertIsSelected()
        assertFrame("manual-episodes")

        onNodeWithTag(ManualBrowsePageTestTags.PLAY_REMEMBER).performClick()
        waitUntil(timeoutMillis = 10_000) { played == 1 }
        runOnIdle {
            val memory = assertNotNull(plays.single())
            assertEquals(OVA_INDEX, memory.episodeIndex)
            assertEquals(EpisodeSort(25), memory.playedAsSort)
        }
    }

    @Test
    fun `manual two pane layout plays temporarily`() = runMediaSelectorScene(844, 390) {
        val plays = mutableListOf<ManualBrowseMemory?>()
        var played = 0
        lateinit var state: ManualBrowseState
        setMediaSelectorFrame {
            val scope = rememberBackgroundScope()
            state = remember {
                MediaSelectorScreenshotFixtures.createManualState(scope.backgroundScope) { _, memory -> plays += memory }
            }
            ManualBrowsePage(
                state,
                MediaSelectorScreenshotFixtures.watching,
                onPlayed = { played++ },
                topBar = { SheetTitleRow(MediaSelectorMode.MANUAL, onModeChange = {}) },
                Modifier.fillMaxSize(),
                closeButton = { CloseSelectorButton() },
                inlineTitle = {
                    CloseSelectorButton()
                    Text(stringResource(Lang.media_selector_mode_manual), style = MaterialTheme.typography.titleLarge)
                },
            )
        }
        runOnIdle { state.search() }
        awaitTag(ManualBrowsePageTestTags.result(0))
        onNodeWithTag(ManualBrowsePageTestTags.result(0)).performClick()
        awaitTag(ManualBrowsePageTestTags.episode(OVA_INDEX))
        onNodeWithTag(ManualBrowsePageTestTags.episode(OVA_INDEX)).performClick()
        onNodeWithTag(ManualBrowsePageTestTags.BACK).assertDoesNotExist()
        assertFrame("manual-two-pane")

        onNodeWithTag(ManualBrowsePageTestTags.PLAY_TEMPORARY).performClick()
        waitUntil(timeoutMillis = 10_000) { played == 1 }
        runOnIdle { assertNull(plays.single()) }
    }

    @Test
    fun `bt compact list toggles the episode chip and reveals excluded rows`() = runMediaSelectorScene(360, 640) {
        lateinit var state: MediaSelectorState
        var clicked: Media? = null
        setMediaSelectorFrame {
            val scope = rememberBackgroundScope()
            state = remember { MediaSelectorScreenshotFixtures.createBtState(scope.backgroundScope) }
            Column(Modifier.fillMaxSize()) {
                SheetTitleRow(MediaSelectorMode.BT, onModeChange = {})
                BtPage(state, onClickItem = { clicked = it }, Modifier.fillMaxWidth())
            }
        }
        waitUntil(timeoutMillis = 10_000) {
            onNodeWithTag(BtResourcesPageTestTags.row(MediaSelectorScreenshotFixtures.btMediaSelected.mediaId)).isDisplayed()
        }
        onNodeWithTag(BtResourcesPageTestTags.COMPACT_LIST).assertExists()
        onNodeWithTag(BtResourcesPageTestTags.row(MediaSelectorScreenshotFixtures.btMediaOtherEpisode.mediaId)).assertDoesNotExist()
        assertFrame("bt-compact")

        onNodeWithTag(BtResourcesPageTestTags.EPISODE_CHIP).performClick()
        runOnIdle { assertFalse(state.btFilterState.episodeFilterEnabled.value) }
        awaitTag(BtResourcesPageTestTags.row(MediaSelectorScreenshotFixtures.btMediaOtherEpisode.mediaId))

        onNodeWithTag(BtResourcesPageTestTags.SHOW_EXCLUDED).performClick()
        onNodeWithTag(BtResourcesPageTestTags.SHOW_EXCLUDED).assertDoesNotExist()
        onNodeWithTag(BtResourcesPageTestTags.COMPACT_LIST)
            .performScrollToNode(hasTestTag(BtResourcesPageTestTags.row(MediaSelectorScreenshotFixtures.btMediaWithoutSubtitle.mediaId)))
        onNodeWithTag(BtResourcesPageTestTags.row(MediaSelectorScreenshotFixtures.btMediaWithoutSubtitle.mediaId)).assertExists()

        onNodeWithTag(BtResourcesPageTestTags.COMPACT_LIST)
            .performScrollToNode(hasTestTag(BtResourcesPageTestTags.row(MediaSelectorScreenshotFixtures.btMediaSelected.mediaId)))
        onNodeWithTag(BtResourcesPageTestTags.row(MediaSelectorScreenshotFixtures.btMediaSelected.mediaId)).performClick()
        runOnIdle { assertEquals(MediaSelectorScreenshotFixtures.btMediaSelected.mediaId, clicked?.mediaId) }
    }

    @Test
    fun `bt table shows excluded rows inline`() = runMediaSelectorScene(960, 700) {
        setMediaSelectorFrame {
            val scope = rememberBackgroundScope()
            val state = remember { MediaSelectorScreenshotFixtures.createBtState(scope.backgroundScope) }
            Column(Modifier.fillMaxSize()) {
                DialogTopBar(MediaSelectorMode.BT, onModeChange = {})
                BtPage(state, onClickItem = {}, Modifier.fillMaxWidth())
            }
        }
        waitUntil(timeoutMillis = 10_000) {
            onNodeWithTag(BtResourcesPageTestTags.row(MediaSelectorScreenshotFixtures.btMediaSelected.mediaId)).isDisplayed()
        }
        onNodeWithTag(BtResourcesPageTestTags.TABLE).assertExists()
        onNodeWithTag(BtResourcesPageTestTags.COMPACT_LIST).assertDoesNotExist()
        onNodeWithTag(BtResourcesPageTestTags.FILTER_CHIP).assertDoesNotExist()

        onNodeWithTag(BtResourcesPageTestTags.SHOW_EXCLUDED).performClick()
        onNodeWithTag(BtResourcesPageTestTags.SHOW_EXCLUDED).assertDoesNotExist()
        awaitTag(BtResourcesPageTestTags.row(MediaSelectorScreenshotFixtures.btMediaWithoutSubtitle.mediaId))
        assertTrue(
            onAllNodesWithTag(BtResourcesPageTestTags.row(MediaSelectorScreenshotFixtures.btMediaOtherEpisode.mediaId))
                .fetchSemanticsNodes().isNotEmpty(),
        )
        assertFrame("bt-table")
    }

    private companion object {
        /**
         * 线路 1 的 "OVA" 在 01..24 之后.
         */
        const val OVA_INDEX = 24
    }
}

///////////////////////////////////////////////////////////////////////////
// 场景
///////////////////////////////////////////////////////////////////////////

internal const val MEDIA_SELECTOR_FRAME = "media_selector_frame"

/**
 * 以 [width] × [height] (px == dp) 的窗口运行一个场景. 文案固定简体中文.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("DEPRECATION")
internal fun runMediaSelectorScene(width: Int, height: Int, body: SkikoComposeUiTest.() -> Unit) {
    val previousLocale = Locale.getDefault()
    Locale.setDefault(Locale.SIMPLIFIED_CHINESE)
    try {
        // 与 runAniComposeUiTest 相同: 否则与其他协程测试同跑时 lifecycle 的 MainDispatcherChecker 会一直阻塞.
        Dispatchers.resetMain()
        runSkikoComposeUiTest(Size(width.toFloat(), height.toFloat()), density = Density(1f)) {
            body()
        }
    } finally {
        Locale.setDefault(previousLocale)
    }
}

/**
 * 浅色主题, 容器色与 ModalBottomSheet / MediaSelectorDialog 相同.
 */
internal fun SkikoComposeUiTest.setMediaSelectorFrame(content: @Composable () -> Unit) {
    setContent {
        ProvideCompositionLocalsForPreview(darkMode = DarkMode.LIGHT) {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Surface(
                    Modifier.fillMaxSize().testTag(MEDIA_SELECTOR_FRAME),
                    color = BottomSheetDefaults.ContainerColor,
                ) {
                    content()
                }
            }
        }
    }
}

/**
 * 等待空闲后截取整个窗口.
 */
internal fun SkikoComposeUiTest.captureFrame(): ImageBitmap {
    waitForIdle()
    return captureToImage()
}

internal fun ImageBitmap.encodeToPng(): ByteArray =
    Image.makeFromBitmap(asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)?.bytes
        ?: error("Could not encode screenshot as PNG")

internal val screenshotExportDir: File
    get() = File(System.getProperty("ani.screenshot.out") ?: "build/screenshots", "media-selector").also { it.mkdirs() }

/**
 * 基线 PNG 的生成环境. 字体栅格化随 macOS 大版本变化, 逐字节比较只在同一大版本的 macOS 上进行;
 * 其他环境只导出到 [screenshotExportDir] 供人工核对. 升级基线时同步改这里.
 */
private const val BASELINE_MACOS_MAJOR_VERSION = "26"

private val isBaselineEnvironment: Boolean
    get() = currentPlatform().isMacOS() &&
            System.getProperty("os.version").orEmpty().substringBefore('.') == BASELINE_MACOS_MAJOR_VERSION

/**
 * 导出到 [screenshotExportDir], 并在与基线相同的环境上逐字节比较.
 */
private fun SkikoComposeUiTest.assertFrame(name: String) {
    val image = captureFrame()
    File(screenshotExportDir, "$name.png").writeBytes(image.encodeToPng())
    if (isBaselineEnvironment) {
        image.assertScreenshot("/screenshots/media-selector/$name.png")
    }
}

/**
 * 窄屏底部弹窗的标题行: 「数据源」+ 模式 chip.
 */
@Composable
internal fun SheetTitleRow(
    mode: MediaSelectorMode,
    onModeChange: (MediaSelectorMode) -> Unit,
    showBt: Boolean = true,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(Lang.media_selector_sources),
            Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
        )
        MediaSelectorModeChip(mode, onModeChange, showBt = showBt)
    }
}

/**
 * 居中对话框的顶栏: 「数据源」+ 模式 chip + 关闭.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DialogTopBar(
    mode: MediaSelectorMode,
    onModeChange: (MediaSelectorMode) -> Unit,
) {
    TopAppBar(
        title = { Text(stringResource(Lang.media_selector_sources)) },
        actions = {
            MediaSelectorModeChip(mode, onModeChange, showBt = true)
            CloseSelectorButton()
        },
        windowInsets = WindowInsets.Zero,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = BottomSheetDefaults.ContainerColor),
    )
}

@Composable
internal fun CloseSelectorButton() {
    IconButton(onClick = {}) {
        Icon(Icons.Rounded.Close, contentDescription = stringResource(Lang.subject_episode_close_selector))
    }
}

@Composable
internal fun BtPage(
    state: MediaSelectorState,
    onClickItem: (Media) -> Unit,
    modifier: Modifier = Modifier,
) {
    BtResourcesPage(
        state,
        MediaSelectorScreenshotFixtures.btSourceResults,
        MediaSelectorScreenshotFixtures.watching,
        MediaSelectorScreenshotFixtures.fetchRequest,
        onFetchRequestChange = {},
        onClickItem = onClickItem,
        onRestartSource = {},
        modifier,
        timeZone = TimeZone.UTC,
    )
}

///////////////////////////////////////////////////////////////////////////
// 夹具: 条目「命运石之门」第 25 话 OVA
///////////////////////////////////////////////////////////////////////////

internal object MediaSelectorScreenshotFixtures {
    private const val SUBJECT_NAME = "命运石之门"

    val watching = WatchingEpisode("25", "OVA")

    val target = ManualBrowseTarget(
        subjectId = 1,
        subjectName = SUBJECT_NAME,
        episodeSort = EpisodeSort(25),
        episodeSortText = "25",
    )

    val fetchRequest = MediaFetchRequest(
        subjectId = "1",
        episodeId = "25",
        subjectNameCN = SUBJECT_NAME,
        subjectNames = listOf(SUBJECT_NAME, "STEINS;GATE"),
        episodeSort = EpisodeSort(25),
        episodeName = "OVA",
        episodeEp = EpisodeSort(25),
    )

    private val selectorContext = MediaSelectorContext.EmptyForPreview.copy(
        subjectInfo = SubjectInfo.Empty.copy(subjectId = 1, name = "STEINS;GATE", nameCn = SUBJECT_NAME),
        episodeInfo = EpisodeInfo(
            episodeId = 25,
            type = EpisodeType.MainStory,
            name = "OVA",
            nameCn = "OVA",
            sort = EpisodeSort(25),
            ep = EpisodeSort(25),
        ),
    )

    // region 自动匹配页

    private const val SOURCE_A = "source-a"
    private const val SOURCE_B = "source-b"
    private const val SOURCE_C = "source-c"
    private const val SOURCE_D = "source-d"

    private fun webMedia(sourceId: String, channel: String): Media = createTestDefaultMedia(
        mediaId = "$sourceId.$channel",
        mediaSourceId = sourceId,
        originalUrl = "https://example.com/$sourceId/$channel",
        download = ResourceLocation.WebVideo("https://example.com/$sourceId/$channel/play"),
        originalTitle = "$SUBJECT_NAME OVA",
        publishedTime = 0,
        properties = createTestMediaProperties(
            subjectName = SUBJECT_NAME,
            episodeName = "OVA",
            subtitleLanguageIds = listOf(SubtitleLanguage.ChineseSimplified.id),
            resolution = "1080P",
            alliance = channel,
            size = FileSize.Unspecified,
            subtitleKind = null,
        ),
        episodeRange = EpisodeRange.single(EpisodeSort(25)),
        location = MediaSourceLocation.Online,
        kind = MediaSourceKind.WEB,
    )

    private val webMediaList: List<Media> = listOf(
        webMedia(SOURCE_A, "线路1"),
        webMedia(SOURCE_A, "线路2"),
        webMedia(SOURCE_A, "线路3"),
        webMedia(SOURCE_B, "主线"),
        webMedia(SOURCE_B, "备用"),
    )

    /**
     * 自动页里选中的线路: 源 A 线路 2.
     */
    val webMediaSelected: Media get() = webMediaList[1]

    private val webFetchResults: List<MediaSourceFetchResult> = listOf(
        FixedFetchResult(SOURCE_A, MediaSourceInfo("源A"), MediaSourceKind.WEB, MediaSourceFetchState.Succeed(1), webMediaList),
        FixedFetchResult(SOURCE_B, MediaSourceInfo("源B"), MediaSourceKind.WEB, MediaSourceFetchState.Succeed(1), webMediaList),
        FixedFetchResult(
            SOURCE_C, MediaSourceInfo("源C"), MediaSourceKind.WEB,
            MediaSourceFetchState.Failed(IllegalStateException("network"), 1), emptyList(),
        ),
        FixedFetchResult(
            SOURCE_D, MediaSourceInfo("源D"), MediaSourceKind.WEB,
            MediaSourceFetchState.CaptchaRequired(
                SolveRequest(SOURCE_D, "https://example.com/$SOURCE_D", WebCaptchaKind.Cloudflare, PageExpectation.AnyContent),
                1,
            ),
            emptyList(),
        ),
    )

    /**
     * 源 A / B 各有线路, 源 C 查询失败, 源 D 需要验证码 (平台支持交互解决); 偏好源为 B.
     */
    fun createAutoState(backgroundScope: CoroutineScope): MediaSelectorState = createSelectorState(
        backgroundScope,
        mediaList = webMediaList,
        fetchResults = webFetchResults,
        preference = MediaPreference.Empty,
        preferredWebMediaSource = SOURCE_B,
        webSessionManager = createInteractiveWebSessionManager(backgroundScope),
    )

    // endregion

    // region BT 资源页

    private val CHS = SubtitleLanguage.ChineseSimplified.id
    private val CHT = SubtitleLanguage.ChineseTraditional.id
    private val JPN = SubtitleLanguage.Japanese.id

    private const val SOURCE_DMHY = "dmhy"
    private const val SOURCE_MOE = "moe"
    private const val SOURCE_ACG = "acg.rip"
    private const val SOURCE_RSS = "rss"

    private fun btMedia(
        id: String,
        sourceId: String,
        alliance: String,
        title: String,
        resolution: String,
        languages: List<String>,
        subtitleKind: SubtitleKind?,
        size: FileSize,
        published: LocalDate,
        range: EpisodeRange,
    ): Media = createTestDefaultMedia(
        mediaId = "$sourceId.$id",
        mediaSourceId = sourceId,
        originalUrl = "https://example.com/$sourceId/$id",
        download = ResourceLocation.MagnetLink("magnet:?xt=urn:btih:$id"),
        originalTitle = title,
        publishedTime = published.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(),
        properties = createTestMediaProperties(
            subjectName = null,
            episodeName = null,
            subtitleLanguageIds = languages,
            resolution = resolution,
            alliance = alliance,
            size = size,
            subtitleKind = subtitleKind,
        ),
        episodeRange = range,
        location = MediaSourceLocation.Online,
        kind = MediaSourceKind.BitTorrent,
    )

    private val ova25 = EpisodeRange.single(EpisodeSort(25))
    private val fullSeason = EpisodeRange.range(EpisodeSort(1), EpisodeSort(25))

    /**
     * 偏好 1080P + 简中. 主列表 5 条; 排除: 繁中 (低于偏好)、无字幕、第 24 话 (集数不符)、720P (低于偏好).
     */
    private val btMediaList: List<Media> = listOf(
        btMedia(
            "sakura-ova", MikanMediaSource.ID, "桜都字幕组",
            "命运石之门 / Steins;Gate [25] OVA [1080p][HEVC][简繁内封]",
            "1080P", listOf(CHS, CHT), SubtitleKind.CLOSED, 1.4.gigaBytes, LocalDate(2025, 9, 20), ova25,
        ),
        btMedia(
            "nekomoe-ova", MikanMediaSource.ID, "喵萌奶茶屋",
            "Steins;Gate - 25 OVA [WebRip 1080p HEVC-10bit AAC][简日双语]",
            "1080P", listOf(CHS, JPN), SubtitleKind.CLOSED, 1.1.gigaBytes, LocalDate(2025, 9, 20), ova25,
        ),
        btMedia(
            "ani-ova", SOURCE_DMHY, "ANi",
            "命運石之門 OVA - 25 [1080P][Baha][WEB-DL][AAC AVC][CHT]",
            "1080P", listOf(CHT), SubtitleKind.EMBEDDED, 640.megaBytes, LocalDate(2025, 9, 19), ova25,
        ),
        btMedia(
            "lolihouse-ova", SOURCE_DMHY, "LoliHouse",
            "Steins;Gate OVA [WebRip 1080p HEVC-10bit AAC ASSx2]",
            "1080P", listOf(CHS, CHT), SubtitleKind.EXTERNAL_PROVIDED, 1.6.gigaBytes, LocalDate(2025, 9, 19), ova25,
        ),
        btMedia(
            "chika-ova", MikanMediaSource.ID, "千夏字幕组",
            "[命运石之门][Steins;Gate][OVA][1080p_AVC][简体]",
            "1080P", listOf(CHS), SubtitleKind.CLOSED, 980.megaBytes, LocalDate(2025, 9, 18), ova25,
        ),
        btMedia(
            "vcb-fin", SOURCE_DMHY, "VCB-Studio",
            "Steins;Gate 10-bit 1080p HEVC BDRip [Fin]",
            "1080P", emptyList(), null, 28.4.gigaBytes, LocalDate(2025, 5, 2), fullSeason,
        ),
        btMedia(
            "sakura-fin", MikanMediaSource.ID, "桜都字幕组",
            "命运石之门 / Steins;Gate [01-25 Fin] [1080p][HEVC][简繁内封]",
            "1080P", listOf(CHS, CHT), SubtitleKind.CLOSED, 24.1.gigaBytes, LocalDate(2025, 4, 30), fullSeason,
        ),
        btMedia(
            "ani-24", SOURCE_MOE, "ANi",
            "命運石之門 - 24 [1080P][Baha][WEB-DL][AAC AVC][CHT]",
            "1080P", listOf(CHS, CHT), SubtitleKind.EMBEDDED, 620.megaBytes, LocalDate(2025, 9, 12),
            EpisodeRange.single(EpisodeSort(24)),
        ),
        btMedia(
            "sakura-ova-720", SOURCE_MOE, "桜都字幕组",
            "命运石之门 / Steins;Gate [25] OVA [720p][AVC][简繁内封]",
            "720P", listOf(CHS, CHT), SubtitleKind.CLOSED, 520.megaBytes, LocalDate(2025, 9, 20), ova25,
        ),
    )

    val btMediaSelected: Media get() = btMediaList[0]
    val btMediaWithoutSubtitle: Media get() = btMediaList[5]
    val btMediaOtherEpisode: Media get() = btMediaList[7]

    private fun btSource(id: String, info: MediaSourceInfo, state: MediaSourceFetchState) = MediaSourceResultPresentation(
        instanceId = id,
        mediaSourceId = id,
        state = state,
        info = info,
        kind = MediaSourceKind.BitTorrent,
        totalCount = 0,
        isPreferred = false,
    )

    /**
     * 数据源面板: 三个查询成功、一个禁用、一个失败; 没有查询中的源.
     */
    val btSourceResults = MediaSourceResultListPresentation(
        listOf(
            btSource(MikanMediaSource.ID, MikanMediaSource.INFO, MediaSourceFetchState.Succeed(1)),
            btSource(SOURCE_DMHY, MediaSourceInfo("动漫花园", iconResourceId = "dmhy.png"), MediaSourceFetchState.Succeed(1)),
            btSource(SOURCE_MOE, MediaSourceInfo("萌番组"), MediaSourceFetchState.Succeed(1)),
            btSource(SOURCE_ACG, MediaSourceInfo("ACG.RIP"), MediaSourceFetchState.Disabled),
            btSource(SOURCE_RSS, MediaSourceInfo("RSS"), MediaSourceFetchState.Failed(IllegalStateException("network"), 1)),
        ),
    )

    /**
     * 第一条 (桜都 OVA) 已临时选中; 偏好 1080P + 简中.
     */
    fun createBtState(backgroundScope: CoroutineScope): MediaSelectorState = createSelectorState(
        backgroundScope,
        mediaList = btMediaList,
        fetchResults = emptyList(),
        preference = MediaPreference.Empty.copy(resolution = "1080P", subtitleLanguageId = CHS),
        preferredWebMediaSource = null,
        webSessionManager = createTestWebSessionManager(backgroundScope),
        selected = btMediaSelected,
    )

    // endregion

    // region 手动查找

    /**
     * 四个支持浏览的源, 默认选中第一个; 搜索结果与线路来自 [TestBrowsableMediaSource].
     */
    fun createManualState(
        backgroundScope: CoroutineScope,
        onPlay: suspend (Media, ManualBrowseMemory?) -> Unit,
    ): ManualBrowseState = ManualBrowseState(
        browsableSources = flowOf(
            listOf("源A", "源B", "源C", "源E").map { name ->
                createTestMediaSourceInstance(TestBrowsableMediaSource(mediaSourceId = name), instanceId = name)
            },
        ),
        webSessionManager = createTestWebSessionManager(backgroundScope),
        target = flowOf(target),
        preferredSourceId = flowOf(null),
        onPlay = onPlay,
        backgroundScope = backgroundScope,
    )

    // endregion

    private fun createSelectorState(
        backgroundScope: CoroutineScope,
        mediaList: List<Media>,
        fetchResults: List<MediaSourceFetchResult>,
        preference: MediaPreference,
        preferredWebMediaSource: String?,
        webSessionManager: WebSessionManager,
        selected: Media? = null,
    ): MediaSelectorState {
        val selector = DefaultMediaSelector(
            mediaSelectorContextNotCached = flowOf(selectorContext),
            mediaListNotCached = MutableStateFlow(mediaList),
            savedUserPreference = flowOf(preference),
            savedDefaultPreference = flowOf(MediaPreference.Empty),
            mediaSelectorSettings = flowOf(MediaSelectorSettings.Default),
        )
        if (selected != null) {
            runBlocking { selector.selectTemporarily(selected) }
        }
        return MediaSelectorState(
            selector,
            mediaSourceFetchResults = flowOf(fetchResults),
            createTestMediaSourceInfoProvider(),
            preferredWebMediaSource = flowOf(preferredWebMediaSource),
            backgroundScope,
            webSessionManager,
        )
    }

    /**
     * 与 createTestWebSessionManager 相同, 只是声明支持交互解决验证码, 自动页显示「需要处理…」而不是 iOS 分支.
     */
    private fun createInteractiveWebSessionManager(backgroundScope: CoroutineScope): WebSessionManager = WebSessionManager(
        browserFactory = object : CaptchaBrowserFactory {
            override val isSupported: Boolean get() = true
            override suspend fun create(): CaptchaBrowser =
                throw UnsupportedOperationException("Screenshot tests never open a browser")
        },
        evaluator = PageEvaluator(),
        cookieJar = WebSourceCookieJar(),
        identityRegistry = WebSourceIdentityRegistry(),
        client = createDefaultHttpClient().asScopedHttpClient(),
        backgroundScope = backgroundScope,
    )

    private class FixedFetchResult(
        override val mediaSourceId: String,
        override val sourceInfo: MediaSourceInfo,
        override val kind: MediaSourceKind,
        state: MediaSourceFetchState,
        results: List<Media>,
    ) : MediaSourceFetchResult {
        override val instanceId: String get() = mediaSourceId
        override val state: StateFlow<MediaSourceFetchState> = MutableStateFlow(state)
        override val results: Flow<List<Media>> = flowOf(results.filter { it.mediaSourceId == mediaSourceId })
        override fun restart() {}
        override fun enable() {}
    }
}
