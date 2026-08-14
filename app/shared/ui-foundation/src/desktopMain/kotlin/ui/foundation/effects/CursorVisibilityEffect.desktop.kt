/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.effects

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import me.him188.ani.app.platform.window.AwtWindowUtils.Companion.blankCursor

/**
 * 两个分支必须产生结构相同的修饰符链, 只允许 [PointerIcon] 的取值不同.
 *
 * 这个修饰符挂在整个播放器上, 而 [visible] 会在播放期间翻转 (控制器显隐、detached slider 显隐).
 * 若两个分支的链长不同, 翻转时下游节点会被重建, 正在识别的手势随之取消: 控制器隐藏时的横滑 seek
 * 一开始就会请求 detached slider, 反过来翻转 [visible], 于是每次都在刚起手时被自己打断.
 */
actual fun Modifier.cursorVisibility(visible: Boolean): Modifier {
    val blank = blankCursor
        // headless 下无法构造空白光标, 两个分支同样都不挂 pointerHoverIcon, 结构依旧一致.
        ?: return testTag(
            if (visible) TAG_CURSOR_VISIBILITY_EFFECT_VISIBLE else TAG_CURSOR_VISIBILITY_EFFECT_INVISIBLE,
        )

    return pointerHoverIcon(if (visible) PointerIcon.Default else PointerIcon(blank))
        .testTag(
            if (visible) TAG_CURSOR_VISIBILITY_EFFECT_VISIBLE else TAG_CURSOR_VISIBILITY_EFFECT_INVISIBLE,
        )
}
