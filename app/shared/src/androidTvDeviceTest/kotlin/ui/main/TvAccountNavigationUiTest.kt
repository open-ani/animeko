/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.tv.ui.main

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.printToString
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid
import me.him188.ani.app.data.models.preference.ThemeSettings
import me.him188.ani.app.data.models.user.SelfInfo
import me.him188.ani.app.ui.framework.AniComposeUiTest
import me.him188.ani.app.ui.framework.assertScreenshot
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.tv.ui.foundation.focus.TvFocusKey
import me.him188.ani.tv.ui.foundation.focus.rememberTvFocusScope
import me.him188.ani.tv.ui.foundation.focus.tvFocusAnchor
import me.him188.ani.tv.ui.foundation.focus.tvFocusMemorable
import me.him188.ani.tv.ui.foundation.theme.TvApplicationTheme
import me.him188.ani.tv.ui.foundation.widgets.TvOptionRow

class TvAccountNavigationUiTest {
    @Test
    fun guestAvatarOpensLoginWithoutShowingLogout() = runAniComposeUiTest {
        val fixture = mount(loggedIn = false)
        focusAvatar()
        onNodeWithTag("tv-navigation-logout").assertDoesNotExist()
        key(Key.DirectionCenter)
        awaitFocus("test-page-Login")
        assertEquals(TvShellContent.Login, fixture.content)
        assertEquals(0, fixture.logoutCount)
    }

    @Test
    fun validSessionWithoutProfileDoesNotOpenLogin() = runAniComposeUiTest {
        val fixture = mount()
        runOnIdle { fixture.selfInfo = null }
        focusAvatar()
        onNodeWithTag("tv-navigation-logout").assertExists()
        key(Key.DirectionCenter)
        onNodeWithTag("tv-navigation-avatar").assertIsFocused()
        assertEquals(TvShellContent.Schedule, fixture.content)
    }

    @Test
    fun restoringSessionWaitsForItsResultWithoutLosingAvatarFocus() = runAniComposeUiTest {
        val fixture = mount(loggedIn = false)
        runOnIdle { fixture.isLoggedIn = null }
        focusAvatar()
        key(Key.DirectionCenter)
        assertEquals(TvShellContent.Schedule, fixture.content)
        onNodeWithTag("tv-navigation-logout").assertDoesNotExist()
        runOnIdle { fixture.isLoggedIn = true }
        onNodeWithTag("tv-navigation-avatar").assertIsFocused()
        onNodeWithTag("tv-navigation-logout").assertExists()
    }

    @Test
    fun loggedInAvatarRevealsLogoutAboveWithoutMovingNavigationOrOpeningLogin() = runAniComposeUiTest {
        val fixture = mount()
        openRail()
        onNodeWithTag("tv-navigation-logout").assertDoesNotExist()
        val before = navigationBounds()
        repeat(3) { key(Key.DirectionUp) }
        awaitFocus("tv-navigation-avatar")
        onNodeWithTag("tv-navigation-logout").assertExists()
        assertEquals(before, navigationBounds())
        val avatar = onNodeWithTag("tv-navigation-avatar").getUnclippedBoundsInRoot()
        val logout = onNodeWithTag("tv-navigation-logout").getUnclippedBoundsInRoot()
        assertEquals(avatar.left, logout.left)
        assertEquals(avatar.right, logout.right)
        before.forEach { assertEquals(avatar.right - avatar.left, it.right - it.left) }
        assertTrue(logout.bottom < avatar.top)
        key(Key.DirectionCenter)
        onNodeWithTag("tv-navigation-avatar").assertIsFocused()
        assertEquals(TvShellContent.Schedule, fixture.content)
        capture("avatar-actions")

        key(Key.DirectionUp)
        awaitFocus("tv-navigation-logout")
        assertEquals(before, navigationBounds())
        capture("logout-focused")
        key(Key.DirectionDown)
        awaitFocus("tv-navigation-avatar")
        key(Key.DirectionDown)
        onNodeWithTag("tv-navigation-logout").assertDoesNotExist()
        assertEquals(before, navigationBounds())
        key(Key.DirectionUp)
        awaitFocus("tv-navigation-avatar")
        key(Key.Menu)
        awaitFocus("test-page-Schedule")
        onNodeWithTag("tv-navigation-logout").assertDoesNotExist()
    }

