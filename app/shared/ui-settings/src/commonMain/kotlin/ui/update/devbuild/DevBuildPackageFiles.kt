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
 * 给 [file] 加上可执行权限. 下载得到的 AppImage 没有可执行权限, 需要补上.
 */
internal expect suspend fun markExecutable(file: SystemPath)
