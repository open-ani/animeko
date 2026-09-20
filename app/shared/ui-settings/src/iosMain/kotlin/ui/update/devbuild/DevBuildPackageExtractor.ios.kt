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

// iOS 没有可安装的 CI 安装包, DevBuildPackageSpec.forPlatform 返回 null, 这里不会被调用.

internal actual suspend fun extractZipEntryByExtension(
    archive: SystemPath,
    extension: String,
    target: SystemPath,
): Boolean = throw UnsupportedOperationException("Installing CI builds is not supported on iOS")

internal actual suspend fun markExecutable(file: SystemPath) {
    throw UnsupportedOperationException("Installing CI builds is not supported on iOS")
}
