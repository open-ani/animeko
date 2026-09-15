/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.window_drop_unsupported_description
import me.him188.ani.app.ui.lang.window_drop_unsupported_title
import me.him188.ani.utils.io.name
import org.jetbrains.compose.resources.stringResource
import kotlin.math.PI
import kotlin.math.roundToInt

/**
 * 主窗口级拖放的处理者. 每个功能 (安装安装包, 播放本地文件, ...) 实现一个, 注册到 [WindowDropHost].
 *
 * 一次拖放会话只由一个处理者接管: 拖放开始时按注册顺序依次调用 [onDragStarted],
 * 第一个返回非 `null` 预览的处理者接管本次会话, 松手后只有它会收到 [onDrop].
 */
@Stable
interface WindowDropHandler {
    /**
     * 拖放会话开始, 决定是否接管. 返回 `null` 表示这次拖入的内容与本处理者无关, 交给后续处理者.
     *
     * [content] 为 `null` 表示拖动阶段读不到内容 (部分系统只在松手后才提供文件列表),
     * 愿意在松手后再判断的处理者可以返回一个通用预览.
     */
    fun onDragStarted(content: DragAndDropContent?): WindowDropPreview?

    /**
     * 松手. 返回 `true` 表示已处理.
     */
    fun onDrop(content: DragAndDropContent): Boolean

    /**
     * 在「不支持的文件」提示中列出本处理者支持的内容, 例如「dmg、zip 安装包」. `null` 表示不列出.
     */
    @Composable
    fun supportedHint(): String? = null
}

enum class WindowDropTone {
    /**
     * 松手后会有动作.
     */
    ACCEPT,

    /**
     * 松手后不会有动作, 提示以错误色展示.
     */
    REJECT,
}

/**
 * 拖入期间在窗口内容之上展示的预览. [content] 渲染在 [WindowDropHost] 提供的卡片之内,
 * 标准布局用 [WindowDropCardContent].
 */
@Stable
class WindowDropPreview(
    val tone: WindowDropTone = WindowDropTone.ACCEPT,
    val content: @Composable () -> Unit,
)

/**
 * 一次拖放会话.
 */
@Stable
sealed interface WindowDropSession {
    /**
     * 已由 [handler] 接管.
     */
    class Accepted(val handler: WindowDropHandler, val preview: WindowDropPreview) : WindowDropSession

    /**
     * 拖入的是文件, 但没有处理者接管. [fileName] 是首个文件的文件名.
     */
    class Rejected(val fileName: String) : WindowDropSession
}

/**
 * [WindowDropHost] 的会话状态. [session] 只随拖放会话的开始与结束变化,
 * 不随指针进出窗口内其他拖放目标变化, 提示层因此不会闪烁.
 */
@Stable
class WindowDropHostState {
    var session: WindowDropSession? by mutableStateOf(null)
        private set

    fun onDragStarted(content: DragAndDropContent?, handlers: List<WindowDropHandler>) {
        if (handlers.isEmpty()) {
            session = null
            return
        }
        session = findHandler(content, handlers)
            ?: (content as? DragAndDropContent.FileList)?.files?.firstOrNull()
                ?.let { WindowDropSession.Rejected(it.name) }
    }

    /**
     * 松手. 已接管的处理者优先; 拖动阶段没有处理者接管 (例如读不到内容) 时, 用实际内容再问一遍.
     */
    fun onDrop(content: DragAndDropContent, handlers: List<WindowDropHandler>): Boolean {
        val handler = (session as? WindowDropSession.Accepted)?.handler
            ?: findHandler(content, handlers)?.handler
            ?: return false
        return handler.onDrop(content)
    }

    fun onDragEnded() {
        session = null
    }

    private fun findHandler(
        content: DragAndDropContent?,
        handlers: List<WindowDropHandler>,
    ): WindowDropSession.Accepted? {
        for (handler in handlers) {
            val preview = handler.onDragStarted(content) ?: continue
            return WindowDropSession.Accepted(handler, preview)
        }
        return null
    }
}

object WindowDropTestTags {
    const val OVERLAY = "window_drop_overlay"
}

/**
 * 让 [content] 所在区域 (通常是整个主窗口) 接受拖放, 交给 [handlers] 处理.
 *
 * [handlers] 为空时不参与拖放. 参与时 [content] 内部的其他拖放目标仍优先接收落在其上的拖放.
 * 拖入期间在 [content] 之上淡入接管者提供的预览 (没有处理者接管的文件则提示「不支持的文件」),
 * 拖放结束后淡出; 没有持续动效.
 */
@Composable
fun WindowDropHost(
    handlers: List<WindowDropHandler>,
    modifier: Modifier = Modifier,
    state: WindowDropHostState = remember { WindowDropHostState() },
    content: @Composable () -> Unit,
) {
    val currentHandlers by rememberUpdatedState(handlers)
    val target = remember(state) {
        object : DragAndDropTarget {
            override fun onStarted(event: DragAndDropEvent) {
                // 部分系统在拖动阶段读取内容会抛异常, 此时按「读不到」处理, 松手后再判断.
                val content = runCatching { processDragAndDropEventImpl(event) }.getOrNull()
                state.onDragStarted(content, currentHandlers)
            }

            override fun onEnded(event: DragAndDropEvent) {
                state.onDragEnded()
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                val content = runCatching { processDragAndDropEventImpl(event) }
                    .getOrDefault(DragAndDropContent.Unsupported)
                return state.onDrop(content, currentHandlers)
            }
        }
    }

    Box(modifier.dragAndDropTarget({ currentHandlers.isNotEmpty() }, target)) {
        content()

        val session = state.session
        // 淡出期间仍需展示最后一次的内容
        var lastSession by remember { mutableStateOf(session) }
        if (session != null) lastSession = session
        AnimatedVisibility(
            visible = session != null,
            modifier = Modifier.matchParentSize(),
            enter = fadeIn(tween(durationMillis = 200)),
            exit = fadeOut(tween(durationMillis = 150)),
        ) {
            lastSession?.let { WindowDropOverlay(it, currentHandlers) }
        }
    }
}

