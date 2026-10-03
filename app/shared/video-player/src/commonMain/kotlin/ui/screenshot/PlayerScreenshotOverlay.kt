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
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.node.Ref
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.preference.DarkMode
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.animation.EmphasizedDecelerateEasing
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.video_player_screenshot_copy
import me.him188.ani.app.ui.lang.video_player_screenshot_share
import me.him188.ani.app.videoplayer.screenshot.SavedPlayerScreenshot
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

const val TAG_PLAYER_SCREENSHOT_FLASH = "playerScreenshotFlash"
const val TAG_PLAYER_SCREENSHOT_PANEL = "playerScreenshotPanel"
const val TAG_PLAYER_SCREENSHOT_THUMBNAIL = "playerScreenshotThumbnail"
const val TAG_PLAYER_SCREENSHOT_SHARE = "playerScreenshotShare"
const val TAG_PLAYER_SCREENSHOT_COPY = "playerScreenshotCopy"

object PlayerScreenshotOverlayDefaults {
    /** 面板落定后到自动收起的时间. 鼠标悬停或按住面板时重新计时. */
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
private val LobeHeight = ActionButtonSize + LobePadding * 2

/**
 * B 区域向屏幕中央伸出 A 以外的长度: 第一个按钮紧贴 A 的边缘 (与画面的距离就是 [ImagePadding]), 然后是间距、第二个按钮和末端留白.
 */
private val LobeExtension = ActionButtonSize * 2 + ActionButtonGap + LobePadding

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
 * 4. [autoDismissDelay] 后自动收起, 鼠标悬停或按住面板时重新计时; 点击画面会打开它并立即收起面板.
 *
 * 再次截图会替换面板内容并重放整个流程.
 *
 * @param onShare 点击分享按钮; 第二个参数是分享按钮在窗口中的位置 (dp), 供平台的分享面板定位
 * @param onCopy 点击复制按钮
 * @param onOpen 点击画面; 面板随即收起
 * @param bottomOffset 面板需要避开的底部控制栏高度; 控制栏隐藏时传 0, 面板随之下移
 * @param windowInsets 面板需要避开的系统栏; 变换起点仍与视频区域对齐
 */
@Composable
fun PlayerScreenshotOverlay(
    state: PlayerScreenshotPanelState,
    onShare: (SavedPlayerScreenshot, anchorInWindow: DpRect?) -> Unit,
    onCopy: (SavedPlayerScreenshot) -> Unit,
    onOpen: (SavedPlayerScreenshot) -> Unit,
    modifier: Modifier = Modifier,
    bottomOffset: Dp = 0.dp,
    windowInsets: WindowInsets = WindowInsets(0),
    autoDismissDelay: Duration = PlayerScreenshotOverlayDefaults.AutoDismissDelay,
) {
    var playerSize by remember { mutableStateOf(IntSize.Zero) }
    Box(modifier.onSizeChanged { playerSize = it }) {
        ScreenshotFlash(state.current, Modifier.matchParentSize())
        val presentation = state.current
        if (presentation != null && playerSize != IntSize.Zero) {
            key(presentation) {
                ScreenshotPresentation(
                    presentation = presentation,
                    playerSize = playerSize,
                    bottomOffset = bottomOffset,
                    windowInsets = windowInsets,
                    autoDismissDelay = autoDismissDelay,
                    onShare = { anchor -> onShare(presentation.screenshot, anchor) },
                    onCopy = { onCopy(presentation.screenshot) },
                    onOpen = { onOpen(presentation.screenshot) },
                    onDismissed = { state.dismiss(presentation) },
                )
            }
        }
    }
}

@Composable
private fun ScreenshotFlash(current: PlayerScreenshotPresentation?, modifier: Modifier) {
    // 组合此层时已经在展示的截图不再闪光, 只有之后新发生的截图才触发
    val initial = remember { current }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(current) {
        if (current == null || current === initial) return@LaunchedEffect
        alpha.snapTo(FLASH_PEAK_ALPHA)
        alpha.animateTo(0f, tween(FLASH_DURATION_MILLIS, easing = LinearOutSlowInEasing))
    }
    val visible by remember { derivedStateOf { alpha.value > 0f } }
    if (visible) {
        Box(
            modifier
                .testTag(TAG_PLAYER_SCREENSHOT_FLASH)
                .graphicsLayer { this.alpha = alpha.value }
                .background(Color.White),
        )
    }
}

@Composable
private fun ScreenshotPresentation(
    presentation: PlayerScreenshotPresentation,
    playerSize: IntSize,
    bottomOffset: Dp,
    windowInsets: WindowInsets,
    autoDismissDelay: Duration,
    onShare: (anchorInWindow: DpRect?) -> Unit,
    onCopy: () -> Unit,
    onOpen: () -> Unit,
    onDismissed: () -> Unit,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val screenshot = presentation.screenshot
    val geometry = remember(playerSize, screenshot, windowInsets, bottomOffset, density, layoutDirection) {
        with(density) {
            computePlayerScreenshotPanelGeometry(
                playerSize = playerSize,
                imageSize = IntSize(screenshot.image.width, screenshot.image.height),
                insets = windowInsets,
                density = density,
                layoutDirection = layoutDirection,
                // 几何算的是画面矩形; A 区域比它大一圈留白, 让 A 的边缘落在 PanelMargin 处
                margin = (PanelMargin + ImagePadding).toPx(),
                bottomOffset = bottomOffset.toPx(),
                thumbnailHeight = (if (playerSize.height.toDp() < CompactPlayerHeight) CompactThumbnailHeight else ThumbnailHeight).toPx(),
                maxThumbnailWidth = playerSize.width * MAX_THUMBNAIL_WIDTH_FRACTION,
            )
        }
    }
    val latestGeometry by rememberUpdatedState(geometry)
    val imageCornerPx = with(density) { (PanelCornerRadius - ImagePadding).toPx() }

    val startDocked = presentation.docked
    val rect = remember {
        Animatable(if (startDocked) geometry.thumbnailRect else geometry.startRect, Rect.VectorConverter)
    }
    val cornerRadius = remember { Animatable(if (startDocked) imageCornerPx else 0f) }
    val chromeAlpha = remember { Animatable(if (startDocked) 1f else 0f) }
    val scale = remember { Animatable(1f) }
    var dismissRequested by remember { mutableStateOf(false) }
    val interactions = remember { PanelInteractions() }
    val engaged by interactions.collectIsEngagedAsState()

    // 入场: 原位停留 -> 收进角落 -> 长出外壳 -> 等待自动收起
    LaunchedEffect(Unit) {
        if (!presentation.docked) {
            delay(HOLD_MILLIS.toLong())
            coroutineScope {
                launch { rect.animateTo(latestGeometry.thumbnailRect, tween(MOVE_MILLIS, easing = EmphasizedDecelerateEasing)) }
                launch { cornerRadius.animateTo(imageCornerPx, tween(MOVE_MILLIS, easing = EmphasizedDecelerateEasing)) }
            }
            presentation.docked = true
            chromeAlpha.animateTo(1f, tween(CHROME_MILLIS))
        }
        // 倒计时与再次交互赛跑: 倒计时先到就收起, 悬停或按住先到就等交互结束后重新计时
        val engagedFlow = snapshotFlow { engaged }
        while (true) {
            engagedFlow.first { !it }
            val timedOut = merge(
                flow {
                    delay(autoDismissDelay)
                    emit(true)
                },
                engagedFlow.filter { it }.map { false },
            ).first()
            if (timedOut) break
        }
        dismissRequested = true
    }
    // 停靠后目标位置变化 (控制栏显隐、区域大小变化) 时跟随
    LaunchedEffect(geometry, presentation.docked) {
        if (presentation.docked && rect.targetValue != geometry.thumbnailRect) {
            rect.animateTo(geometry.thumbnailRect, tween(FOLLOW_MILLIS))
        }
    }
    // 退场: 外壳淡出, 整体向停靠的角缩小
    LaunchedEffect(dismissRequested) {
        if (!dismissRequested) return@LaunchedEffect
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
        // 外壳只在停靠后才组合; 退场期间保留它做淡出, 但不再响应点击
        chromeComposed = presentation.docked,
        chromeEnabled = presentation.docked && !dismissRequested,
        interactions = interactions,
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

/** A、B 两个区域在面板自身坐标系中的位置. 测量阶段写入, 放置与绘制阶段读取. */
private class PanelFrame {
    /** A 区域: 容纳画面的圆角矩形. */
    var regionA: Rect = Rect.Zero

    /** B 区域: 与 A 底边对齐、从 A 的下角向屏幕中央伸出的药丸, 一端藏在 A 里, 伸出的部分容纳动作按钮. */
    var regionB: Rect = Rect.Zero
}

/**
 * 面板上各个可交互节点各自的 [MutableInteractionSource]. 涟漪、悬停态与焦点态都取自节点自己的来源,
 * 所以不能共用: 共用会让按下一个按钮时画面与另一个按钮一起亮起.
 */
private class PanelInteractions {
    /** 整块面板的悬停; 悬停在画面或按钮上时面板同样处于悬停. */
    val panel = MutableInteractionSource()
    val image = MutableInteractionSource()
    val share = MutableInteractionSource()
    val copy = MutableInteractionSource()
}

/** 用户正在与面板交互: 悬停在面板上, 或按住画面与按钮中的任意一个. 自动收起在此期间暂停. */
@Composable
private fun PanelInteractions.collectIsEngagedAsState(): State<Boolean> {
    val hovered by panel.collectIsHoveredAsState()
    val imagePressed by image.collectIsPressedAsState()
    val sharePressed by share.collectIsPressedAsState()
    val copyPressed by copy.collectIsPressedAsState()
    return remember { derivedStateOf { hovered || imagePressed || sharePressed || copyPressed } }
}

/**
 * 异形面板. A 区域是一个圆角矩形, 画面落在其中、四周留 [ImagePadding] 的边, 画面本身就是 [thumbnailRect];
 * B 区域是与 A 底边对齐、从 A 的下角向屏幕中央伸出 [LobeExtension] 的药丸, 伸出的部分里是分享与复制两个圆形图标按钮,
 * 靠 A 的是分享. 整块面板是 [screenshotPanelOutline] 描出的一条闭合路径做成的 [GenericShape], 填 surfaceContainer 并带阴影:
 * A 与 B 的底边连成一条直线, B 的上边以内凹圆角接到 A 的侧边.
 *
 * 布局按面板停在左下角 (B 向右伸出) 计算, 停在右下角时整体水平镜像.
 * 入场期间 ([chromeComposed] 为 `false`) 只组合画面本身, 即正在移动的截图.
 */
@Composable
private fun ScreenshotPanel(
    screenshot: SavedPlayerScreenshot,
    corner: PlayerScreenshotPanelCorner,
    thumbnailRect: () -> Rect,
    cornerRadius: () -> Float,
    chromeAlpha: () -> Float,
    scale: () -> Float,
    chromeComposed: Boolean,
    chromeEnabled: Boolean,
    interactions: PanelInteractions,
    onShare: (anchorInWindow: DpRect?) -> Unit,
    onCopy: () -> Unit,
    onOpen: () -> Unit,
) {
    val density = LocalDensity.current
    val imagePaddingPx = with(density) { ImagePadding.roundToPx() }
    val panelCornerPx = with(density) { PanelCornerRadius.toPx() }
    val buttonSizePx = with(density) { ActionButtonSize.roundToPx() }
    val buttonGapPx = with(density) { ActionButtonGap.roundToPx() }
    val lobePaddingPx = with(density) { LobePadding.roundToPx() }
    val lobeHeightPx = with(density) { LobeHeight.roundToPx() }
    val extensionPx = with(density) { LobeExtension.roundToPx() }
    val neckRadiusPx = with(density) { NeckRadius.toPx() }
    val elevationPx = with(density) { PanelElevation.toPx() }
    val panelColor = MaterialTheme.colorScheme.surfaceContainer
    val frame = remember { PanelFrame() }
    val lobeExtendsRight = corner == PlayerScreenshotPanelCorner.BottomLeft
    val panelShape = remember(lobeExtendsRight, panelCornerPx, neckRadiusPx) {
        GenericShape { _, _ ->
            addPath(screenshotPanelOutline(frame.regionA, frame.regionB, panelCornerPx, neckRadiusPx, lobeExtendsRight))
        }
    }

    Layout(
        content = {
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
                    .clickable(interactions.image, LocalIndication.current, enabled = chromeEnabled, onClick = onOpen),
                contentScale = ContentScale.Fit,
            )
            if (chromeComposed) {
                // 面板底: 阴影与底色随外壳一起淡入
                Box(
                    Modifier
                        .layoutId(BACKGROUND_ID)
                        .graphicsLayer {
                            alpha = chromeAlpha()
                            shadowElevation = elevationPx
                            shape = panelShape
                        }
                        .background(panelColor, panelShape),
                )
                // 弹出式的分享面板从分享按钮旁边弹出: 点击时取按钮当前在窗口中的位置
                val shareButtonCoordinates = remember { Ref<LayoutCoordinates>() }
                PanelActionButton(
                    icon = Icons.Rounded.Share,
                    label = stringResource(Lang.video_player_screenshot_share),
                    alpha = chromeAlpha,
                    enabled = chromeEnabled,
                    interactionSource = interactions.share,
                    onClick = { onShare(shareButtonCoordinates.value?.boundsInWindow()?.toDpRect(density)) },
                    modifier = Modifier.layoutId(SHARE_ID).testTag(TAG_PLAYER_SCREENSHOT_SHARE)
                        .onPlaced { shareButtonCoordinates.value = it },
                )
                PanelActionButton(
                    icon = Icons.Rounded.ContentCopy,
                    label = stringResource(Lang.video_player_screenshot_copy),
                    alpha = chromeAlpha,
                    enabled = chromeEnabled,
                    interactionSource = interactions.copy,
                    onClick = onCopy,
                    modifier = Modifier.layoutId(COPY_ID).testTag(TAG_PLAYER_SCREENSHOT_COPY),
                )
            }
        },
        modifier = Modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(Constraints())
                layout(constraints.maxWidth, constraints.maxHeight) {
                    // 放置后画面正好落在目标矩形上: 画面在 A 区域内缩进一圈留白
                    val target = thumbnailRect()
                    placeable.place(
                        (target.left - frame.regionA.left).roundToInt() - imagePaddingPx,
                        (target.top - frame.regionA.top).roundToInt() - imagePaddingPx,
                    )
                }
            }
            .testTag(TAG_PLAYER_SCREENSHOT_PANEL)
            .graphicsLayer {
                scaleX = scale()
                scaleY = scale()
                transformOrigin = if (lobeExtendsRight) TransformOrigin(0f, 1f) else TransformOrigin(1f, 1f)
            }
            .hoverable(interactions.panel),
    ) { measurables, constraints ->
        val target = thumbnailRect()
        val imageWidth = target.width.roundToInt().coerceAtLeast(1)
        val imageHeight = target.height.roundToInt().coerceAtLeast(1)
        val regionAWidth = imageWidth + 2 * imagePaddingPx
        val regionAHeight = imageHeight + 2 * imagePaddingPx
        val width = regionAWidth + extensionPx
        val height = regionAHeight
        // 按 B 向右伸出计算 x, 向左伸出时镜像
        fun x(left: Int, itemWidth: Int) = if (lobeExtendsRight) left else width - left - itemWidth

        val regionALeft = x(0, regionAWidth)
        frame.regionA = Rect(regionALeft.toFloat(), 0f, (regionALeft + regionAWidth).toFloat(), regionAHeight.toFloat())
        // B 朝 A 的那个圆头 (半径等于 B 高度的一半) 完全藏在 A 里
        val lobeWidth = lobeHeightPx / 2 + extensionPx
        val regionBLeft = x(regionAWidth - lobeHeightPx / 2, lobeWidth)
        frame.regionB = Rect(
            regionBLeft.toFloat(),
            (regionAHeight - lobeHeightPx).toFloat(),
            (regionBLeft + lobeWidth).toFloat(),
            height.toFloat(),
        )
        // 分享紧贴 A 的边缘, 与画面只隔 A 的留白; 复制在外侧
        val buttonY = regionAHeight - lobeHeightPx + lobePaddingPx
        val shareX = x(regionAWidth, buttonSizePx)
        val copyX = x(regionAWidth + buttonSizePx + buttonGapPx, buttonSizePx)

        val image = measurables.first { it.layoutId == IMAGE_ID }
            .measure(Constraints.fixed(imageWidth, imageHeight))
        val background = measurables.firstOrNull { it.layoutId == BACKGROUND_ID }
            ?.measure(Constraints.fixed(width, height))
        val share = measurables.firstOrNull { it.layoutId == SHARE_ID }
            ?.measure(Constraints.fixed(buttonSizePx, buttonSizePx))
        val copy = measurables.firstOrNull { it.layoutId == COPY_ID }
            ?.measure(Constraints.fixed(buttonSizePx, buttonSizePx))
        layout(width, height) {
            background?.place(0, 0)
            image.place(x(imagePaddingPx, imageWidth), imagePaddingPx)
            share?.place(shareX, buttonY)
            copy?.place(copyX, buttonY)
        }
    }
}

/** 面板上的圆形图标按钮: 圆底 surfaceContainerLowest, 只有图标. 退场淡出期间虽已禁用, 外观保持不变. */
@Composable
private fun PanelActionButton(
    icon: ImageVector,
    label: String,
    alpha: () -> Float,
    enabled: Boolean,
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = MaterialTheme.colorScheme.surfaceContainerLowest
    val content = MaterialTheme.colorScheme.onSurface
    FilledIconButton(
        onClick = onClick,
        modifier = modifier.graphicsLayer { this.alpha = alpha() },
        enabled = enabled,
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = container,
            disabledContentColor = content,
        ),
        interactionSource = interactionSource,
    ) {
        Icon(icon, contentDescription = label, Modifier.size(20.dp))
    }
}

private fun Rect.toDpRect(density: Density): DpRect = with(density) {
    DpRect(left.toDp(), top.toDp(), right.toDp(), bottom.toDp())
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
    val presentation = remember { PlayerScreenshotPresentation(screenshot).also { it.docked = docked } }
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
