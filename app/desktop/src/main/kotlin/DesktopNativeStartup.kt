/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.desktop

/**
 * Initializes the selected player backend on the safe side of JCEF startup.
 *
 * Loading VLC before JCEF can make CEF's framework load crash in dyld on Intel macOS (#3269), while
 * mpv already initializes safely before JCEF on its supported platforms. Keep this ordering in one
 * place so changes to the selected player backend cannot accidentally reverse it again.
 */
internal suspend fun initializeJcefAndPlayerBackend(
    usesMpv: Boolean,
    prepareMpv: suspend () -> Unit,
    initializeJcef: suspend () -> Unit,
    prepareVlc: suspend () -> Unit,
) {
    if (usesMpv) {
        prepareMpv()
    }

    initializeJcef()

    if (!usesMpv) {
        prepareVlc()
    }
}
