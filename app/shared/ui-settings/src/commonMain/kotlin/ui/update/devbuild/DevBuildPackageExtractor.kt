/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.update.devbuild

import me.him188.ani.utils.io.SystemPath

/**
 * 从 zip [archive] 中取出第一个扩展名为 [extension] (不区分大小写, 不含点) 的文件条目, 写入 [target].
 *
 * @return 是否找到了这样的条目. 没找到时不会创建 [target].
 */
internal expect suspend fun extractZipEntryByExtension(
    archive: SystemPath,
    extension: String,
    target: SystemPath,
): Boolean

/**
 * 给 [file] 加上可执行权限. GitHub Actions artifact 不保留文件权限, 解压出的 AppImage 需要补上.
 */
internal expect suspend fun markExecutable(file: SystemPath)
