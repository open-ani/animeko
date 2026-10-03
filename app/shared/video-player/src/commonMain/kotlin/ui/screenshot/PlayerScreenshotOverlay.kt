/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui.screenshot

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.video_player_screenshot_dismiss
import me.him188.ani.app.ui.lang.video_player_screenshot_share
import me.him188.ani.app.videoplayer.screenshot.SavedPlayerScreenshot
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

const val TAG_PLAYER_SCREENSHOT_FLASH = "playerScreenshotFlash"
const val TAG_PLAYER_SCREENSHOT_PANEL = "playerScreenshotPanel"
const val TAG_PLAYER_SCREENSHOT_THUMBNAIL = "playerScreenshotThumbnail"
const val TAG_PLAYER_SCREENSHOT_SHARE = "playerScreenshotShare"
const val TAG_PLAYER_SCREENSHOT_DISMISS = "playerScreenshotDismiss"

object PlayerScreenshotOverlayDefaults {
    /** 面板落定后到自动收起的时间. 鼠标悬停或按住面板时暂停计时. */
    val AutoDismissDelay: Duration = 6.seconds
}

private const val FLASH_PEAK_ALPHA = 0.8f
private const val FLASH_DURATION_MILLIS = 320

/** 截图先在原位停留, 让用户看清截到了什么, 再收进角落. */
private const val HOLD_MILLIS = 200
private const val MOVE_MILLIS = 450
private const val CHROME_MILLIS = 150
private const val FOLLOW_MILLIS = 250
private const val EXIT_MILLIS = 200
private const val EXIT_SCALE = 0.85f
private val DISMISS_TICK = 100.milliseconds

/** M3 emphasized decelerate: 进入角落时快速起步、缓慢落定. */
private val MoveEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

private val PanelMargin = 16.dp
private val PanelBorderWidth = 1.dp
private val ThumbnailCornerRadius = 16.dp
private val PillHeight = 36.dp

/** 分享按钮塞进缩略图下沿的深度, 使两者的轮廓连成一体. */
private val PillOverlap = 12.dp
private val CloseButtonSize = 24.dp
private val CompactThumbnailHeight = 88.dp
private val ThumbnailHeight = 112.dp

/** 播放器区域高度低于此值时缩略图使用 [CompactThumbnailHeight]. */
private val CompactPlayerHeight = 480.dp
private const val MAX_THUMBNAIL_WIDTH_FRACTION = 0.4f

/**
 * 截图成功后的反馈层, 覆盖整个播放器区域; 面板以外的区域不拦截输入, 手势和控制器照常工作.
 *
 * 1. 播放器区域白色闪光一次;
 * 2. 截图先原位停留片刻, 再以容器变换收进角落: 竖屏布局 (区域高大于宽) 右下角, 横屏布局左下角;
 * 3. 落定后长出面板外壳: 缩略图与分享按钮连成一体的异形面板, 外侧上角有关闭按钮;
 * 4. [autoDismissDelay] 后自动收起, 鼠标悬停或按住面板时暂停计时; 用户也可点关闭.
 *
 * 再次截图会替换面板内容并重放整个流程.
 *
 * @param onShare 点击分享按钮
 * @param onOpen 点击缩略图
 * @param bottomOffset 面板需要避开的底部控制栏高度; 控制栏隐藏时传 0, 面板随之下移
 * @param windowInsets 面板需要避开的系统栏; 变换起点仍与视频区域对齐
 */
@Composable
fun PlayerScreenshotOverlay(
    state: PlayerScreenshotPanelState,
    onShare: (SavedPlayerScreenshot) -> Unit,
    onOpen: (SavedPlayerScreenshot) -> Unit,
    modifier: Modifier = Modifier,
    bottomOffset: Dp = 0.dp,
    windowInsets: WindowInsets = WindowInsets(0),
    autoDismissDelay: Duration = PlayerScreenshotOverlayDefaults.AutoDismissDelay,
) {
    BoxWithConstraints(modifier) {
        ScreenshotFlash(trigger = state.sequence, Modifier.matchParentSize())
        val presentation = state.current
        if (presentation != null) {
            key(presentation.sequence) {
                ScreenshotPresentation(
                    presentation = presentation,
                    playerSize = IntSize(constraints.maxWidth, constraints.maxHeight),
                    bottomOffset = bottomOffset,
                    windowInsets = windowInsets,
                    autoDismissDelay = autoDismissDelay,
                    onShare = { onShare(presentation.screenshot) },
                    onOpen = { onOpen(presentation.screenshot) },
                    onDismissed = { state.dismiss(presentation) },
                )
            }
        }
    }
}

