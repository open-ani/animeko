/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.delay
import me.him188.ani.utils.platform.currentTimeMillis

/**
 * Confirm-key aware clickable modifier that prevents long-press from triggering unwanted clicks.
 * 
 * Under focus-driven navigation, this modifier:
 * - Handles DPAD_CENTER (OK button) key events properly
 * - Distinguishes between short-press (click) and long-press
 * - Prevents long-press from triggering onClick
 * - Only triggers onClick on KeyUp for short presses
 * 
 * Otherwise falls back to standard clickable behavior.
 * 
 * @param enabled Controls the enabled state
 * @param onClickLabel Semantic label for the click action
 * @param onClick Callback invoked on short-press/click
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.aniClickable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    interactionSource: MutableInteractionSource? = null,
    indication: Indication? = null,
    onClick: () -> Unit
): Modifier = composed {
    val focusDriven = LocalAniUiBehavior.current.focusDrivenNavigation
    val resolvedIndication = indication ?: LocalIndication.current

    if (focusDriven) {
        var keyDownTime by remember { mutableStateOf(0L) }
        var isLongPress by remember { mutableStateOf(false) }
        
        this
            .onKeyEvent { keyEvent ->
                if (!enabled) return@onKeyEvent false
                
                when {
                    keyEvent.key == Key.DirectionCenter && keyEvent.type == KeyEventType.KeyDown -> {
                        if (keyDownTime == 0L) {
                            // First key down
                            keyDownTime = currentTimeMillis()
                            isLongPress = false
                        } else {
                            // Key repeat (long press)
                            isLongPress = true
                        }
                        true // Consume the event
                    }
                    keyEvent.key == Key.DirectionCenter && keyEvent.type == KeyEventType.KeyUp -> {
                        val pressDuration = currentTimeMillis() - keyDownTime
                        keyDownTime = 0L
                        
                        // Only trigger click for short press (< 500ms)
                        if (!isLongPress && pressDuration < LONG_CLICK_DURATION_MILLIS) {
                            onClick()
                        }
                        isLongPress = false
                        true // Consume the event
                    }
                    else -> false
                }
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = resolvedIndication,
                enabled = enabled,
                onClickLabel = onClickLabel,
                onClick = onClick,
            )
    } else {
        // Non-TV: use standard clickable
        this.combinedClickable(
            interactionSource = interactionSource,
            indication = resolvedIndication,
            enabled = enabled,
            onClickLabel = onClickLabel,
            onClick = onClick,
        )
    }
}

/**
 * Confirm-key aware combined clickable modifier with support for both click and long-click.
 * 
 * Under focus-driven navigation, this modifier:
 * - Handles DPAD_CENTER (OK button) key events properly
 * - Distinguishes between short-press (onClick) and long-press (onLongClick)
 * - Prevents long-press from triggering onClick
 * - Triggers onLongClick after holding for threshold duration
 * 
 * Otherwise falls back to standard combinedClickable behavior.
 * 
 * @param enabled Controls the enabled state
 * @param onClickLabel Semantic label for the click action
 * @param onLongClickLabel Semantic label for the long-click action
 * @param onDoubleClick Callback invoked on double-click (primarily for touch input)
 * @param onClick Callback invoked on short-press/click
 * @param onLongClick Callback invoked on long-press, or null if not supported
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.aniCombinedClickable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    onLongClickLabel: String? = null,
    onDoubleClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null,
    indication: Indication? = null,
    onClick: () -> Unit
): Modifier = composed {
    val focusDriven = LocalAniUiBehavior.current.focusDrivenNavigation
    val resolvedIndication = indication ?: LocalIndication.current

    if (focusDriven && onLongClick != null) {
        var keyDownTime by remember { mutableStateOf(0L) }
        var longClickTriggered by remember { mutableStateOf(false) }
        var isKeyDown by remember { mutableStateOf(false) }
        
        // Monitor for long press threshold
        LaunchedEffect(isKeyDown) {
            if (isKeyDown && keyDownTime > 0L) {
                delay(LONG_CLICK_DURATION_MILLIS) // Long press threshold
                if (isKeyDown && currentTimeMillis() - keyDownTime >= LONG_CLICK_DURATION_MILLIS) {
                    longClickTriggered = true
                    onLongClick()
                }
            }
        }
        
        this
            .onKeyEvent { keyEvent ->
                if (!enabled) return@onKeyEvent false
                
                when {
                    keyEvent.key == Key.DirectionCenter && keyEvent.type == KeyEventType.KeyDown -> {
                        if (keyDownTime == 0L) {
                            // First key down
                            keyDownTime = currentTimeMillis()
                            longClickTriggered = false
                            isKeyDown = true
                        }
                        true // Consume the event
                    }
                    keyEvent.key == Key.DirectionCenter && keyEvent.type == KeyEventType.KeyUp -> {
                        isKeyDown = false
                        val pressDuration = currentTimeMillis() - keyDownTime
                        keyDownTime = 0L
                        
                        // Only trigger click if long click wasn't triggered and it was a short press
                        if (!longClickTriggered && pressDuration < LONG_CLICK_DURATION_MILLIS) {
                            onClick()
                        }
                        longClickTriggered = false
                        true // Consume the event
                    }
                    else -> false
                }
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = resolvedIndication,
                enabled = enabled,
                onClickLabel = onClickLabel,
                onLongClickLabel = onLongClickLabel,
                onDoubleClick = onDoubleClick,
                onLongClick = onLongClick,
                onClick = onClick,
            )
    } else if (focusDriven) {
        // TV without long click - use aniClickable
        aniClickable(enabled, onClickLabel, interactionSource, indication, onClick)
    } else {
        // Non-TV: use standard combinedClickable
        this.combinedClickable(
            interactionSource = interactionSource,
            indication = resolvedIndication,
            enabled = enabled,
            onClickLabel = onClickLabel,
            onLongClickLabel = onLongClickLabel,
            onDoubleClick = onDoubleClick,
            onLongClick = onLongClick,
            onClick = onClick,
        )
    }
}

/**
 * 吞掉"把本界面开出来的那一次长按"的余波: 从挂载起, 直到看见**新的一次按下** (非连发的
 * KeyDown, 即 `repeatCount == 0`) 为止, 确认键事件一律消费.
 *
 * 遥控器按住确认键期间系统会以约 50ms 一次连发 KeyDown. [aniCombinedClickable] 在 500ms 上
 * 触发 onLongClick 弹出下拉菜单时用户的手还没松 —— 菜单一拿到焦点, 紧接着的那几发 KeyDown
 * 与随后的 KeyUp 就落在菜单第一项上, 表现为"菜单刚弹出来就自己把第一项选了".
 *
 * 放行判据与原生控件同一条 (`View.onKeyDown` 只认 `repeatCount == 0`), **不做定时兜底**:
 * 这里曾经"等 1.5 秒或一个 KeyUp 就停止吞", 用户按住超过 1.5 秒时保护到点失效, 剩下的连发
 * 与 KeyUp 照样点中菜单项. 按新判据按多久都安全; "抬起发生在挂载之前"的情形也不再需要超时
 * 自救 —— 那之后收到的第一个事件就是新按下, 当场放行.
 *
 * 挂在弹层内容上 (如 `DropdownMenu(modifier = ...)`): `onPreviewKeyEvent` 自弹层根部向下传,
 * 会先于菜单项拿到事件. 弹层内容只在展开期间组合, 收起时节点随之移除 —— 调用方不必再自己按
 * 展开态开合窗口.
 *
 * 长按的**发起方**以及长按把焦点送到的目标不需要这层保护: [Modifier.tvLongPressKey] 只认从
 * 自己节点起手的手势, 残余连发天然被忽略. 本 modifier 是给弹层**内部**那些普通 clickable
 * (菜单项 / 按钮) 用的.
 *
 * @param enabled 只在本界面可能被长按开出来时才需要 true. 改用"见到新按下才放行"后, 短按开出
 *   来的弹层挂着它也不会误吞 (那次 KeyUp 已经发生, 接下来第一个事件就是新按下), 传 false 纯粹
 *   是省一层拦截.
 */
fun Modifier.consumeHeldConfirmKey(enabled: Boolean = true): Modifier = composed {
    if (!enabled) return@composed this

    var sawNewPress by remember { mutableStateOf(false) }
    onPreviewKeyEvent { event ->
        if (sawNewPress) return@onPreviewKeyEvent false
        if (event.key !in TV_CONFIRM_KEYS) return@onPreviewKeyEvent false
        if (event.type == KeyEventType.KeyDown && event.isAutoRepeat != true) {
            // 新的一次按下 (无从判别的平台上第一发就当新按下): 本事件连同之后的全部放行
            sawNewPress = true
            return@onPreviewKeyEvent false
        }
        true // 残余: 按住途中的连发, 或那一次按住最终的 KeyUp
    }
}
