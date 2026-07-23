/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.desktop

import me.him188.ani.utils.platform.Platform
import me.him188.ani.utils.platform.isAArch
import me.him188.ani.utils.platform.isLinux
import me.him188.ani.utils.platform.isMacOS
import me.him188.ani.utils.platform.isWindows

/**
 * 当前桌面平台是否使用 libmpv (mediamp-mpv) 后端而非 VLC.
 *
 * mpv 为 Windows x64 / Windows arm64 / Linux x64 / macOS arm64 分发 native library;
 * macOS x64 分发 VLC binaries. 这里的判断必须与
 * `app/desktop/build.gradle.kts` 中按平台选择的 native runtime 依赖保持一致.
 */
internal fun Platform.Desktop.usesMpv(): Boolean =
    isLinux() || isWindows() || (isMacOS() && isAArch())
