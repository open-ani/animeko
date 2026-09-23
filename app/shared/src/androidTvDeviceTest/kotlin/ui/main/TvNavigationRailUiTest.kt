/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.tv.ui.main

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import me.him188.ani.app.data.models.preference.ThemeSettings
import me.him188.ani.app.ui.framework.AniComposeUiTest
import me.him188.ani.app.ui.framework.assertScreenshot
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.tv.ui.foundation.focus.TvFocusKey
import me.him188.ani.tv.ui.foundation.focus.TvFocusScope
import me.him188.ani.tv.ui.foundation.focus.rememberTvFocusScope
import me.him188.ani.tv.ui.foundation.focus.tvFocusAnchor
import me.him188.ani.tv.ui.foundation.focus.tvFocusMemorable
import me.him188.ani.tv.ui.foundation.theme.TvApplicationTheme
import me.him188.ani.tv.ui.foundation.widgets.TvNavigationRailDefaults
import me.him188.ani.tv.ui.foundation.widgets.TvOptionRow
import org.junit.jupiter.api.Assumptions.assumeTrue

class TvNavigationRailUiTest {
    @Test
    fun floatingRailKeepsThePageFullScreenAndAnimatesTheWholePill() = runAniComposeUiTest {
        val fixture = mount()
        val pageBounds = onNodeWithTag("rail-test-page").fetchSemanticsNode().boundsInRoot
        val screenBounds = onNodeWithTag("tv-main-shell").fetchSemanticsNode().boundsInRoot
        assertEquals(screenBounds, pageBounds)
        val insets = fixture.insets
        assertTrue(insets.calculateLeftPadding(LayoutDirection.Ltr) > 0.dp)
        val collapsed = railItem("Explore").fetchSemanticsNode().boundsInRoot
        assertEquals(collapsed.height, collapsed.width)
        assertItemWidths(collapsed.width)
        onNodeWithText("Explore").assertDoesNotExist()
        capture("collapsed")

        key(Key.Menu)
        awaitRailFocus("Explore")
        mainClock.advanceTimeBy(80)
        val expanding = railItem("Explore").fetchSemanticsNode().boundsInRoot
        assertItemWidths(expanding.width)
        val textLayout = mutableListOf<TextLayoutResult>()
        onNodeWithText("Collection", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(textLayout) }
        assertTrue(textLayout.isNotEmpty() && textLayout.none { it.isLineEllipsized(0) },
            "The container reveals the label without reflowing it during expansion")
        capture("expanding")
        settle()
        val expanded = railItem("Explore").fetchSemanticsNode().boundsInRoot
        assertItemWidths(expanded.width)
        assertTrue(expanding.width > collapsed.width && expanding.width < expanded.width,
            "The pill must have an intermediate width: $collapsed -> $expanding -> $expanded")
        assertEquals(collapsed.height, expanded.height)
        assertEquals(collapsed.left, expanded.left)
        assertEquals(collapsed.top, expanded.top)
        assertEquals(pageBounds, onNodeWithTag("rail-test-page").fetchSemanticsNode().boundsInRoot)
        assertEquals(insets, fixture.insets)
        railLabels.forEach {
            onNodeWithText(it).assertExists()
        }
        val pill = railItem("Explore").captureToImage().asAndroidBitmap()
        assertTrue(brightness(pill.getPixel(pill.width / 2, 3)) > 245, "White background spans the title")
        assertTrue(brightness(pill.getPixel(0, 0)) < 240, "The pill has rounded corners")
        fun hasDarkContent(start: Int, end: Int) = (start until end).any { x ->
            (pill.height / 3 until pill.height * 2 / 3).any { y -> brightness(pill.getPixel(x, y)) < 80 }
        }
        assertTrue(hasDarkContent(pill.height / 4, pill.height * 3 / 4), "The icon is dark")
        assertTrue(hasDarkContent(pill.height + 8, pill.width - pill.height / 3), "The title is dark")
        capture("expanded")

        key(Key.DirectionRight)
        awaitContentFocus()
        mainClock.advanceTimeBy(150)
        val shrinking = railItem("Explore").fetchSemanticsNode().boundsInRoot
        assertItemWidths(shrinking.width)
        settle()
        assertTrue(shrinking.width > collapsed.width && shrinking.width < expanded.width)
        assertEquals(collapsed, railItem("Explore").fetchSemanticsNode().boundsInRoot)
        assertEquals(pageBounds, onNodeWithTag("rail-test-page").fetchSemanticsNode().boundsInRoot)
    }