@Composable
private fun ScreenshotFlash(trigger: Int, modifier: Modifier) {
    // 组合此层时已经存在的截图不再闪光, 只有之后发生的截图才触发
    val initialTrigger = remember { trigger }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger == initialTrigger) return@LaunchedEffect
        alpha.snapTo(FLASH_PEAK_ALPHA)
        alpha.animateTo(0f, tween(FLASH_DURATION_MILLIS, easing = LinearOutSlowInEasing))
    }
    if (alpha.value > 0f) {
        Box(
            modifier
                .testTag(TAG_PLAYER_SCREENSHOT_FLASH)
                .graphicsLayer { this.alpha = alpha.value }
                .background(Color.White),
        )
    }
}

private enum class PanelPhase {
    /** 截图铺在视频区域上原位停留. */
    Hold,

    /** 正在收进角落. */
    Moving,

    /** 停在角落, 外壳与按钮可见. */
    Docked,

    /** 正在收起. */
    Leaving,
}

@Composable
private fun ScreenshotPresentation(
    presentation: PlayerScreenshotPresentation,
    playerSize: IntSize,
    bottomOffset: Dp,
    windowInsets: WindowInsets,
    autoDismissDelay: Duration,
    onShare: () -> Unit,
    onOpen: () -> Unit,
    onDismissed: () -> Unit,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val insetPadding = windowInsets.asPaddingValues()
    val screenshot = presentation.screenshot
    val geometry = remember(playerSize, screenshot, insetPadding, bottomOffset, density, layoutDirection) {
        with(density) {
            val playerHeight = playerSize.height.toDp()
            computePlayerScreenshotPanelGeometry(
                playerSize = playerSize,
                imageSize = IntSize(screenshot.image.width, screenshot.image.height),
                insets = PlayerScreenshotPanelInsets(
                    left = insetPadding.calculateLeftPadding(layoutDirection).toPx(),
                    top = insetPadding.calculateTopPadding().toPx(),
                    right = insetPadding.calculateRightPadding(layoutDirection).toPx(),
                    bottom = insetPadding.calculateBottomPadding().toPx(),
                ),
                margin = PanelMargin.toPx(),
                bottomOffset = bottomOffset.toPx(),
                thumbnailHeight = (if (playerHeight < CompactPlayerHeight) CompactThumbnailHeight else ThumbnailHeight).toPx(),
                maxThumbnailWidth = playerSize.width * MAX_THUMBNAIL_WIDTH_FRACTION,
            )
        }
    }
    val latestGeometry by rememberUpdatedState(geometry)
    val thumbnailCornerPx = with(density) { ThumbnailCornerRadius.toPx() }

    val startDocked = presentation.hasDocked
    val rect = remember {
        Animatable(if (startDocked) geometry.thumbnailRect else geometry.startRect, Rect.VectorConverter)
    }
    val cornerRadius = remember { Animatable(if (startDocked) thumbnailCornerPx else 0f) }
    val chromeAlpha = remember { Animatable(if (startDocked) 1f else 0f) }
    val scale = remember { Animatable(1f) }
    var phase by remember { mutableStateOf(if (startDocked) PanelPhase.Docked else PanelPhase.Hold) }
    var dismissRequested by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()

    // 入场: 原位停留 -> 收进角落 -> 长出外壳 -> 倒计时自动收起
    LaunchedEffect(Unit) {
        if (phase != PanelPhase.Docked) {
            delay(HOLD_MILLIS.toLong())
            phase = PanelPhase.Moving
            coroutineScope {
                launch { rect.animateTo(latestGeometry.thumbnailRect, tween(MOVE_MILLIS, easing = MoveEasing)) }
                launch { cornerRadius.animateTo(thumbnailCornerPx, tween(MOVE_MILLIS, easing = MoveEasing)) }
            }
            phase = PanelPhase.Docked
            presentation.hasDocked = true
            chromeAlpha.animateTo(1f, tween(CHROME_MILLIS))
        }
        var remaining = autoDismissDelay
        while (remaining > Duration.ZERO) {
            delay(DISMISS_TICK)
            if (!hovered && !pressed) remaining -= DISMISS_TICK
        }
        dismissRequested = true
    }
    // 停靠后目标位置变化 (控制栏显隐、区域大小变化) 时跟随
    LaunchedEffect(geometry, phase) {
        if (phase == PanelPhase.Docked && rect.targetValue != geometry.thumbnailRect) {
            rect.animateTo(geometry.thumbnailRect, tween(FOLLOW_MILLIS))
        }
    }
    // 退场: 外壳淡出, 整体向停靠的角缩小
    LaunchedEffect(dismissRequested) {
        if (!dismissRequested) return@LaunchedEffect
        phase = PanelPhase.Leaving
        coroutineScope {
            launch { chromeAlpha.animateTo(0f, tween(EXIT_MILLIS)) }
            launch { scale.animateTo(EXIT_SCALE, tween(EXIT_MILLIS)) }
        }
        onDismissed()
    }

    ScreenshotPanel(
        screenshot = screenshot,
        corner = geometry.corner,
        thumbnailRect = { rect.value },
        cornerRadius = { cornerRadius.value },
        chromeAlpha = { chromeAlpha.value },
        scale = { scale.value },
        chromeVisible = phase == PanelPhase.Docked,
        interactionSource = interactionSource,
        onShare = onShare,
        onOpen = onOpen,
        onDismiss = { dismissRequested = true },
    )
}

