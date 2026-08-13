/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.widgets

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.him188.ani.app.ui.foundation.consumeHeldConfirmKey
import me.him188.ani.app.ui.foundation.focus.resolveFocusRepeatedly
import kotlin.math.roundToInt

/** 正文右侧为滚动条预留的宽度. */
private val SCROLLBAR_RESERVE = 12.dp

/** 按行滚动的动画时长 (毫秒): 短到不拖沓, 又能看出方向. */
private const val SCROLL_ANIM_MS = 120

/**
 * 居中的可滚动纯文字弹窗: 标题 + 全文 (方向键按行滚动) + 可选的单个操作按钮.
 *
 * 用途是给「放不下的长文」一个焦点友好的归宿: 页面上的正文块按可用高度截断且**不可聚焦**
 * (遥控器上每个焦点停留点都要一次按键, 而正文一旦可聚焦就有「按下键是滚动还是移到下一行」的
 * 歧义), 全文改由显式入口开本弹窗读 —— 弹窗是模态, 里面没有别的焦点目标, 上下键滚动天然无歧义.
 *
 * 滚动按**精确行界**推进: 视口能放下几整行按排版结果算, 底边永远落在行界上, 不露半行.
 * 不用标称行高估算 —— 首行的字体内衬会摊不平, 差几像素就会裁掉末行.
 *
 * 按键分工: 上下 = 滚动全文, 确认 = 触发操作按钮, 返回 = 关闭. [action] 只放**一个**按钮
 * (调用方按当前状态给出唯一可执行的那个), 因此左右键不占用焦点导航 —— 默认吞掉,
 * 调用方可经 [onHorizontalNav] 把它们用作切换弹窗内容 (如相邻集).
 */
