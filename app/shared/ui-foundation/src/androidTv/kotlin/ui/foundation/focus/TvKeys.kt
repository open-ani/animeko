/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.foundation.focus

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/*
 * 遥控器按键语义 (设计对齐上游 PR#3217 的 TvLongPressKey, 按本模块 android-only 简化):
 * 长按判定基于系统 KeyDown 连发计数而非计时, 与原生 View 的手势判据一致.
 */

/**
 * 长按判定阈值: 第几次 KeyDown 算长按.
 *
 * 系统在按住期间连发 KeyDown —— 第 1 次是按下本身, 第 2 次是首个连发 (约在按住 400~500ms 后),
 * 之后约 50ms 一发. 取 2 即"首个连发就算长按", 与手机端 500ms 的长按门槛大致同档.
 */
const val LONG_PRESS_KEY_DOWN_COUNT = 2

/** 确认键: 遥控器 OK / 键盘回车. */
val TV_CONFIRM_KEYS = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)

/** 播放键. 不含 [Key.MediaPause] —— 它语义单一 (只暂停), 不该被长按改写. */
val TV_PLAY_KEYS = setOf(Key.MediaPlayPause, Key.MediaPlay)

/**
 * 本次 KeyDown 是否为系统按住连发: true = 连发, false = 新的一次按下.
 * 这是区分"用户在本节点上按下了键"与"按住途中焦点切到了本节点"的唯一凭据.
 */
private val KeyEvent.isAutoRepeat: Boolean
    get() = (nativeKeyEvent as? android.view.KeyEvent)?.let { it.repeatCount > 0 } ?: false

/** [Modifier.tvLongPressKey] 的按住进度. 只在需要画按压反馈时才建, 否则由 modifier 内部自持. */
@Stable
class TvLongPressKeyState {
    /** 本次按住已收到的 KeyDown 次数; 抬起或失焦即归零. */
    var downCount: Int by mutableIntStateOf(0)
        internal set

    /** 本次按住已经触发过长按 (于是抬起时不再派发短按). */
    var longPressFired: Boolean by mutableStateOf(false)
        internal set

    /** 手势由本节点起手 (见过非连发的 KeyDown). 别处起手的残余手势不计数也不派发. */
    internal var tracking: Boolean = false

    /** 按住途中但还没到阈值 —— "按下即缩小、到阈值弹回"那类反馈读它. */
    val pressing: Boolean get() = downCount in 1 until LONG_PRESS_KEY_DOWN_COUNT

    internal fun reset() {
        downCount = 0
        longPressFired = false
        tracking = false
    }
}

@Composable
fun rememberTvLongPressKeyState(): TvLongPressKeyState = remember { TvLongPressKeyState() }

/**
 * 遥控器长按某个键: 数 KeyDown 连发次数, 到 [LONG_PRESS_KEY_DOWN_COUNT] **当场**触发
 * [onLongPress] (不等松手); 抬起时若长按没触发过就算短按, 派发 [onShortPress].
 *
 * 不变量 (缺一即产生真实事故, 见上游 PR 的验证记录):
 * - 认领键的 KeyDown 与 KeyUp 一律消费 (KeyUp 漏下去 clickable 会二次触发 onClick).
 * - 只认从本节点起手的手势: 没见过非连发 KeyDown 就收到的连发/KeyUp 一律吞掉 —— 那是别处
 *   的长按把焦点送过来时还没松开的那次按住.
 * - 按住到阈值立即触发, 而不是松手才判.
 * - 失焦即复位.
 *
 * 挂在已经拥有该键的节点上, 放在 clickable 之前. 没有长按动作时整个别挂.
 */
fun Modifier.tvLongPressKey(
    onLongPress: () -> Unit,
    onShortPress: () -> Unit,
    state: TvLongPressKeyState? = null,
    keys: Set<Key> = TV_CONFIRM_KEYS,
): Modifier = composed {
    val resolved = state ?: remember { TvLongPressKeyState() }
    onFocusChanged { if (!it.hasFocus) resolved.reset() }
        .onPreviewKeyEvent { event ->
            if (event.key !in keys) return@onPreviewKeyEvent false
            when (event.type) {
                KeyEventType.KeyDown -> {
                    if (!event.isAutoRepeat) {
                        // 新按下: 无条件重来 (上一次按住的 KeyUp 可能被弹层吃掉, 留有脏状态)
                        resolved.reset()
                        resolved.tracking = true
                        resolved.downCount = 1
                    } else if (resolved.tracking) {
                        resolved.downCount++
                        if (!resolved.longPressFired && resolved.downCount >= LONG_PRESS_KEY_DOWN_COUNT) {
                            resolved.longPressFired = true
                            onLongPress()
                        }
                    }
                    // else: 没起手就收到连发 = 残余手势, 吞掉不计数
                }

                KeyEventType.KeyUp -> {
                    val tracked = resolved.tracking
                    val fired = resolved.longPressFired
                    resolved.reset()
                    if (tracked && !fired) onShortPress()
                }
            }
            true
        }
}

/**
 * 吞掉"把本界面开出来的那一次长按"的余波: 从挂载起, 直到看见新的一次按下 (非连发 KeyDown)
 * 为止, 确认键事件一律消费. 挂在长按弹出的弹层内容上 (如 `DropdownMenu(modifier = ...)`),
 * 防止按住未松的连发当场点中菜单第一项.
 */
fun Modifier.consumeHeldConfirmKey(enabled: Boolean = true): Modifier = composed {
    if (!enabled) return@composed this

    var sawNewPress by remember { mutableStateOf(false) }
    onPreviewKeyEvent { event ->
        if (sawNewPress) return@onPreviewKeyEvent false
        if (event.key !in TV_CONFIRM_KEYS) return@onPreviewKeyEvent false
        if (event.type == KeyEventType.KeyDown && !event.isAutoRepeat) {
            sawNewPress = true
            return@onPreviewKeyEvent false
        }
        true
    }
}
