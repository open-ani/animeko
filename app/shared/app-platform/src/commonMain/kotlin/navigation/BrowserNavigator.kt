/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.navigation

import me.him188.ani.app.platform.Context

interface BrowserNavigator {
    fun openBrowser(context: Context, url: String)

    fun openJoinGroup(context: Context)

    fun openJoinTelegram(context: Context) = openBrowser(
        context,
        "https://t.me/openani",
    )

    // Android Intent.ACTION_VIEW
    fun intentActionView(context: Context, url: String)
    
    fun intentOpenVideo(context: Context, url: String) {}
}

object NoopBrowserNavigator : BrowserNavigator {
    override fun openBrowser(context: Context, url: String) {
    }

    override fun openJoinGroup(context: Context) {
    }

    override fun intentActionView(context: Context, url: String) {
    }
}
