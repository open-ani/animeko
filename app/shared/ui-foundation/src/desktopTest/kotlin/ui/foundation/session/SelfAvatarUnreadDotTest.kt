/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.session

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import me.him188.ani.app.ui.foundation.ProvideFoundationCompositionLocalsForTest
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.login_sign_in
import me.him188.ani.app.ui.lang.notification_has_unread
import me.him188.ani.app.ui.user.SelfInfoUiState
import me.him188.ani.app.ui.user.TestSelfInfoUiState
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.getString
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(TestOnly::class)
class SelfAvatarUnreadDotTest {
    private val unreadText = runBlocking { getString(Lang.notification_has_unread) }

    private val guest = SelfInfoUiState(selfInfo = null, isLoading = false, isSessionValid = false, bangumiConnected = null)
    private val loading = SelfInfoUiState(selfInfo = null, isLoading = true, isSessionValid = null, bangumiConnected = null)

    private fun ComposeUiTest.avatarDescriptions(): List<String> =
        onNodeWithTag(AVATAR).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()

    @Test
    fun `logged-in avatar announces unread only while the dot is provided`() = runAniComposeUiTest {
        var dot by mutableStateOf(true)
        var clicks = 0
        setContent {
            ProvideFoundationCompositionLocalsForTest {
                CompositionLocalProvider(LocalSelfAvatarUnreadDot provides dot) {
                    SelfAvatar(TestSelfInfoUiState, AvatarSize, onClick = { clicks++ }, Modifier.testTag(AVATAR))
                }
            }
        }

        assertTrue(unreadText in avatarDescriptions(), avatarDescriptions().toString())

        runOnIdle { dot = false }
        waitForIdle()
        assertFalse(unreadText in avatarDescriptions(), avatarDescriptions().toString())

        onNodeWithTag(AVATAR).performClick()
        runOnIdle { assertEquals(1, clicks) }
    }

    @Test
    fun `dot is drawn on the top end of the avatar with a ring cut out around it`() = runAniComposeUiTest {
        var dot by mutableStateOf(true)
        var errorColor = Color.Unspecified
        var surfaceColor = Color.Unspecified
        setContent {
            ProvideFoundationCompositionLocalsForTest {
                errorColor = MaterialTheme.colorScheme.error
                surfaceColor = MaterialTheme.colorScheme.surface
                // 放大以免抗锯齿影响取样
                CompositionLocalProvider(
                    LocalDensity provides Density(Scale),
                    LocalSelfAvatarUnreadDot provides dot,
                ) {
                    Box(Modifier.testTag(CONTAINER).background(Background).padding(ContainerPadding)) {
                        SelfAvatar(TestSelfInfoUiState, AvatarSize, onClick = {})
                    }
                }
            }
        }

        // 头像在 48dp 的点击区域中央, 红点圆心在头像圆周的右上方 45° 处
        val avatarCenter = ContainerPadding + 24.dp
        val dotCenterX = avatarCenter + 12.73.dp
        val dotCenterY = avatarCenter - 12.73.dp
        // 从红点圆心向头像圆心偏移 4dp, 落在挖去的一圈 (距红点圆心 3dp 到 5dp) 中
        val ringX = dotCenterX - 2.83.dp
        val ringY = dotCenterY + 2.83.dp

        var image = onNodeWithTag(CONTAINER).captureToImage()
        assertColor(errorColor, image.pixelAt(dotCenterX.value, dotCenterY.value))
        assertColor(Background, image.pixelAt(ringX.value, ringY.value))

        runOnIdle { dot = false }
        waitForIdle()
        image = onNodeWithTag(CONTAINER).captureToImage()
        assertFalse(image.containsColor(errorColor))
        assertColor(surfaceColor, image.pixelAt(ringX.value, ringY.value))
    }

    @Test
    fun `guest sign-in button and loading avatar never show the dot`() = runAniComposeUiTest {
        var state by mutableStateOf(guest)
        var errorColor = Color.Unspecified
        setContent {
            ProvideFoundationCompositionLocalsForTest {
                errorColor = MaterialTheme.colorScheme.error
                CompositionLocalProvider(LocalSelfAvatarUnreadDot provides true) {
                    Box(Modifier.testTag(CONTAINER)) {
                        SelfAvatar(state, AvatarSize, onClick = {}, Modifier.testTag(AVATAR))
                    }
                }
            }
        }

        val signIn = runBlocking { getString(Lang.login_sign_in) }
        onNodeWithText(signIn).assertExists()
        onAllNodesWithContentDescription(unreadText, useUnmergedTree = true).assertCountEquals(0)
        assertFalse(onNodeWithTag(CONTAINER).captureToImage().containsColor(errorColor))

        runOnIdle { state = loading }
        waitForIdle()
        onAllNodesWithContentDescription(unreadText, useUnmergedTree = true).assertCountEquals(0)
        assertFalse(onNodeWithTag(CONTAINER).captureToImage().containsColor(errorColor))
    }

    private fun ImageBitmap.pixelAt(xDp: Float, yDp: Float): Color =
        toPixelMap()[(xDp * Scale).toInt(), (yDp * Scale).toInt()]

    private fun ImageBitmap.containsColor(color: Color): Boolean {
        val pixels = toPixelMap()
        for (x in 0 until width) {
            for (y in 0 until height) {
                if (pixels[x, y].isCloseTo(color)) return true
            }
        }
        return false
    }

    private fun Color.isCloseTo(other: Color): Boolean =
        abs(red - other.red) < 0.02f && abs(green - other.green) < 0.02f && abs(blue - other.blue) < 0.02f

    private fun assertColor(expected: Color, actual: Color) {
        assertTrue(actual.isCloseTo(expected), "expected $expected, got $actual")
    }

    private companion object {
        const val AVATAR = "avatar"
        const val CONTAINER = "container"
        const val Scale = 4f
        val AvatarSize = DpSize(36.dp, 36.dp)
        val ContainerPadding = 8.dp
        val Background = Color(0xFF00FF00)
    }
}
