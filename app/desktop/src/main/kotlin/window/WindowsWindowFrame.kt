/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package me.him188.ani.app.desktop.window

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.MutableWindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.FontLoadResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.him188.ani.app.platform.window.ExtendedUser32
import me.him188.ani.app.platform.window.LocalTitleBarThemeController
import me.him188.ani.app.platform.window.TitleBarThemeController
import me.him188.ani.app.platform.window.WindowsWindowUtils
import me.him188.ani.app.platform.window.rememberLayoutHitTestOwner
import me.him188.ani.app.ui.foundation.ifThen
import me.him188.ani.app.ui.foundation.layout.LocalCaptionButtonInsets
import me.him188.ani.app.ui.foundation.layout.LocalPlatformWindow
import me.him188.ani.app.ui.foundation.layout.LocalTitleBarInsets
import me.him188.ani.app.ui.foundation.layout.ZeroInsets
import me.him188.ani.app.ui.foundation.layout.isSystemInFullscreen
import me.him188.ani.app.videoplayer.ui.guesture.VIDEO_GESTURE_MOUSE_MOVE_SHOW_CONTROLLER_DURATION
import me.him188.ani.desktop.generated.resources.Res
import me.him188.ani.desktop.generated.resources.ic_fluent_arrow_minimize_28_regular
import me.him188.ani.desktop.generated.resources.ic_fluent_dismiss_48_regular
import me.him188.ani.desktop.generated.resources.ic_fluent_square_48_regular
import me.him188.ani.desktop.generated.resources.ic_fluent_square_multiple_48_regular
import me.him188.ani.desktop.generated.resources.ic_fluent_subtract_48_filled
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalTextApi::class, InternalComposeUiApi::class)
@Composable
fun FrameWindowScope.WindowsWindowFrame(
    windowState: WindowState,
    onCloseRequest: () -> Unit,
    content: @Composable () -> Unit
) {
    val layoutHitTestOwner = rememberLayoutHitTestOwner()
    val platformWindow = LocalPlatformWindow.current
    if (layoutHitTestOwner == null) {
        content()
        return
    }
    val titleBarInsets = remember { MutableWindowInsets() }
    val captionButtonsInsets = remember { MutableWindowInsets() }
    val buttonRects = remember { Array(3) { Rect.Zero } }
    val density = LocalDensity.current
    val windowUtils = WindowsWindowUtils.instance
    DisposableEffect(window.windowHandle, density, layoutHitTestOwner, platformWindow, this) {
        windowUtils.extendToTitleBar(platformWindow, this@WindowsWindowFrame) { x, y ->
            when {
                buttonRects[0].contains(x, y) -> ExtendedUser32.HTMINBUTTON
                buttonRects[1].contains(x, y) -> ExtendedUser32.HTMAXBUTTON
                buttonRects[2].contains(x, y) -> ExtendedUser32.HTCLOSE
                y <= titleBarInsets.insets.getTop(density) && !layoutHitTestOwner.hitTest(
                    x,
                    y,
                ) -> ExtendedUser32.HTCAPTION

                else -> ExtendedUser32.HTCLIENT
            }
        }
        onDispose {
            windowUtils.removeExtendToTitleBar(platformWindow)
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        val isFullScreen = isSystemInFullscreen()
        //Control the visibility of the title bar. initial value is !isFullScreen.
        val isTitleBarVisible = remember(isFullScreen) { mutableStateOf(!isFullScreen) }
        val captionButtonThemeController = remember(platformWindow) { TitleBarThemeController() }
        CompositionLocalProvider(
            LocalTitleBarInsets provides if (isTitleBarVisible.value) {
                titleBarInsets
            } else {
                ZeroInsets
            },
            LocalCaptionButtonInsets provides if (isTitleBarVisible.value) {
                captionButtonsInsets
            } else {
                ZeroInsets
            },
            LocalTitleBarThemeController provides captionButtonThemeController,
            content = content,
        )
        val titleBarInteractionSource = remember(isFullScreen) { MutableInteractionSource() }
        val titleBarHovered by titleBarInteractionSource.collectIsHoveredAsState()
        LaunchedEffect(titleBarInteractionSource, titleBarHovered, isFullScreen) {
            if (!titleBarHovered && isFullScreen) {
                delay(VIDEO_GESTURE_MOUSE_MOVE_SHOW_CONTROLLER_DURATION)
                isTitleBarVisible.value = false
            }
        }
        AnimatedVisibility(
            visible = isTitleBarVisible.value,
            modifier = Modifier
                .ifThen(isTitleBarVisible.value && isFullScreen) { hoverable(titleBarInteractionSource) }
                .fillMaxWidth()
                .onSizeChanged { titleBarInsets.insets = WindowInsets(top = it.height) }
                .wrapContentWidth(AbsoluteAlignment.Right),
        ) {
            Row(
                modifier = Modifier.onSizeChanged {
                    captionButtonsInsets.insets = WindowInsets(right = it.width, top = it.height)
                },
            ) {
                val fontIconFamily = remember { mutableStateOf<FontFamily?>(null) }
                // Get windows system font icon, if get failed fall back to fluent svg icon.
                val fontFamilyResolver = LocalFontFamilyResolver.current
                LaunchedEffect(fontFamilyResolver) {
                    fontIconFamily.value = sequenceOf("Segoe Fluent Icons", "Segoe MDL2 Assets")
                        .mapNotNull {
                            val fontFamily = FontFamily(it)
                            runCatching {
                                val result = fontFamilyResolver.resolve(fontFamily).value as FontLoadResult
                                if (result.typeface == null || result.typeface?.familyName != it) {
                                    null
                                } else {
                                    fontFamily
                                }
                            }.getOrNull()
                        }
                        .firstOrNull()
                }
                CompositionLocalProvider(
                    LocalCaptionIconFamily provides fontIconFamily.value,
                    LocalWindowsColorScheme provides if (captionButtonThemeController.isDark) {
                        WindowsColorScheme.dark()
                    } else {
                        WindowsColorScheme.light()
                    },
                ) {
                    CaptionButtonRow(
                        windowsWindowUtils = windowUtils,
                        windowState = windowState,
                        isMaximize = windowState.placement == WindowPlacement.Maximized,
                        onCloseRequest = onCloseRequest,
                        onMaximizeButtonRectUpdate = {
                            buttonRects[1] = it
                        },
                        onMinimizeButtonRectUpdate = {
                            buttonRects[0] = it
                        },
                        onCloseButtonRectUpdate = {
                            buttonRects[2] = it
                        },
                    )
                }
            }
        }

        //Auto hoverable area that can be used to show title bar when title bar is hidden.
        if (!isTitleBarVisible.value) {
            val awareAreaInteractionSource = remember { MutableInteractionSource() }
            val isAwareHovered by awareAreaInteractionSource.collectIsHoveredAsState()
            LaunchedEffect(isAwareHovered) {
                if (isAwareHovered) {
                    isTitleBarVisible.value = true
                }
            }
            Spacer(
                modifier = Modifier.hoverable(awareAreaInteractionSource)
                    .fillMaxWidth()
                    .height(16.dp),
            )
        }
    }
}

@Composable
private fun FrameWindowScope.CaptionButtonRow(
    windowsWindowUtils: WindowsWindowUtils,
    isMaximize: Boolean,
    windowState: WindowState,
    onCloseRequest: () -> Unit,
    modifier: Modifier = Modifier,
    onMinimizeButtonRectUpdate: (Rect) -> Unit,
    onMaximizeButtonRectUpdate: (Rect) -> Unit,
    onCloseButtonRectUpdate: (Rect) -> Unit,
) {
    val isActive = remember { mutableStateOf(false) }
    //Draw the caption button
    val platformWindow = LocalPlatformWindow.current
    LaunchedEffect(platformWindow) {
        windowsWindowUtils.windowIsActive(platformWindow)
            .collect {
                isActive.value = it != false
            }
    }
    Row(
        horizontalArrangement = Arrangement.aligned(AbsoluteAlignment.Right),
        modifier = modifier
            .zIndex(1f),
    ) {
        CaptionButton(
            onClick = {
                windowsWindowUtils.minimizeWindow(window.windowHandle)
            },
            icon = CaptionButtonIcon.Minimize,
            isActive = isActive.value,
            modifier = Modifier.onGloballyPositioned { onMinimizeButtonRectUpdate(it.boundsInWindow()) },
        )
        val isFullScreen = isSystemInFullscreen()
        val coroutineScope = rememberCoroutineScope()
        CaptionButton(
            onClick = {
                when {
                    isFullScreen -> {
                        coroutineScope.launch {
                            windowsWindowUtils.setUndecoratedFullscreen(
                                platformWindow,
                                windowState,
                                false,
                            )
                        }
                    }

                    isMaximize -> {
                        windowsWindowUtils.restoreWindow(window.windowHandle)
                    }

                    else -> {
                        windowsWindowUtils.maximizeWindow(window.windowHandle)
                    }
                }
            },
            icon = when {
                isFullScreen -> CaptionButtonIcon.BackToWindow
                isMaximize -> CaptionButtonIcon.Restore
                else -> CaptionButtonIcon.Maximize
            },
            isActive = isActive.value,
            modifier = Modifier.onGloballyPositioned {
                onMaximizeButtonRectUpdate(it.boundsInWindow())
            },
        )
        CaptionButton(
            icon = CaptionButtonIcon.Close,
            onClick = onCloseRequest,
            isActive = isActive.value,
            colors = CaptionButtonDefaults.closeColors(),
            modifier = Modifier.onGloballyPositioned { onCloseButtonRectUpdate(it.boundsInWindow()) },
        )
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
private fun CaptionButton(
    onClick: () -> Unit,
    icon: CaptionButtonIcon,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    colors: CaptionButtonColors = CaptionButtonDefaults.defaultColors(),
    interaction: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val isHovered by interaction.collectIsHoveredAsState()
    val isPressed by interaction.collectIsPressedAsState()

    val color =
        when {
            isPressed -> {
                colors.pressed
            }

            isHovered -> {
                colors.hovered
            }

            else -> {
                colors.default
            }
        }
    Surface(
        color =
            if (isActive) {
                color.background
            } else {
                color.inactiveBackground
            },
        contentColor =
            if (isActive) {
                color.foreground
            } else {
                color.inactiveForeground
            },
        modifier =
            modifier.size(46.dp, 32.dp).clickable(
                onClick = onClick,
                interactionSource = interaction,
                indication = null,
            ),
        shape = RectangleShape,
    ) {
        val fontFamily = LocalCaptionIconFamily.current
        if (fontFamily != null) {
            Text(
                text = icon.glyph.toString(),
                fontFamily = fontFamily,
                textAlign = TextAlign.Center,
                fontSize = 10.sp,
                modifier = Modifier.fillMaxSize().wrapContentSize(Alignment.Center),
            )
        } else {
            Icon(
                painter = painterResource(icon.imageVector),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().wrapContentSize(Alignment.Center).size(13.dp),
            )
        }
    }
}

private object CaptionButtonDefaults {
    @Composable
    @Stable
    fun defaultColors(
        default: CaptionButtonColor =
            CaptionButtonColor(
                background = Color.Transparent,
                foreground = LocalWindowsColorScheme.current.textPrimaryColor,
                inactiveBackground = Color.Transparent,
                inactiveForeground = LocalWindowsColorScheme.current.textDisabledColor,
            ),
        hovered: CaptionButtonColor =
            default.copy(
                background = LocalWindowsColorScheme.current.fillSubtleSecondaryColor,
                inactiveBackground = LocalWindowsColorScheme.current.fillSubtleSecondaryColor,
                inactiveForeground = LocalWindowsColorScheme.current.textPrimaryColor,
            ),
        pressed: CaptionButtonColor =
            default.copy(
                background = LocalWindowsColorScheme.current.fillSubtleTertiaryColor,
                foreground = LocalWindowsColorScheme.current.textSecondaryColor,
                inactiveBackground = LocalWindowsColorScheme.current.fillSubtleTertiaryColor,
                inactiveForeground = LocalWindowsColorScheme.current.textTertiaryColor,
            ),
        disabled: CaptionButtonColor =
            default.copy(
                foreground = LocalWindowsColorScheme.current.textDisabledColor,
            ),
    ) = CaptionButtonColors(
        default = default,
        hovered = hovered,
        pressed = pressed,
        disabled = disabled,
    )

    @Composable
    @Stable
    fun closeColors(
        default: CaptionButtonColor =
            CaptionButtonColor(
                background = LocalWindowsColorScheme.current.fillSubtleTransparentColor,
                foreground = LocalWindowsColorScheme.current.textPrimaryColor,
                inactiveBackground = LocalWindowsColorScheme.current.fillSubtleTransparentColor,
                inactiveForeground = LocalWindowsColorScheme.current.textDisabledColor,
            ),
        hovered: CaptionButtonColor =
            default.copy(
                background = LocalWindowsColorScheme.current.shellCloseColor,
                foreground = Color.White,
                inactiveBackground = LocalWindowsColorScheme.current.shellCloseColor,
                inactiveForeground = Color.White,
            ),
        pressed: CaptionButtonColor =
            default.copy(
                background = LocalWindowsColorScheme.current.shellCloseColor.copy(0.9f),
                foreground = Color.White.copy(0.7f),
                inactiveBackground = LocalWindowsColorScheme.current.shellCloseColor.copy(0.9f),
                inactiveForeground = Color.White.copy(0.7f),
            ),
        disabled: CaptionButtonColor =
            default.copy(
                foreground = LocalWindowsColorScheme.current.textDisabledColor,
            ),
    ) = CaptionButtonColors(
        default = default,
        hovered = hovered,
        pressed = pressed,
        disabled = disabled,
    )
}

private data class WindowsColorScheme(
    val textPrimaryColor: Color,
    val textSecondaryColor: Color,
    val textTertiaryColor: Color,
    val textDisabledColor: Color,
    val fillSubtleTransparentColor: Color,
    val fillSubtleSecondaryColor: Color,
    val fillSubtleTertiaryColor: Color,
    val fillSubtleDisabledColor: Color,
    val shellCloseColor: Color = Color(0xFFC42B1C),
) {
    companion object {
        fun light() =
            WindowsColorScheme(
                textPrimaryColor = Color(0xE4000000),
                textSecondaryColor = Color(0x9B000000),
                textTertiaryColor = Color(0x72000000),
                textDisabledColor = Color(0x5C000000),
                fillSubtleTransparentColor = Color.Transparent,
                fillSubtleSecondaryColor = Color(0x09000000),
                fillSubtleTertiaryColor = Color(0x06000000),
                fillSubtleDisabledColor = Color.Transparent,
            )

        fun dark() =
            WindowsColorScheme(
                textPrimaryColor = Color(0xFFFFFFFF),
                textSecondaryColor = Color(0xC5FFFFFF),
                textTertiaryColor = Color(0x87FFFFFF),
                textDisabledColor = Color(0x5DFFFFFF),
                fillSubtleTransparentColor = Color.Transparent,
                fillSubtleSecondaryColor = Color(0x0FFFFFFF),
                fillSubtleTertiaryColor = Color(0x0AFFFFFF),
                fillSubtleDisabledColor = Color.Transparent,
            )
    }
}

private val LocalWindowsColorScheme = staticCompositionLocalOf { WindowsColorScheme.light() }
private val LocalCaptionIconFamily = staticCompositionLocalOf<FontFamily?> { null }

@Stable
private data class CaptionButtonColors(
    val default: CaptionButtonColor,
    val hovered: CaptionButtonColor,
    val pressed: CaptionButtonColor,
    val disabled: CaptionButtonColor,
)

@Stable
private data class CaptionButtonColor(
    val background: Color,
    val foreground: Color,
    val inactiveBackground: Color,
    val inactiveForeground: Color,
)

private enum class CaptionButtonIcon(
    val glyph: Char,
    val imageVector: DrawableResource,
) {
    Minimize(
        glyph = '\uE921',
        imageVector = Res.drawable.ic_fluent_subtract_48_filled,
    ),
    Maximize(
        glyph = '\uE922',
        imageVector = Res.drawable.ic_fluent_square_48_regular,
    ),
    Restore(
        glyph = '\uE923',
        imageVector = Res.drawable.ic_fluent_square_multiple_48_regular,
    ),
    BackToWindow(
        glyph = '\uE92C',
        imageVector = Res.drawable.ic_fluent_arrow_minimize_28_regular,
    ),
    Close(
        glyph = '\uE8BB',
        imageVector = Res.drawable.ic_fluent_dismiss_48_regular,
    ),

}

private fun Rect.contains(
    x: Float,
    y: Float,
): Boolean = x >= left && x < right && y >= top && y < bottom