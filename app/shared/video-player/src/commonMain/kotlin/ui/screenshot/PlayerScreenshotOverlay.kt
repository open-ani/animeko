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
import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.preference.DarkMode
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.video_player_screenshot_copy
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
const val TAG_PLAYER_SCREENSHOT_COPY = "playerScreenshotCopy"

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

/** 面板与播放器区域边缘的间距. */
private val PanelMargin = 16.dp
private val PanelCornerRadius = 20.dp

/** A 区域内画面到边缘的留白. 画面圆角等于 [PanelCornerRadius] 减去此值, 与 A 区域同心. */
private val ImagePadding = 8.dp
private val PanelElevation = 6.dp
private val ActionButtonSize = 40.dp
private val ActionButtonGap = 8.dp

/** B 区域内动作按钮到边缘的留白. */
private val LobePadding = 8.dp

/** B 区域向屏幕中央伸出 A 以外的长度: 正好放下两个动作按钮. */
private val LobeExtension = LobePadding * 2 + ActionButtonSize * 2 + ActionButtonGap

/** B 区域上边与 A 区域侧边之间内凹圆角的半径. */
private val NeckRadius = 12.dp
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
 * 3. 落定后长出面板: 画面所在的 A 区域的下角向屏幕中央伸出药丸形的 B 区域, 里面是分享与复制两个圆形图标按钮,
 *    整块由一条闭合路径画成带阴影的异形面板;
 * 4. [autoDismissDelay] 后自动收起, 鼠标悬停或按住面板时暂停计时; 点击画面会打开它并立即收起面板.
 *
 * 再次截图会替换面板内容并重放整个流程.
 *
 * @param onShare 点击分享按钮
 * @param onCopy 点击复制按钮
 * @param onOpen 点击画面; 面板随即收起
 * @param bottomOffset 面板需要避开的底部控制栏高度; 控制栏隐藏时传 0, 面板随之下移
 * @param windowInsets 面板需要避开的系统栏; 变换起点仍与视频区域对齐
 */
@Composable
fun PlayerScreenshotOverlay(
    state: PlayerScreenshotPanelState,
    onShare: (SavedPlayerScreenshot) -> Unit,
    onCopy: (SavedPlayerScreenshot) -> Unit,
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
                    onCopy = { onCopy(presentation.screenshot) },
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
    onCopy: () -> Unit,
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
                // 几何算的是画面矩形; A 区域比它大一圈留白, 让 A 的边缘落在 PanelMargin 处
                margin = (PanelMargin + ImagePadding).toPx(),
                bottomOffset = bottomOffset.toPx(),
                thumbnailHeight = (if (playerHeight < CompactPlayerHeight) CompactThumbnailHeight else ThumbnailHeight).toPx(),
                maxThumbnailWidth = playerSize.width * MAX_THUMBNAIL_WIDTH_FRACTION,
            )
        }
    }
    val latestGeometry by rememberUpdatedState(geometry)
    val imageCornerPx = with(density) { (PanelCornerRadius - ImagePadding).toPx() }

    val startDocked = presentation.hasDocked
    val rect = remember {
        Animatable(if (startDocked) geometry.thumbnailRect else geometry.startRect, Rect.VectorConverter)
    }
    val cornerRadius = remember { Animatable(if (startDocked) imageCornerPx else 0f) }
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
                launch { cornerRadius.animateTo(imageCornerPx, tween(MOVE_MILLIS, easing = MoveEasing)) }
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
        onCopy = onCopy,
        onOpen = {
            onOpen()
            // 打开画面后面板的任务就完成了
            dismissRequested = true
        },
    )
}

private const val BACKGROUND_ID = "background"
private const val IMAGE_ID = "image"
private const val SHARE_ID = "share"
private const val COPY_ID = "copy"

/** 面板各部分在面板自身坐标系中的位置. 测量阶段写入, 放置与绘制阶段读取. */
private class PanelFrame {
    /** A 区域: 容纳画面的圆角矩形. */
    var regionA: Rect = Rect.Zero

    /** B 区域: 与 A 底边对齐、从 A 的下角向屏幕中央伸出的药丸, 一端藏在 A 里, 伸出的部分容纳动作按钮. */
    var regionB: Rect = Rect.Zero

    /** 画面在 A 区域内的位置. */
    var image: Rect = Rect.Zero
}