@Composable
private fun WindowDropOverlay(session: WindowDropSession, handlers: List<WindowDropHandler>) {
    when (session) {
        is WindowDropSession.Accepted -> WindowDropOverlayLayer(session.preview.tone) {
            session.preview.content()
        }

        is WindowDropSession.Rejected -> WindowDropOverlayLayer(WindowDropTone.REJECT) {
            val hints = handlers.mapNotNull { it.supportedHint() }
            WindowDropCardContent(
                icon = Icons.Rounded.Block,
                title = stringResource(Lang.window_drop_unsupported_title),
                subtitle = session.fileName,
                description = if (hints.isEmpty()) null else stringResource(
                    Lang.window_drop_unsupported_description,
                    hints.joinToString("、"),
                ),
                tone = WindowDropTone.REJECT,
            )
        }
    }
}

private val DropCardCornerRadius = 28.dp

/**
 * 提示层的外观: 一层蒙版 (深色主题压暗, 浅色主题提亮, 让卡片边缘不与蒙版形成双重边),
 * 居中一张按 [tone] 着色的虚线边框卡片, [content] 渲染在卡片之内.
 */
@Composable
private fun WindowDropOverlayLayer(
    tone: WindowDropTone,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val isDark = colors.surface.luminance() < 0.5f
    val scrimColor = if (isDark) colors.scrim.copy(alpha = 0.72f) else colors.surface.copy(alpha = 0.8f)
    Box(
        modifier
            .fillMaxSize()
            .background(scrimColor)
            .testTag(WindowDropTestTags.OVERLAY),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            Modifier
                .width(560.dp)
                .dashedBorder(if (tone == WindowDropTone.REJECT) colors.error else colors.primary, DropCardCornerRadius),
            shape = RoundedCornerShape(DropCardCornerRadius),
            color = colors.surfaceContainer,
            shadowElevation = 6.dp,
        ) {
            Box(Modifier.padding(start = 40.dp, top = 40.dp, end = 40.dp, bottom = 32.dp)) {
                content()
            }
        }
    }
}

/**
 * 提示卡片的标准内容: 图标, 标题, 一枚显示 [subtitle] (通常是文件名) 的胶囊, 说明文字 [description],
 * 以及底部的小标签 [badge]. 处理者的 [WindowDropPreview.content] 通常直接使用它.
 */
@Composable
fun WindowDropCardContent(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    description: String? = null,
    badge: String? = null,
    tone: WindowDropTone = WindowDropTone.ACCEPT,
) {
    val colors = MaterialTheme.colorScheme
    val reject = tone == WindowDropTone.REJECT
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier
                .size(72.dp)
                .background(
                    if (reject) colors.errorContainer else colors.primaryContainer,
                    RoundedCornerShape(24.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                Modifier.size(36.dp),
                tint = if (reject) colors.onErrorContainer else colors.onPrimaryContainer,
            )
        }
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurface,
        )
        if (subtitle != null) {
            Surface(
                shape = CircleShape,
                color = if (reject) colors.errorContainer else colors.secondaryContainer,
                contentColor = if (reject) colors.onErrorContainer else colors.onSecondaryContainer,
            ) {
                Text(
                    subtitle,
                    Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                )
            }
        }
        if (description != null) {
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (badge != null) {
            Surface(
                Modifier.padding(top = 4.dp),
                shape = CircleShape,
                color = Color.Transparent,
                contentColor = colors.outline,
                border = BorderStroke(1.dp, colors.outlineVariant),
            ) {
                Text(
                    badge,
                    Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

/**
 * 在内容之上绘制一圈圆头虚线的圆角边框, 边框完全落在自身范围内.
 * 虚线长度按周长取整, 首尾相接处不会出现半截虚线.
 */
private fun Modifier.dashedBorder(
    color: Color,
    cornerRadius: Dp,
    width: Dp = 2.dp,
    dash: Dp = 14.dp,
): Modifier = drawWithContent {
    drawContent()
    val strokeWidth = width.toPx()
    val inset = strokeWidth / 2
    val rectSize = Size(size.width - strokeWidth, size.height - strokeWidth)
    val radius = (cornerRadius.toPx() - inset).coerceAtLeast(0f)
    val perimeter = 2 * (rectSize.width + rectSize.height) - 8 * radius + 2 * PI.toFloat() * radius
    val segments = (perimeter / (2 * dash.toPx())).roundToInt().coerceAtLeast(1)
    val segment = perimeter / (2 * segments)
    drawRoundRect(
        color = color,
        topLeft = Offset(inset, inset),
        size = rectSize,
        cornerRadius = CornerRadius(radius),
        style = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round,
            // 圆头会让每段各向外延伸半个线宽, 相应缩短实线并加长间隔, 保持视觉上等分
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(segment - strokeWidth, segment + strokeWidth)),
        ),
    )
}
