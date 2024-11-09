/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import me.him188.ani.utils.platform.Platform
import java.awt.Dimension
import java.awt.GraphicsEnvironment

object ScreenUtils {

    /**
     * 获取经过缩放后的, 实际可用的屏幕大小. 将窗口设置为这个大小即可占满整个屏幕
     */
    fun getScreenSize(): DpSize {
        val graphicsEnvironment = GraphicsEnvironment.getLocalGraphicsEnvironment()
        val dimension: Dimension = graphicsEnvironment.maximumWindowBounds.size
        return when (me.him188.ani.utils.platform.currentPlatformDesktop()) {
            is Platform.Linux, // TODO: 检查 linux 的 getScreenSize
            is Platform.MacOS -> {
                // macos dimension 是经过缩放的

                // macbook M2 Max 16':
                // density = 2.0
                // java.awt.Dimension[width=1728,height=1117]
                DpSize(dimension.width.dp, dimension.height.dp)
            }

            is Platform.Windows -> {
                // windows 的 dimension 是经过缩放的

                DpSize(dimension.width.dp, dimension.height.dp)
            }
        }
    }
}