    @Test
    fun railBlurFadesAtItsBoundaryAndRestoresTheBackgroundOnExit() = runAniComposeUiTest {
        assumeTrue(Build.VERSION.SDK_INT >= 31)
        mount()
        val original = capture("background-clear")
        key(Key.Menu)
        awaitRailFocus("Explore")
        settle()
        val blurred = capture("background-gradient-blur")
        fun contrast(bitmap: Bitmap, start: Int, end: Int): Int {
            val samples = (start until end).map {
                brightness(bitmap.getPixel(it, bitmap.height / 8))
            }
            return samples.max() - samples.min()
        }
        val blurEnd = (original.width * TvNavigationRailDefaults.BackgroundBlurWidthFraction).roundToInt()
        val originalContrast = contrast(original, blurEnd / 8, blurEnd / 3)
        val leftContrast = contrast(blurred, blurEnd / 8, blurEnd / 3)
        val fadingContrast = contrast(blurred, blurEnd * 4 / 5, blurEnd)
        assertTrue(originalContrast > 80)
        assertTrue(leftContrast < originalContrast / 4, "The left edge is strongly blurred: $leftContrast")
        assertTrue(fadingContrast > leftContrast + 40,
            "The blur must fade toward its boundary: $leftContrast -> $fadingContrast")
        assertTrue(contrast(blurred, blurEnd, original.width) > originalContrast / 2,
            "The dimmed background retains contrast outside the blur")
        val dimmedColors = mutableMapOf<Int, Int>()
        for (x in blurEnd until original.width) {
            val sourceColor = original.getPixel(x, original.height / 8)
            val dimmedColor = blurred.getPixel(x, blurred.height / 8)
            val expected = dimmedColors.getOrPut(sourceColor) { dimmedColor }
            assertEquals(expected, dimmedColor,
                "The uniform dim preserves sharp stripe edges outside the blur at x=$x")
        }
        key(Key.Menu)
        awaitContentFocus()
        settle()
        val restored = capture("background-restored")
        for (x in 0 until original.width) {
            assertEquals(original.getPixel(x, original.height / 8), restored.getPixel(x, restored.height / 8),
                "Leaving the rail restores the background at x=$x")
        }
    }

    @Test
    fun railExitRestoresTheRememberedControlAndSwitchingPagesKeepsNavigationWorking() = runAniComposeUiTest {
        val fixture = mount()
        for (exit in listOf(Key.Menu, Key.DirectionRight, Key.Back, Key.DirectionCenter)) {
            key(Key.Menu)
            awaitRailFocus("Explore")
            key(exit)
            awaitContentFocus()
            assertEquals(TvShellContent.Exploration, fixture.page)
        }
        key(Key.Menu)
        awaitRailFocus("Explore")
        key(Key.DirectionDown)
        awaitRailFocus("Schedule")
        key(Key.DirectionCenter)
        settle()
        awaitContentFocus()
        assertEquals(TvShellContent.Schedule, fixture.page)
        key(Key.Menu)
        awaitRailFocus("Schedule")
        key(Key.DirectionRight)
        awaitContentFocus()
    }

    @Test
    fun outgoingTabCannotTakeFocusDuringTheShellTransition() = runAniComposeUiTest {
        val fixture = mount()
        val outgoing = fixture.scopes.getValue(TvShellContent.Exploration)
        val started = mainClock.currentTime
        runOnIdle { fixture.page = TvShellContent.Schedule }
        waitUntil(timeoutMillis = 5_000) {
            mainClock.advanceTimeByFrame()
            assertTrue(mainClock.currentTime < started + 200)
            fixture.scopes[TvShellContent.Schedule]?.isFocused(TvFocusKey("remembered-Schedule")) == true
        }
        runOnIdle {
            assertTrue(!outgoing.isActive)
            assertTrue(outgoing.isAnchorAttached(TvFocusKey("remembered-Exploration")))
            outgoing.request(TvFocusKey("remembered-Exploration"))
            outgoing.requesterOf(TvFocusKey("remembered-Exploration")).requestFocus()
        }
        onNodeWithTag("rail-test-remembered").assertIsFocused()
        settle()
        runOnIdle { assertTrue(fixture.scopes.getValue(TvShellContent.Schedule).isFocused(TvFocusKey("remembered-Schedule"))) }
    }

