/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform.features

import kotlinx.io.files.Path
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.inSystem

interface FileRevealer {
    suspend fun revealFile(file: SystemPath): Boolean

    /**
     * 在 Windows 资源管理器或 macOS Finder 中打开文件所在目录, 并高亮该文件
     */
    suspend fun revealFile(file: Path): Boolean = revealFile(file.inSystem)
}