@Composable
fun AniScrollableTextDialog(
    title: String,
    text: String,
    onDismissRequest: () -> Unit,
    /** 每次上下键滚动的行数. */
    scrollLines: Int = 3,
    /**
     * 非 null 时左右键调用它 (`delta` = -1/+1), 供调用方原地切换弹窗内容 (如相邻集的详情);
     * 到端点等无效场合由调用方自行忽略. null 时左右键只是被吞掉 (防焦点飘出弹窗).
     */
    onHorizontalNav: ((delta: Int) -> Unit)? = null,
    /**
     * 满幅铺在弹窗里的背景 (如剧照), 上方自动压遮罩. 不做成正文上方的图块 ——
     * 图按宽高比铺开会吃掉大半高度, 把正文和按钮挤出布局; 当背景既不占布局又有氛围.
     */
    background: (@Composable BoxScope.() -> Unit)? = null,
    /** 非 null 时面板按此宽高比定尺寸 (见 [AniCenteredPanelDialog]). */
    aspectRatio: Float? = null,
    /**
     * 底部唯一的操作按钮; 收到的 Modifier 必须挂上 —— 弹窗靠它把打开后的初始焦点送进来.
     * 为 null 时焦点落在一个隐形节点上 (仅用于接收滚动键).
     */
    action: (@Composable (Modifier) -> Unit)? = null,
) {
    val textScroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    // 背景态下由面板提供白色, 否则跟随主题
    val contentColor = LocalContentColor.current
    var layout by remember(text) { mutableStateOf<TextLayoutResult?>(null) }
    var fitLines by remember { mutableStateOf(1) }
    var topLine by remember(text) { mutableStateOf(0) }
    val actionFocus = remember { FocusRequester() }
    var actionFocused by remember { mutableStateOf(false) }

    fun scrollByLines(delta: Int) {
        val l = layout ?: return
        val maxTop = (l.lineCount - fitLines).coerceAtLeast(0)
        val target = (topLine + delta).coerceIn(0, maxTop)
        if (target == topLine) return
        topLine = target
        scope.launch {
            textScroll.animateScrollTo(
                l.getLineTop(target).roundToInt(),
                animationSpec = tween(SCROLL_ANIM_MS),
            )
        }
    }

    // 内容被原地切换 (见 [onHorizontalNav]) 时回到顶部: [topLine]/[layout] 都键在 text 上
    // 自动重置, 唯独滚动像素值残留在旧文位置, 不归零的话新文从半截开始显示
    LaunchedEffect(text) {
        textScroll.scrollTo(0)
    }

    // 弹窗自身不会分配焦点: 打开后显式送进去 (到位确认 + 重试, 焦点分配与弹窗组合有时序竞争)
    LaunchedEffect(Unit) {
        resolveFocusRepeatedly(attempts = 20, delayMillis = 0, arrived = { actionFocused }) {
            runCatching { actionFocus.requestFocus() }
        }
    }

    AniCenteredPanelDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        background = background,
        aspectRatio = aspectRatio,
    ) {
        Column(
            Modifier.fillMaxSize()
                // 本弹窗可能被长按开出来 (按住途中已弹出, 见 tvLongPressKey), 而打开后焦点又
                // 直接送到 action 按钮上 —— 不吞掉那次按住的残余确认键, 按钮会当场被按下.
                // 短按开出来的场合它也安全 (见 consumeHeldConfirmKey), 所以恒挂.
                .consumeHeldConfirmKey()
                .onPreviewKeyEvent { event ->
                    when (event.key) {
                        Key.DirectionUp -> {
                            if (event.type == KeyEventType.KeyDown) scrollByLines(-scrollLines)
                            true
                        }

                        Key.DirectionDown -> {
                            if (event.type == KeyEventType.KeyDown) scrollByLines(scrollLines)
                            true
                        }

                        // 只有一个操作按钮, 左右键不用于焦点导航: 交给调用方切换内容
                        // (未提供时纯吞掉). 恒返回 true, 防止焦点飘出弹窗
                        Key.DirectionLeft, Key.DirectionRight -> {
                            if (event.type == KeyEventType.KeyDown) {
                                onHorizontalNav?.invoke(if (event.key == Key.DirectionLeft) -1 else 1)
                            }
                            true
                        }

                        else -> false
                    }
                },
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val l = layout
                if (l != null && l.lineCount > 0) {
                    var n = 0
                    val availablePx = constraints.maxHeight.toFloat()
                    while (n < l.lineCount && l.getLineBottom(n) <= availablePx) n++
                    fitLines = n.coerceAtLeast(1)
                }
                Column(Modifier.fillMaxSize().verticalScroll(textScroll)) {
                    Text(
                        text,
                        Modifier.fillMaxWidth().padding(end = SCROLLBAR_RESERVE),
                        style = MaterialTheme.typography.bodyLarge,
                        onTextLayout = { layout = it },
                    )
                }
                // 右侧滚动条: 轨道与视口同高, 滑块位置/长度按滚动进度与视口占比算
                if (textScroll.maxValue > 0) {
                    BoxWithConstraints(
                        Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(4.dp),
                    ) {
                        val total = (textScroll.maxValue + textScroll.viewportSize).coerceAtLeast(1)
                        Box(
                            Modifier.fillMaxSize().clip(CircleShape)
                                .background(contentColor.copy(alpha = 0.24f)),
                        )
                        Box(
                            Modifier
                                .offset(y = maxHeight * (textScroll.value.toFloat() / total))
                                .fillMaxWidth()
                                .height(maxHeight * (textScroll.viewportSize.toFloat() / total))
                                .clip(CircleShape)
                                .background(contentColor.copy(alpha = 0.72f)),
                        )
                    }
                }
            }
            if (action != null) {
                action(
                    Modifier.focusRequester(actionFocus)
                        .onFocusChanged { actionFocused = it.isFocused },
                )
            } else {
                Box(
                    Modifier.size(1.dp)
                        .focusRequester(actionFocus)
                        .onFocusChanged { actionFocused = it.isFocused }
                        .focusable(),
                )
            }
        }
    }
}