private const val THUMBNAIL_ID = "thumbnail"
private const val PILL_ID = "pill"
private const val CLOSE_ID = "close"

/** 面板各部分在面板自身坐标系中的位置. 测量阶段写入, 放置与绘制阶段读取. */
private class PanelFrame {
    var thumbnail: Rect = Rect.Zero
    var pill: Rect = Rect.Zero
}

/**
 * 异形面板: 缩略图停在 [thumbnailRect]; 分享按钮从缩略图下沿向屏幕中央伸出, 一部分塞在缩略图下面,
 * 两者共用一块底色与一圈边框, 轮廓是两个圆角矩形的并集; 关闭按钮骑在缩略图外侧上角.
 *
 * 入场期间 ([chromeAlpha] 为 0) 只画缩略图本身, 即正在移动的截图.
 */
@Composable
private fun ScreenshotPanel(
    screenshot: SavedPlayerScreenshot,
    corner: PlayerScreenshotPanelCorner,
    thumbnailRect: () -> Rect,
    cornerRadius: () -> Float,
    chromeAlpha: () -> Float,
    scale: () -> Float,
    chromeVisible: Boolean,
    interactionSource: MutableInteractionSource,
    onShare: () -> Unit,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    val pillHeightPx = with(density) { PillHeight.roundToPx() }
    val pillOverlapPx = with(density) { PillOverlap.roundToPx() }
    val closeSizePx = with(density) { CloseButtonSize.roundToPx() }
    val closeOverhangPx = closeSizePx / 2
    val borderWidthPx = with(density) { PanelBorderWidth.toPx() }
    val panelColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    val frame = remember { PanelFrame() }
    val indication = LocalIndication.current
    val shareLabel = stringResource(Lang.video_player_screenshot_share)
    val dismissLabel = stringResource(Lang.video_player_screenshot_dismiss)

    Layout(
        content = {
            Image(
                bitmap = screenshot.image,
                contentDescription = null,
                modifier = Modifier
                    .layoutId(THUMBNAIL_ID)
                    .testTag(TAG_PLAYER_SCREENSHOT_THUMBNAIL)
                    .graphicsLayer {
                        shape = RoundedCornerShape(cornerRadius())
                        clip = true
                    }
                    .clickable(interactionSource, indication, enabled = chromeVisible, onClick = onOpen),
                contentScale = ContentScale.Fit,
            )
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                Row(
                    Modifier
                        .layoutId(PILL_ID)
                        .testTag(TAG_PLAYER_SCREENSHOT_SHARE)
                        .graphicsLayer { alpha = chromeAlpha() }
                        .clip(RoundedCornerShape(50))
                        .clickable(
                            interactionSource,
                            indication,
                            enabled = chromeVisible,
                            onClickLabel = shareLabel,
                            onClick = onShare,
                        )
                        .padding(
                            // 塞在缩略图下面的那段不放内容
                            start = if (corner == PlayerScreenshotPanelCorner.BottomLeft) PillOverlap + 10.dp else 14.dp,
                            end = if (corner == PlayerScreenshotPanelCorner.BottomLeft) 14.dp else PillOverlap + 10.dp,
                        ),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = null, Modifier.size(18.dp))
                    Text(shareLabel, style = MaterialTheme.typography.labelLarge)
                }
                Box(
                    Modifier
                        .layoutId(CLOSE_ID)
                        .testTag(TAG_PLAYER_SCREENSHOT_DISMISS)
                        .graphicsLayer { alpha = chromeAlpha() }
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .border(PanelBorderWidth, borderColor, CircleShape)
                        .clickable(
                            interactionSource,
                            indication,
                            enabled = chromeVisible,
                            onClickLabel = dismissLabel,
                            onClick = onDismiss,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = dismissLabel, Modifier.size(14.dp))
                }
            }
        },
        modifier = Modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(Constraints())
                layout(constraints.maxWidth, constraints.maxHeight) {
                    // 放置后缩略图子项正好落在目标矩形上
                    val target = thumbnailRect()
                    placeable.place(
                        (target.left - frame.thumbnail.left).roundToInt(),
                        (target.top - frame.thumbnail.top).roundToInt(),
                    )
                }
            }
            .testTag(TAG_PLAYER_SCREENSHOT_PANEL)
            .graphicsLayer {
                scaleX = scale()
                scaleY = scale()
                transformOrigin = when (corner) {
                    PlayerScreenshotPanelCorner.BottomLeft -> TransformOrigin(0f, 1f)
                    PlayerScreenshotPanelCorner.BottomRight -> TransformOrigin(1f, 1f)
                }
            }
            .hoverable(interactionSource)
            .drawBehind {
                val alpha = chromeAlpha()
                if (alpha <= 0f) return@drawBehind
                val outline = Path.combine(
                    PathOperation.Union,
                    Path().apply { addRoundRect(RoundRect(frame.thumbnail, CornerRadius(cornerRadius()))) },
                    Path().apply { addRoundRect(RoundRect(frame.pill, CornerRadius(frame.pill.height / 2))) },
                )
                drawPath(outline, panelColor, alpha = alpha)
                drawPath(outline, borderColor, alpha = alpha, style = Stroke(borderWidthPx))
            },
    ) { measurables, constraints ->
        val target = thumbnailRect()
        val thumbnailWidth = target.width.roundToInt().coerceAtLeast(1)
        val thumbnailHeight = target.height.roundToInt().coerceAtLeast(1)
        val thumbnail = measurables.first { it.layoutId == THUMBNAIL_ID }
            .measure(Constraints.fixed(thumbnailWidth, thumbnailHeight))
        val pill = measurables.first { it.layoutId == PILL_ID }
            .measure(Constraints(minHeight = pillHeightPx, maxHeight = pillHeightPx))
        val close = measurables.first { it.layoutId == CLOSE_ID }
            .measure(Constraints.fixed(closeSizePx, closeSizePx))

        val pillExtension = (pill.width - pillOverlapPx).coerceAtLeast(0)
        val thumbnailX = when (corner) {
            PlayerScreenshotPanelCorner.BottomLeft -> closeOverhangPx
            PlayerScreenshotPanelCorner.BottomRight -> pillExtension
        }
        val thumbnailY = closeOverhangPx
        val pillX = when (corner) {
            PlayerScreenshotPanelCorner.BottomLeft -> thumbnailX + thumbnailWidth - pillOverlapPx
            PlayerScreenshotPanelCorner.BottomRight -> thumbnailX + pillOverlapPx - pill.width
        }
        val pillY = thumbnailY + thumbnailHeight - pill.height
        val closeX = when (corner) {
            PlayerScreenshotPanelCorner.BottomLeft -> thumbnailX - closeOverhangPx
            PlayerScreenshotPanelCorner.BottomRight -> thumbnailX + thumbnailWidth - closeSizePx + closeOverhangPx
        }
        frame.thumbnail = Rect(
            thumbnailX.toFloat(),
            thumbnailY.toFloat(),
            (thumbnailX + thumbnailWidth).toFloat(),
            (thumbnailY + thumbnailHeight).toFloat(),
        )
        frame.pill = Rect(
            pillX.toFloat(),
            pillY.toFloat(),
            (pillX + pill.width).toFloat(),
            (pillY + pill.height).toFloat(),
        )

        val width = closeOverhangPx + thumbnailWidth + pillExtension
        val height = closeOverhangPx + thumbnailHeight
        layout(width, height) {
            // 先放分享按钮, 缩略图盖在它塞进来的那段上面
            pill.place(pillX, pillY)
            thumbnail.place(thumbnailX, thumbnailY)
            close.place(closeX, 0)
        }
    }
}