/**
 * 异形面板. A 区域是一个圆角矩形, 画面落在其中、四周留 [ImagePadding] 的边, 画面本身就是 [thumbnailRect];
 * B 区域是与 A 底边对齐、从 A 的下角向屏幕中央伸出 [LobeExtension] 的药丸, 伸出的部分里是分享与复制两个圆形图标按钮,
 * 靠 A 的是分享. 整块面板是 [screenshotPanelOutline] 描出的一条闭合路径做成的 [GenericShape], 填 surfaceContainer 并带阴影:
 * A 与 B 的底边连成一条直线, B 的上边以内凹圆角接到 A 的侧边.
 *
 * 入场期间 ([chromeAlpha] 为 0) 只画画面本身, 即正在移动的截图.
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
    onCopy: () -> Unit,
    onOpen: () -> Unit,
) {
    val density = LocalDensity.current
    val imagePaddingPx = with(density) { ImagePadding.roundToPx() }
    val panelCornerPx = with(density) { PanelCornerRadius.toPx() }
    val buttonSizePx = with(density) { ActionButtonSize.roundToPx() }
    val buttonGapPx = with(density) { ActionButtonGap.roundToPx() }
    val lobePaddingPx = with(density) { LobePadding.roundToPx() }
    val neckRadiusPx = with(density) { NeckRadius.toPx() }
    val lobeHeightPx = buttonSizePx + 2 * lobePaddingPx
    val extensionPx = 2 * lobePaddingPx + 2 * buttonSizePx + buttonGapPx
    // B 藏进 A 里的深度取 B 的半径, 朝 A 的那个圆头完全藏在 A 里
    val lobeOverlapPx = lobeHeightPx / 2
    val panelColor = MaterialTheme.colorScheme.surfaceContainer
    val buttonColor = MaterialTheme.colorScheme.surfaceContainerLowest
    val frame = remember { PanelFrame() }
    val panelShape = remember(frame, panelCornerPx, neckRadiusPx) {
        GenericShape { _, _ ->
            addPath(screenshotPanelOutline(frame.regionA, frame.regionB, panelCornerPx, neckRadiusPx))
        }
    }
    val indication = LocalIndication.current
    val shareLabel = stringResource(Lang.video_player_screenshot_share)
    val copyLabel = stringResource(Lang.video_player_screenshot_copy)

    Layout(
        content = {
            // 面板底: 阴影与底色随外壳一起淡入, 画面移动期间不可见
            Box(
                Modifier
                    .layoutId(BACKGROUND_ID)
                    .graphicsLayer { alpha = chromeAlpha() }
                    .shadow(PanelElevation, panelShape, clip = false)
                    .background(panelColor, panelShape),
            )
            Image(
                bitmap = screenshot.image,
                contentDescription = null,
                modifier = Modifier
                    .layoutId(IMAGE_ID)
                    .testTag(TAG_PLAYER_SCREENSHOT_THUMBNAIL)
                    .graphicsLayer {
                        shape = RoundedCornerShape(cornerRadius())
                        clip = true
                    }
                    .clickable(interactionSource, indication, enabled = chromeVisible, onClick = onOpen),
                contentScale = ContentScale.Fit,
            )
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                PanelCircleButton(
                    icon = Icons.Rounded.Share,
                    label = shareLabel,
                    color = buttonColor,
                    iconSize = 20.dp,
                    alpha = chromeAlpha,
                    enabled = chromeVisible,
                    interactionSource = interactionSource,
                    indication = indication,
                    onClick = onShare,
                    modifier = Modifier.layoutId(SHARE_ID).testTag(TAG_PLAYER_SCREENSHOT_SHARE),
                )
                PanelCircleButton(
                    icon = Icons.Rounded.ContentCopy,
                    label = copyLabel,
                    color = buttonColor,
                    iconSize = 20.dp,
                    alpha = chromeAlpha,
                    enabled = chromeVisible,
                    interactionSource = interactionSource,
                    indication = indication,
                    onClick = onCopy,
                    modifier = Modifier.layoutId(COPY_ID).testTag(TAG_PLAYER_SCREENSHOT_COPY),
                )
            }
        },
        modifier = Modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(Constraints())
                layout(constraints.maxWidth, constraints.maxHeight) {
                    // 放置后画面子项正好落在目标矩形上
                    val target = thumbnailRect()
                    placeable.place(
                        (target.left - frame.image.left).roundToInt(),
                        (target.top - frame.image.top).roundToInt(),
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
            .hoverable(interactionSource),
    ) { measurables, constraints ->
        val target = thumbnailRect()
        val imageWidth = target.width.roundToInt().coerceAtLeast(1)
        val imageHeight = target.height.roundToInt().coerceAtLeast(1)
        val image = measurables.first { it.layoutId == IMAGE_ID }
            .measure(Constraints.fixed(imageWidth, imageHeight))
        val share = measurables.first { it.layoutId == SHARE_ID }
            .measure(Constraints.fixed(buttonSizePx, buttonSizePx))
        val copy = measurables.first { it.layoutId == COPY_ID }
            .measure(Constraints.fixed(buttonSizePx, buttonSizePx))

        val regionAWidth = imageWidth + 2 * imagePaddingPx
        val regionAHeight = imageHeight + 2 * imagePaddingPx
        val width = regionAWidth + extensionPx
        val height = regionAHeight
        // A 贴着面板的外侧边; B 与 A 底边对齐, 从 A 的下角向屏幕中央伸出
        val regionAX = when (corner) {
            PlayerScreenshotPanelCorner.BottomLeft -> 0
            PlayerScreenshotPanelCorner.BottomRight -> extensionPx
        }
        frame.regionA = Rect(regionAX.toFloat(), 0f, (regionAX + regionAWidth).toFloat(), regionAHeight.toFloat())
        val regionBTop = (regionAHeight - lobeHeightPx).toFloat()
        frame.regionB = when (corner) {
            PlayerScreenshotPanelCorner.BottomLeft ->
                Rect((regionAX + regionAWidth - lobeOverlapPx).toFloat(), regionBTop, width.toFloat(), height.toFloat())

            PlayerScreenshotPanelCorner.BottomRight ->
                Rect(0f, regionBTop, (regionAX + lobeOverlapPx).toFloat(), height.toFloat())
        }
        val imageX = regionAX + imagePaddingPx
        val imageY = imagePaddingPx
        frame.image = Rect(
            imageX.toFloat(),
            imageY.toFloat(),
            (imageX + imageWidth).toFloat(),
            (imageY + imageHeight).toFloat(),
        )
        // 动作按钮都在 B 伸出 A 的那段里, 靠 A 的是分享, 外侧的是复制
        val buttonY = regionAHeight - lobeHeightPx + lobePaddingPx
        val shareX = when (corner) {
            PlayerScreenshotPanelCorner.BottomLeft -> regionAX + regionAWidth + lobePaddingPx
            PlayerScreenshotPanelCorner.BottomRight -> regionAX - lobePaddingPx - buttonSizePx
        }
        val copyX = when (corner) {
            PlayerScreenshotPanelCorner.BottomLeft -> shareX + buttonSizePx + buttonGapPx
            PlayerScreenshotPanelCorner.BottomRight -> shareX - buttonGapPx - buttonSizePx
        }

        val background = measurables.first { it.layoutId == BACKGROUND_ID }
            .measure(Constraints.fixed(width, height))
        layout(width, height) {
            background.place(0, 0)
            image.place(imageX, imageY)
            share.place(shareX, buttonY)
            copy.place(copyX, buttonY)
        }
    }
}

/** 面板上的圆形图标按钮: 圆底 [color], 只有图标. */
@Composable
private fun PanelCircleButton(
    icon: ImageVector,
    label: String,
    color: Color,
    iconSize: Dp,
    alpha: () -> Float,
    enabled: Boolean,
    interactionSource: MutableInteractionSource,
    indication: Indication?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .graphicsLayer { this.alpha = alpha() }
            .clip(CircleShape)
            .background(color)
            .clickable(interactionSource, indication, enabled = enabled, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, Modifier.size(iconSize))
    }
}

