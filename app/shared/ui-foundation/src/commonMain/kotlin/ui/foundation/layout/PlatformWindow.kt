/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:Suppress("NOTHING_TO_INLINE")

package me.him188.ani.app.ui.foundation.layout

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.DrawerDefaults
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationRailDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import me.him188.ani.app.platform.PlatformWindow

/**
 * 增加桌面端系统强制的窗口 padding.
 *
 * - Windows: 0
 * - macOS: 窗口可沉浸到标题栏内, 可在标题栏内绘制, 然后使用 padding 让内容放置在标题栏区域之外
 */
fun Modifier.desktopTitleBarPadding(): Modifier = composed({ name = "desktopTitleBarPadding" }) {
    Modifier.windowInsetsPadding(WindowInsets.desktopTitleBar)
}

@Composable
fun WindowInsets.Companion.desktopTitleBar() = desktopTitleBar

/**
 * @see desktopTitleBarPadding
 */
val WindowInsets.Companion.desktopTitleBar
    @Composable
    get() = LocalTitleBarInsets.current

val WindowInsets.Companion.desktopCaptionButton
    @Composable
    get() = LocalCaptionButtonInsets.current

val ZeroInsets = WindowInsets(0.dp)

val LocalTitleBarInsets = compositionLocalOf { ZeroInsets }
val LocalCaptionButtonInsets = compositionLocalOf { ZeroInsets }

operator fun WindowInsets.plus(other: WindowInsets): WindowInsets {
    return PlusWindowInsets(this, other) { a, b -> a + b }
}

@Immutable
private class PlusWindowInsets(
    private val a: WindowInsets,
    private val b: WindowInsets,
    private val function: (Int, Int) -> Int,
) : WindowInsets {
    override fun getBottom(density: Density): Int {
        return function(a.getBottom(density), b.getBottom(density))
    }

    override fun getLeft(density: Density, layoutDirection: LayoutDirection): Int {
        return function(a.getLeft(density, layoutDirection), b.getLeft(density, layoutDirection))
    }

    override fun getRight(density: Density, layoutDirection: LayoutDirection): Int {
        return function(a.getRight(density, layoutDirection), b.getRight(density, layoutDirection))
    }

    override fun getTop(density: Density): Int {
        return function(a.getTop(density), b.getTop(density))
    }
}

typealias PlatformWindowMP = PlatformWindow

val LocalPlatformWindow: ProvidableCompositionLocal<PlatformWindowMP> = staticCompositionLocalOf {
    error("No PlatformWindow provided")
}

@Stable
object AniWindowInsets {
    // 不会包含手机横屏状态下的左侧屏幕刘海 (displayCutout)
    val systemBars
        @Composable
        get() = WindowInsets.systemBars + WindowInsets.desktopTitleBar

    val statusBars
        @Composable
        get() = WindowInsets.statusBars + WindowInsets.desktopTitleBar

    // 总是包含各种刘海
    val safeDrawing
        @Composable
        get() = WindowInsets.safeDrawing + WindowInsets.desktopTitleBar

    /**
     * 如果 TopAppBar 会接触窗口左上角, 就使用这个. 因为 macOS 的标题栏是透明且悬浮的.
     */
    @Composable
    inline fun forTopAppBar() = (systemBars.union(WindowInsets.displayCutout))
        .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal) // 要加上 displayCutout 因为刘海可能会挡住横屏状态下的状态栏返回键

    /**
     * 如果 TopAppBar 不会接触窗口左上角, 就使用这个.
     */
    @Composable
    inline fun forTopAppBarWithoutDesktopTitle() = forTopAppBar()// 要加上 displayCutout 因为刘海可能会挡住横屏状态下的状态栏返回键

    /**
     * 包含 macOS 标题栏
     */
    @Composable
    inline fun forPageContent() = safeDrawing

    @Composable
    inline fun forSearchBar() = safeDrawing

    /**
     * @see NavigationBarDefaults.windowInsets
     */
    @Composable
    fun forNavigationBar() = safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)

    /**
     * @see NavigationRailDefaults.windowInsets
     */
    @Composable
    fun forNavigationRail() = safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical)

    /**
     * @see DrawerDefaults.windowInsets
     */
    @Composable
    @NonRestartableComposable
    inline fun forNavigationDrawer() = forNavigationRail()

//    fun print() {
//            println("systemBars Left: " + systemBars.getLeft(LocalDensity.current, LayoutDirection.Ltr))
//            println("systemBars Right: " + systemBars.getRight(LocalDensity.current, LayoutDirection.Ltr))
//            println("systemBars Top: " + systemBars.getTop(LocalDensity.current))
//
//            println("safeDrawing Left: " + WindowInsets.safeDrawing.getLeft(LocalDensity.current, LayoutDirection.Ltr))
//            println("safeGestures Left: " + WindowInsets.safeGestures.getLeft(LocalDensity.current, LayoutDirection.Ltr))
//            println("safeContent Left: " + WindowInsets.safeContent.getLeft(LocalDensity.current, LayoutDirection.Ltr))
//    }
}
