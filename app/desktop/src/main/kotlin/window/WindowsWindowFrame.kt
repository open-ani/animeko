/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.him188.ani.app.platform.PlatformWindow
import me.him188.ani.app.platform.window.LayoutHitTestOwner
import me.him188.ani.app.platform.window.LocalTitleBarThemeController
import me.him188.ani.app.platform.window.TitleBarThemeController
import me.him188.ani.app.platform.window.WindowsWindowHitResult
import me.him188.ani.app.platform.window.WindowsWindowUtils
import me.him188.ani.app.ui.foundation.ifThen
import me.him188.ani.app.ui.foundation.layout.LocalCaptionButtonInsets
import me.him188.ani.app.ui.foundation.layout.LocalPlatformWindow
import me.him188.ani.app.ui.foundation.layout.LocalTitleBarInsets
import me.him188.ani.app.ui.foundation.layout.ZeroInsets
import me.him188.ani.app.ui.foundation.layout.isSystemInFullscreen
import me.him188.ani.desktop.generated.resources.Res
import me.him188.ani.desktop.generated.resources.ic_fluent_arrow_minimize_28_regular
import me.him188.ani.desktop.generated.resources.ic_fluent_dismiss_48_regular
import me.him188.ani.desktop.generated.resources.ic_fluent_square_48_regular
import me.him188.ani.desktop.generated.resources.ic_fluent_square_multiple_48_regular
import me.him188.ani.desktop.generated.resources.ic_fluent_subtract_48_filled
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import kotlin.time.Duration.Companion.seconds