// region Preview

/** 16:9 的合成画面: 深蓝底上一个橙色圆和一块青色矩形, 便于看出比例与位置. */
@Composable
private fun rememberPreviewScreenshot(): SavedPlayerScreenshot = remember {
    val image = ImageBitmap(1920, 1080)
    val canvas = Canvas(image)
    val paint = Paint()
    paint.color = Color(0xFF1E3A5F)
    canvas.drawRect(Rect(0f, 0f, 1920f, 1080f), paint)
    paint.color = Color(0xFFE07A2F)
    canvas.drawCircle(Offset(600f, 540f), 320f, paint)
    paint.color = Color(0xFF5FB3A2)
    canvas.drawRect(Rect(1150f, 220f, 1800f, 860f), paint)
    SavedPlayerScreenshot(image, "12345-01-1m23s456ms.png", "preview")
}

/**
 * 播放器区域用黑底代替视频. 底部控制栏按 72dp 计, 面板会在它上方停靠.
 *
 * @param docked `true` 为落定后的面板 (画面、分享与复制按钮); `false` 为入场起点, 截图铺在视频区域上
 */
@Composable
private fun PreviewScreenshotPresentationImpl(
    playerWidth: Dp,
    playerHeight: Dp,
    docked: Boolean,
) = ProvideCompositionLocalsForPreview(darkMode = DarkMode.DARK) {
    val screenshot = rememberPreviewScreenshot()
    val presentation = remember {
        PlayerScreenshotPresentation(screenshot, sequence = 1).apply { hasDocked = docked }
    }
    BoxWithConstraints(Modifier.size(playerWidth, playerHeight).background(Color.Black)) {
        ScreenshotPresentation(
            presentation = presentation,
            playerSize = IntSize(constraints.maxWidth, constraints.maxHeight),
            bottomOffset = 72.dp,
            windowInsets = WindowInsets(0),
            autoDismissDelay = PlayerScreenshotOverlayDefaults.AutoDismissDelay,
            onShare = {},
            onCopy = {},
            onOpen = {},
            onDismissed = {},
        )
    }
}

@Preview(name = "Docked, landscape (bottom left)", widthDp = 800, heightDp = 450)
@Composable
private fun PreviewScreenshotPresentationDockedLandscape() =
    PreviewScreenshotPresentationImpl(800.dp, 450.dp, docked = true)

@Preview(name = "Docked, portrait (bottom right)", widthDp = 400, heightDp = 700)
@Composable
private fun PreviewScreenshotPresentationDockedPortrait() =
    PreviewScreenshotPresentationImpl(400.dp, 700.dp, docked = true)

@Preview(name = "Entering, screenshot over the video", widthDp = 800, heightDp = 450)
@Composable
private fun PreviewScreenshotPresentationEntering() =
    PreviewScreenshotPresentationImpl(800.dp, 450.dp, docked = false)

// endregion
