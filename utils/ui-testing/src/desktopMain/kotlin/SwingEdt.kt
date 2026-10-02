/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.framework

import javax.swing.SwingUtilities

/**
 * 在 Swing EDT 上同步执行 [block] 并原样抛出其中的异常.
 *
 * Skiko UI 测试默认在 JUnit 线程上跑; 但真实桌面 App 的 Compose 运行在 EDT 上,
 * 有些库 (例如 zoomimage) 会断言手势与动画必须在 EDT 上执行. 需要这类库的 UI 测试用它包一层.
 */
fun <T> runOnSwingEdt(block: () -> T): T {
    if (SwingUtilities.isEventDispatchThread()) return block()
    var result: Result<T>? = null
    SwingUtilities.invokeAndWait {
        result = runCatching(block)
    }
    return checkNotNull(result) { "EDT block did not run" }.getOrThrow()
}