    private class Fixture {
        var page by mutableStateOf(TvShellContent.Exploration)
        var insets: PaddingValues = PaddingValues(0.dp)
        val scopes = mutableMapOf<TvShellContent, TvFocusScope>()
    }

    private fun AniComposeUiTest.mount(): Fixture {
        mainClock.autoAdvance = false
        val fixture = Fixture()
        setContent {
            TvApplicationTheme(ThemeSettings.Default.seedColor, languageTag = "en") {
                TvMainShell(TvMainUiState(), fixture.page, { fixture.page = it }, {}, {}) { page, insets ->
                    fixture.insets = insets
                    val focus = rememberTvFocusScope()
                    SideEffect { fixture.scopes[page] = focus }
                    val target = TvFocusKey("remembered-$page")
                    focus.Resolver()
                    focus.InitialFocus(target)
                    Box(Modifier.fillMaxSize().testTag("rail-test-page")) {
                        Canvas(Modifier.fillMaxSize()) {
                            val stripeWidth = 16.dp.toPx()
                            repeat(ceil(size.width / stripeWidth).toInt()) { index ->
                                drawRect(
                                    if (index % 2 == 0) Color(0xFFB3CADB) else Color(0xFF193450),
                                    Offset(index * stripeWidth, 0f), Size(stripeWidth, size.height),
                                )
                            }
                        }
                        Box(Modifier.fillMaxSize().padding(insets).padding(24.dp)) {
                            Text("Page: $page", Modifier.align(Alignment.TopStart), color = Color.White)
                            TvOptionRow("Nearest control", modifier = Modifier.align(Alignment.CenterStart).width(180.dp)) {}
                            TvOptionRow(
                                "Remembered control", modifier = Modifier.align(Alignment.BottomEnd).width(200.dp)
                                    .testTag("rail-test-remembered").tvFocusAnchor(focus, target).tvFocusMemorable(target),
                            ) {}
                        }
                    }
                }
            }
        }
        awaitContentFocus()
        settle()
        return fixture
    }

    private fun AniComposeUiTest.railItem(label: String): SemanticsNodeInteraction =
        onNode(hasContentDescription(label) and hasAnyAncestor(hasTestTag("tv-main-navigation")))

    private val railLabels = listOf("Search", "Explore", "Schedule", "Collection", "Settings", "Sign In")

    private fun AniComposeUiTest.assertItemWidths(expected: Float) {
        railLabels.forEach { label ->
            assertEquals(expected, railItem(label).fetchSemanticsNode().boundsInRoot.width, "Width of $label")
        }
    }

    private fun AniComposeUiTest.awaitRailFocus(label: String) {
        waitUntil(timeoutMillis = 5_000) {
            mainClock.advanceTimeByFrame()
            onAllNodes(hasContentDescription(label) and isFocused()).fetchSemanticsNodes().isNotEmpty()
        }
        railItem(label).assertIsFocused()
    }

    private fun AniComposeUiTest.awaitContentFocus() {
        waitUntil(timeoutMillis = 5_000) {
            mainClock.advanceTimeByFrame()
            onAllNodes(hasTestTag("rail-test-remembered") and isFocused()).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun AniComposeUiTest.key(key: Key) {
        onAllNodes(isRoot() and hasAnyDescendant(isFocused())).onLast().performKeyInput { pressKey(key) }
        mainClock.advanceTimeByFrame()
    }

    private fun AniComposeUiTest.settle() { mainClock.advanceTimeBy(600); waitForIdle() }

    private fun AniComposeUiTest.capture(name: String): Bitmap {
        val node = onNodeWithTag("tv-main-shell")
        node.assertScreenshot("tv-navigation-rail/$name")
        return node.captureToImage().asAndroidBitmap().also { bitmap ->
            val output = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "tv-rail-$name.png")
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun brightness(pixel: Int): Int = ((pixel shr 16 and 255) + (pixel shr 8 and 255) + (pixel and 255)) / 3
}