    @Test
    fun logoutRequiresConfirmationAndCancelAndBackRestoreItsEntry() = runAniComposeUiTest {
        val fixture = mount()
        openLogout()
        awaitFocus("tv-logout-cancel")
        assertEquals(0, fixture.logoutCount)
        capture("confirmation")
        key(Key.Menu)
        onNodeWithTag("tv-logout-cancel").assertIsFocused()
        key(Key.DirectionCenter)
        awaitFocus("tv-navigation-logout")
        onNodeWithTag("tv-logout-confirmation").assertDoesNotExist()
        assertEquals(0, fixture.logoutCount)

        key(Key.DirectionCenter)
        awaitFocus("tv-logout-cancel")
        key(Key.Back)
        awaitFocus("tv-navigation-logout")
        assertEquals(0, fixture.logoutCount)
        assertEquals(TvShellContent.Schedule, fixture.content)
    }

    @Test
    fun confirmedLogoutKeepsCurrentPageAndReturnsToTheLoginAvatar() = runAniComposeUiTest {
        val fixture = mount()
        openLogout()
        awaitFocus("tv-logout-cancel")
        key(Key.DirectionRight)
        awaitFocus("tv-logout-confirm")
        key(Key.DirectionCenter)
        awaitFocus("tv-navigation-avatar")
        assertEquals(1, fixture.logoutCount)
        assertEquals(TvShellContent.Schedule, fixture.content)
        onNodeWithTag("tv-logout-confirmation").assertDoesNotExist()
        onNodeWithTag("tv-navigation-logout").assertDoesNotExist()
        key(Key.DirectionCenter)
        awaitFocus("test-page-Login")
    }

    @Test
    fun sessionEndingWithConfirmationOpenRestoresTheAvatar() = runAniComposeUiTest {
        val fixture = mount()
        openLogout()
        awaitFocus("tv-logout-cancel")
        runOnIdle { fixture.isLoggedIn = false; fixture.selfInfo = null }
        awaitFocus("tv-navigation-avatar")
        onNodeWithTag("tv-logout-confirmation").assertDoesNotExist()
        onNodeWithTag("tv-navigation-logout").assertDoesNotExist()
        assertEquals(0, fixture.logoutCount)
    }

    @Test
    fun touchModeAfterATapKeepsTheRailReachable() = runAniComposeUiTest {
        val fixture = mount()
        tapOutsideControls()
        waitUntil(timeoutMillis = 5_000) {
            mainClock.advanceTimeByFrame()
            runOnUiThread { InputMode.Touch in fixture.inputModes }
        }
        openRail()
    }

    private class Fixture(loggedIn: Boolean) {
        var selfInfo by mutableStateOf(if (loggedIn) SelfInfo(
            Uuid.parse("00000000-0000-0000-0000-000000000001"),
            "Animeko User", null, false, null, null,
        ) else null)
        var isLoggedIn by mutableStateOf<Boolean?>(loggedIn)
        var content by mutableStateOf(TvShellContent.Schedule)
        var logoutCount = 0
        val inputModes = mutableListOf<InputMode>()
    }

