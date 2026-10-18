/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.input

import androidx.compose.runtime.Immutable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import me.him188.ani.app.data.models.preference.PlayerShortcut

/**
 * 一个可以绑定为播放器快捷键的按键.
 *
 * @param id 与平台无关的标识, 持久化在 [PlayerShortcut.key] 中, 与 Compose [Key] 常量同名.
 * @param key 本平台上对应的 Compose 按键.
 * @param label 展示给用户的名称. 字母数字和符号按键直接显示字符, 其余使用通用英文名称, 与系统键帽一致, 不做本地化.
 */
@Immutable
class PlayerShortcutKey(
    val id: String,
    val key: Key,
    val label: String,
)

/**
 * 播放器快捷键支持的按键表. [Key] 的键码在各平台不同, 所以配置里只存 [PlayerShortcutKey.id], 这里负责双向映射.
 *
 * 不在表里的按键不能绑定: Enter / Escape / Tab / DPad 中键留给页面导航和输入框, 修饰键只能作为组合的一部分.
 */
object PlayerShortcutKeys {
    val all: List<PlayerShortcutKey> = buildList {
        fun add(id: String, key: Key, label: String = id) = add(PlayerShortcutKey(id, key, label))

        add("A", Key.A); add("B", Key.B); add("C", Key.C); add("D", Key.D); add("E", Key.E); add("F", Key.F)
        add("G", Key.G); add("H", Key.H); add("I", Key.I); add("J", Key.J); add("K", Key.K); add("L", Key.L)
        add("M", Key.M); add("N", Key.N); add("O", Key.O); add("P", Key.P); add("Q", Key.Q); add("R", Key.R)
        add("S", Key.S); add("T", Key.T); add("U", Key.U); add("V", Key.V); add("W", Key.W); add("X", Key.X)
        add("Y", Key.Y); add("Z", Key.Z)

        add("Zero", Key.Zero, "0"); add("One", Key.One, "1"); add("Two", Key.Two, "2"); add("Three", Key.Three, "3")
        add("Four", Key.Four, "4"); add("Five", Key.Five, "5"); add("Six", Key.Six, "6"); add("Seven", Key.Seven, "7")
        add("Eight", Key.Eight, "8"); add("Nine", Key.Nine, "9")

        add("F1", Key.F1); add("F2", Key.F2); add("F3", Key.F3); add("F4", Key.F4); add("F5", Key.F5); add("F6", Key.F6)
        add("F7", Key.F7); add("F8", Key.F8); add("F9", Key.F9); add("F10", Key.F10); add("F11", Key.F11)
        add("F12", Key.F12)

        add("Spacebar", Key.Spacebar, "Space")
        add("DirectionUp", Key.DirectionUp, "↑")
        add("DirectionDown", Key.DirectionDown, "↓")
        add("DirectionLeft", Key.DirectionLeft, "←")
        add("DirectionRight", Key.DirectionRight, "→")
        add("Backspace", Key.Backspace, "Backspace")
        add("Delete", Key.Delete, "Delete")
        add("Insert", Key.Insert, "Insert")
        add("MoveHome", Key.MoveHome, "Home")
        add("MoveEnd", Key.MoveEnd, "End")
        add("PageUp", Key.PageUp, "Page Up")
        add("PageDown", Key.PageDown, "Page Down")

        add("Comma", Key.Comma, ",")
        add("Period", Key.Period, ".")
        add("Slash", Key.Slash, "/")
        add("Semicolon", Key.Semicolon, ";")
        add("Apostrophe", Key.Apostrophe, "'")
        add("LeftBracket", Key.LeftBracket, "[")
        add("RightBracket", Key.RightBracket, "]")
        add("Backslash", Key.Backslash, "\\")
        add("Minus", Key.Minus, "-")
        add("Equals", Key.Equals, "=")
        add("Grave", Key.Grave, "`")

        add("NumPad0", Key.NumPad0, "Num 0"); add("NumPad1", Key.NumPad1, "Num 1"); add("NumPad2", Key.NumPad2, "Num 2")
        add("NumPad3", Key.NumPad3, "Num 3"); add("NumPad4", Key.NumPad4, "Num 4"); add("NumPad5", Key.NumPad5, "Num 5")
        add("NumPad6", Key.NumPad6, "Num 6"); add("NumPad7", Key.NumPad7, "Num 7"); add("NumPad8", Key.NumPad8, "Num 8")
        add("NumPad9", Key.NumPad9, "Num 9")
        add("NumPadAdd", Key.NumPadAdd, "Num +")
        add("NumPadSubtract", Key.NumPadSubtract, "Num -")
        add("NumPadMultiply", Key.NumPadMultiply, "Num *")
        add("NumPadDivide", Key.NumPadDivide, "Num /")
        add("NumPadDot", Key.NumPadDot, "Num .")
    }

    private val byId: Map<String, PlayerShortcutKey> = all.associateBy { it.id }
    private val byKey: Map<Key, PlayerShortcutKey> = all.associateBy { it.key }

    fun findById(id: String): PlayerShortcutKey? = byId[id]

    fun findByKey(key: Key): PlayerShortcutKey? = byKey[key]

    /**
     * 单独按下时不构成快捷键的修饰键.
     */
    val modifierKeys: Set<Key> = setOf(
        Key.CtrlLeft, Key.CtrlRight,
        Key.ShiftLeft, Key.ShiftRight,
        Key.AltLeft, Key.AltRight,
        Key.MetaLeft, Key.MetaRight,
        Key.CapsLock, Key.NumLock, Key.ScrollLock, Key.Function,
    )

    fun isModifierKey(key: Key): Boolean = key in modifierKeys
}

/**
 * 本平台上对应的 Compose 按键, 标识未知时为 `null`.
 */
val PlayerShortcut.composeKey: Key?
    get() = PlayerShortcutKeys.findById(key)?.key

/**
 * 把按键事件转换成按键组合. 按下的是修饰键或不支持的按键时为 `null`.
 */
fun KeyEvent.toPlayerShortcut(): PlayerShortcut? {
    val info = PlayerShortcutKeys.findByKey(key) ?: return null
    return PlayerShortcut(
        key = info.id,
        ctrl = isCtrlPressed,
        shift = isShiftPressed,
        alt = isAltPressed,
        meta = isMetaPressed,
    )
}

/**
 * 判断 [key] 和各修饰键状态是否正好匹配本组合.
 *
 * @param ignoreShift 为 `true` 时不比较 Shift. 用于把 Shift 当作「微调」修饰的操作.
 */
fun PlayerShortcut.matches(
    key: Key,
    ctrl: Boolean,
    shift: Boolean,
    alt: Boolean,
    meta: Boolean,
    ignoreShift: Boolean = false,
): Boolean {
    if (composeKey != key) return false
    if (this.ctrl != ctrl || this.alt != alt || this.meta != meta) return false
    return ignoreShift || this.shift == shift
}

/**
 * 展示用文本, 如 `Ctrl + Shift + F`. macOS 使用系统符号 `⌃⌥⇧⌘F`.
 */
fun PlayerShortcut.displayText(isMacOs: Boolean): String {
    val keyLabel = PlayerShortcutKeys.findById(key)?.label ?: key
    if (isMacOs) {
        return buildString {
            if (ctrl) append('⌃')
            if (alt) append('⌥')
            if (shift) append('⇧')
            if (meta) append('⌘')
            append(keyLabel)
        }
    }
    return buildList {
        if (ctrl) add("Ctrl")
        if (alt) add("Alt")
        if (shift) add("Shift")
        if (meta) add("Meta")
        add(keyLabel)
    }.joinToString(" + ")
}
