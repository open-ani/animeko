/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.desktop

import me.him188.ani.utils.platform.Arch
import me.him188.ani.utils.platform.Platform

/**
 * Returns whether player native libraries should load before JCEF on [platform].
 *
 * The crash in #3269 occurred after VLC native libraries were loaded before JCEF on Intel macOS.
 * Desktop now uses mpv on every platform, but keeping JCEF first on the affected architecture avoids
 * reintroducing that native-loading order. Other platforms retain their existing mpv-first order.
 */
internal fun shouldPreparePlayerBeforeJcef(platform: Platform.Desktop): Boolean =
    platform !is Platform.MacOS || platform.arch != Arch.X86_64

internal suspend fun initializeJcefAndPlayerBackend(
    preparePlayerBeforeJcef: Boolean,
    preparePlayer: suspend () -> Unit,
    initializeJcef: suspend () -> Unit,
) {
    if (preparePlayerBeforeJcef) {
        preparePlayer()
    }

    initializeJcef()

    if (!preparePlayerBeforeJcef) {
        preparePlayer()
    }
}