    private fun AniComposeUiTest.mount(loggedIn: Boolean = true): Fixture {
        val fixture = Fixture(loggedIn)
        setContent {
            val inputModeManager = LocalInputModeManager.current
            LaunchedEffect(inputModeManager) {
                snapshotFlow { inputModeManager.inputMode }.collect { fixture.inputModes += it }
            }
            TvApplicationTheme(ThemeSettings.Default.seedColor, languageTag = "en") {
                TvMainShell(
                    uiState = TvMainUiState(fixture.selfInfo, fixture.isLoggedIn),
                    content = fixture.content,
                    onContentChange = { fixture.content = it },
                    onOpenSettings = {},
                    onLogout = {
                        fixture.logoutCount++
                        fixture.isLoggedIn = false
                        fixture.selfInfo = null
                    },
                ) { page, navigationRailInsets ->
                    val focus = rememberTvFocusScope()
                    val entry = TvFocusKey("test-page-$page")
                    focus.Resolver()
                    focus.InitialFocus(entry)
                    Box(Modifier.fillMaxSize().padding(navigationRailInsets), contentAlignment = Alignment.Center) {
                        TvOptionRow(
                            title = "Page: $page",
                            modifier = Modifier.width(240.dp).padding(16.dp).testTag("test-page-$page")
                                .tvFocusAnchor(focus, entry).tvFocusMemorable("test-page-$page"),
                        ) {}
                    }
                }
            }
        }
        awaitFocus("test-page-Schedule")
        return fixture
    }

    private fun AniComposeUiTest.navigationBounds() = listOf("Search", "Explore", "Schedule", "Collection", "Settings")
        .map { label ->
            onNode(hasText(label) and hasAnyAncestor(hasTestTag("tv-main-navigation"))).getUnclippedBoundsInRoot()
        } + onNodeWithTag("tv-navigation-avatar").getUnclippedBoundsInRoot()

    private fun AniComposeUiTest.focusAvatar() {
        openRail()
        repeat(3) { key(Key.DirectionUp) }
        awaitFocus("tv-navigation-avatar")
    }

    /**
     * Menu 把焦点送进侧边栏是异步解析的, 而方向键会取消在途的送焦请求;
     * 所以下一次按键前必须等到侧边栏真正持有焦点.
     */
    private fun AniComposeUiTest.openRail() {
        key(Key.Menu)
        awaitFocusWithin("tv-main-navigation")
    }

    private fun AniComposeUiTest.openLogout() {
        focusAvatar()
        key(Key.DirectionUp)
        awaitFocus("tv-navigation-logout")
        key(Key.DirectionCenter)
    }

    private fun AniComposeUiTest.key(key: Key) {
        onAllNodes(isRoot() and hasAnyDescendant(isFocused())).onLast().performKeyInput { pressKey(key) }
        mainClock.advanceTimeByFrame()
    }

    /** 经系统输入管线注入的触摸, 与真实触摸一样让窗口进入 touch mode. */
    private fun AniComposeUiTest.tapOutsideControls() {
        val shell = onNodeWithTag("tv-main-shell").fetchSemanticsNode()
        // 右缘中部: 页面内容居中, 侧边栏在左, 系统栏在上下, 这里只有壳背景.
        val x = shell.positionOnScreen.x + shell.size.width - 16f
        val y = shell.positionOnScreen.y + shell.size.height / 2f
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val downTime = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
            instrumentation.sendPointerSync(event)
            event.recycle()
        }
    }

    private fun AniComposeUiTest.awaitFocus(tag: String) = awaitFocus(tag, hasTestTag(tag) and isFocused())

    private fun AniComposeUiTest.awaitFocusWithin(tag: String) =
        awaitFocus("within $tag", hasTestTag(tag) and hasAnyDescendant(isFocused()))

    private fun AniComposeUiTest.awaitFocus(description: String, matcher: SemanticsMatcher) {
        try {
            waitUntil(timeoutMillis = 5_000) {
                mainClock.advanceTimeByFrame()
                onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (failure: Throwable) {
            throw AssertionError(
                "Expected focus: $description\n" + onAllNodes(isRoot()).onLast().printToString(), failure,
            )
        }
    }

    private fun AniComposeUiTest.capture(name: String) {
        mainClock.advanceTimeBy(250)
        onNodeWithTag("tv-main-shell").assertScreenshot("tv-account/$name")
        val output = File(
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "tv-account-$name.png",
        )
        output.outputStream().use {
            onNodeWithTag("tv-main-shell").captureToImage().asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