@Composable
internal fun FrameWindowScope.WindowsWindowFrame(
    windowState: WindowState,
    frameState: WindowsWindowFrameState? = rememberWindowsWindowFrameState(),
    onCloseRequest: () -> Unit,
    content: @Composable () -> Unit
) {
    if (frameState == null) {
        content()
        return
    }

    val platformWindow = LocalPlatformWindow.current
    val windowUtils = WindowsWindowUtils.instance
    val scope = rememberCoroutineScope()

    //Keep 1px for showing float window top area border.
    val topBorderFixedInsets by remember(platformWindow) {
        derivedStateOf {
            if (!platformWindow.isUndecoratedFullscreen) WindowInsets(top = 1) else ZeroInsets
        }
    }
    Box(modifier = Modifier.fillMaxSize().windowInsetsPadding(topBorderFixedInsets)) {
        //Control the visibility of the title bar. initial value is !isFullScreen.
        LaunchedEffect(platformWindow.isUndecoratedFullscreen) {
            frameState.isTitleBarVisible = !platformWindow.isUndecoratedFullscreen
        }

        // Window content
        CompositionLocalProvider(
            LocalTitleBarInsets provides frameState.titleBarInsets,
            LocalCaptionButtonInsets provides frameState.captionButtonsInsets,
            LocalTitleBarThemeController provides frameState.titleBarThemeController,
            content = content,
        )

        // Hide title bar if window is full screen mode and title bar is not hovered.
        val titleBarInteractionSource = remember(platformWindow.isUndecoratedFullscreen) { MutableInteractionSource() }
        val titleBarHovered by titleBarInteractionSource.collectIsHoveredAsState()
        LaunchedEffect(titleBarInteractionSource, titleBarHovered, platformWindow.isUndecoratedFullscreen) {
            if (!titleBarHovered && platformWindow.isUndecoratedFullscreen) {
                delay(3.seconds)
                frameState.isTitleBarVisible = false
            }
        }
        //Extend window content to title bar.
        ExtendToTitleBar(frameState)
        //Draw Compose Windows title bar.
        AnimatedVisibility(
            visible = frameState.isTitleBarVisible,
            modifier = Modifier
                .ifThen(frameState.isTitleBarVisible && platformWindow.isUndecoratedFullscreen) {
                    hoverable(titleBarInteractionSource)
                }
                .fillMaxWidth()
                .onSizeChanged(frameState::updateTitleBarInsets)
                .wrapContentWidth(AbsoluteAlignment.Right),
        ) {
            Row(
                modifier = Modifier.onSizeChanged(frameState::updateCaptionButtonsInset),
            ) {
                CompositionLocalProvider(
                    LocalCaptionIconFamily provides rememberFontIconFamily().value,
                    LocalWindowsColorScheme provides if (frameState.titleBarThemeController.isDark) {
                        WindowsColorScheme.dark()
                    } else {
                        WindowsColorScheme.light()
                    },
                ) {
                    CaptionButtonRow(
                        frameState = frameState,
                        isMaximize = windowState.placement == WindowPlacement.Maximized,
                        onMinimizeRequest = { windowUtils.minimizeWindow(window.windowHandle) },
                        onMaximizeRequest = { windowUtils.maximizeWindow(window.windowHandle) },
                        onRestoreRequest = { windowUtils.restoreWindow(window.windowHandle) },
                        onExitFullscreenRequest = {
                            scope.launch {
                                windowUtils.setUndecoratedFullscreen(platformWindow, windowState, false)
                            }
                        },
                        onCloseRequest = onCloseRequest,
                        onMaximizeButtonRectUpdate = frameState::updateMaximizeButtonRect,
                        onMinimizeButtonRectUpdate = frameState::updateMinimizeButtonRect,
                        onCloseButtonRectUpdate = frameState::updateCloseButtonRect,
                    )
                }
            }
        }

        //Auto hoverable area that can be used to show title bar when title bar is hidden.
        if (!frameState.isTitleBarVisible) {
            val awareAreaInteractionSource = remember { MutableInteractionSource() }
            val isAwareHovered by awareAreaInteractionSource.collectIsHoveredAsState()
            LaunchedEffect(isAwareHovered) {
                if (isAwareHovered) {
                    frameState.isTitleBarVisible = true
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
internal fun rememberWindowsWindowFrameState(): WindowsWindowFrameState? {
    val platformWindow = LocalPlatformWindow.current
    val layoutHitTestOwner = platformWindow.layoutHitTestOwner ?: return null
    return remember(platformWindow, layoutHitTestOwner) { WindowsWindowFrameState(platformWindow, layoutHitTestOwner) }
}

internal class WindowsWindowFrameState(
    internal val platformWindow: PlatformWindow,
    private val layoutHitTestOwner: LayoutHitTestOwner,
) {
    val titleBarThemeController = TitleBarThemeController()

    var isTitleBarVisible by mutableStateOf(true)

    //0 is minimize, 1 is maximize, 2 is close
    private val captionButtonsRect = Array(3) { Rect.Zero }

    private val _titleBarInsets = MutableWindowInsets()
    private val _captionButtonsInsets = MutableWindowInsets()

    inline val titleBarInsets: WindowInsets
        get() = if (isTitleBarVisible) {
            _titleBarInsets
        } else {
            ZeroInsets
        }

    inline val captionButtonsInsets: WindowInsets
        get() = if (isTitleBarVisible) {
            _captionButtonsInsets
        } else {
            ZeroInsets
        }

    fun updateMinimizeButtonRect(rect: Rect) {
        captionButtonsRect[0] = rect
    }

    fun updateMaximizeButtonRect(rect: Rect) {
        captionButtonsRect[1] = rect
    }

    fun updateCloseButtonRect(rect: Rect) {
        captionButtonsRect[2] = rect
    }

    fun updateCaptionButtonsInset(size: IntSize) {
        _captionButtonsInsets.insets = WindowInsets(right = size.width, top = size.height)
    }

    fun updateTitleBarInsets(size: IntSize) {
        _titleBarInsets.insets = WindowInsets(top = size.height)
    }

    fun hitTest(x: Float, y: Float, density: Density) = when {
        captionButtonsRect[0].contains(x, y) -> WindowsWindowHitResult.CAPTION_MIN
        captionButtonsRect[1].contains(x, y) -> WindowsWindowHitResult.CAPTION_MAX
        captionButtonsRect[2].contains(x, y) -> WindowsWindowHitResult.CAPTION_CLOSE
        y <= titleBarInsets.getTop(density) && !layoutHitTestOwner.hitTest(x, y) -> WindowsWindowHitResult.CAPTION
        else -> WindowsWindowHitResult.CLIENT
    }

    @Composable
    fun collectWindowIsActive(): State<Boolean> {
        return remember(platformWindow) {
            WindowsWindowUtils.instance.windowIsActive(platformWindow).map { it != false }
        }.collectAsState(false)
    }

}

@Composable
private fun ExtendToTitleBar(frameState: WindowsWindowFrameState) {
    val density = LocalDensity.current
    val platformWindow = LocalPlatformWindow.current
    LaunchedEffect(platformWindow, density, frameState) {
        WindowsWindowUtils.instance.collectWindowProcHitTestProvider(platformWindow) { x, y ->
            if (!platformWindow.isUndecoratedFullscreen) {
                frameState.hitTest(x, y, density)
            } else {
                WindowsWindowHitResult.CLIENT
            }
        }
    }
}

@Composable
private fun WindowsWindowFrameState.collectCaptionButtonColors(): CaptionButtonColors {
    val isAccentColorFrameEnabled = remember(platformWindow) {
        WindowsWindowUtils.instance.frameIsColorful(platformWindow)
    }.collectAsState(false)
    return if (isAccentColorFrameEnabled.value) {
        val accentColor = LocalPlatformWindow.current.accentColor.collectAsState(Color.Unspecified)
        if (accentColor.value != Color.Unspecified) {
            CaptionButtonDefaults.accentColors(seedColor = accentColor.value)
        } else {
            CaptionButtonDefaults.defaultColors()
        }
    } else {
        CaptionButtonDefaults.defaultColors()
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
private fun rememberFontIconFamily(): State<FontFamily?> {
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
    return fontIconFamily
}

@Composable
private fun CaptionButtonRow(
    frameState: WindowsWindowFrameState,
    isMaximize: Boolean,
    onMinimizeRequest: () -> Unit,
    onMaximizeRequest: () -> Unit,
    onRestoreRequest: () -> Unit,
    onExitFullscreenRequest: () -> Unit,
    onCloseRequest: () -> Unit,
    modifier: Modifier = Modifier,
    onMinimizeButtonRectUpdate: (Rect) -> Unit,
    onMaximizeButtonRectUpdate: (Rect) -> Unit,
    onCloseButtonRectUpdate: (Rect) -> Unit,
) {
    val captionButtonColors = frameState.collectCaptionButtonColors()
    val isActive by frameState.collectWindowIsActive()
    Row(
        horizontalArrangement = Arrangement.aligned(AbsoluteAlignment.Right),
        modifier = modifier
            .zIndex(1f),
    ) {
        CaptionButton(
            onClick = onMinimizeRequest,
            icon = CaptionButtonIcon.Minimize,
            isActive = isActive,
            colors = captionButtonColors,
            modifier = Modifier.onGloballyPositioned { onMinimizeButtonRectUpdate(it.boundsInWindow()) },
        )
        val isFullScreen = isSystemInFullscreen()
        CaptionButton(
            onClick = when {
                isFullScreen -> onExitFullscreenRequest
                isMaximize -> onRestoreRequest
                else -> onMaximizeRequest
            },
            icon = when {
                isFullScreen -> CaptionButtonIcon.BackToWindow
                isMaximize -> CaptionButtonIcon.Restore
                else -> CaptionButtonIcon.Maximize
            },
            isActive = isActive,
            colors = captionButtonColors,
            modifier = Modifier.onGloballyPositioned {
                onMaximizeButtonRectUpdate(it.boundsInWindow())
            },
        )
        CaptionButton(
            icon = CaptionButtonIcon.Close,
            onClick = onCloseRequest,
            isActive = isActive,
            colors = CaptionButtonDefaults.closeColors(),
            modifier = Modifier.onGloballyPositioned { onCloseButtonRectUpdate(it.boundsInWindow()) },
        )
    }
}

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
        color = if (isActive) color.background else color.inactiveBackground,
        contentColor = if (isActive) color.foreground else color.inactiveForeground,
        modifier = modifier
            .size(46.dp, 32.dp)
            .clickable(
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
    fun closeColors() = accentColors(seedColor = LocalWindowsColorScheme.current.shellCloseColor)

    @Composable
    @Stable
    fun accentColors(
        seedColor: Color,
        default: CaptionButtonColor =
            CaptionButtonColor(
                background = LocalWindowsColorScheme.current.fillSubtleTransparentColor,
                foreground = LocalWindowsColorScheme.current.textPrimaryColor,
                inactiveBackground = LocalWindowsColorScheme.current.fillSubtleTransparentColor,
                inactiveForeground = LocalWindowsColorScheme.current.textDisabledColor,
            ),
        hovered: CaptionButtonColor =
            default.copy(
                background = seedColor,
                foreground = Color.White,
                inactiveBackground = seedColor,
                inactiveForeground = Color.White,
            ),
        pressed: CaptionButtonColor =
            default.copy(
                background = seedColor.copy(0.9f),
                foreground = Color.White.copy(0.7f),
                inactiveBackground = seedColor.copy(0.9f),
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