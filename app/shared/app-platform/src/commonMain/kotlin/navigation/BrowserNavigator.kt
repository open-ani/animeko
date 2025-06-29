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
    fun openBrowser(context: Context, url: String): OpenBrowserResult

    fun openJoinGroup(context: Context): OpenBrowserResult

    fun openJoinTelegram(context: Context): OpenBrowserResult = openBrowser(
        context,
        "https://t.me/openani",
    )

    // Android Intent.ACTION_VIEW
    fun intentActionView(context: Context, url: String): OpenBrowserResult

    fun intentOpenVideo(context: Context, url: String): OpenBrowserResult {
        return OpenBrowserResult.Success
    }
}

const val QQ_GROUP_ID = "927170241"
const val QQ_GROUP_JOIN_LINK =
    "https://qm.qq.com/cgi-bin/qm/qr?_wv=1027&k=-6GULqAjYtA7HERBcFn9_Hz3789NUALP&authKey=Hsdzw9xBWcAaRKyt%2BmxYP%2FQElAPgOS0PY5pw2ld6YrN04YRY%2F6IWaVZn9CuhS7XR&noverify=0&group_code=927170241"

sealed class OpenBrowserResult {
    data object Success : OpenBrowserResult()

    data class Failure(val throwable: Throwable, val dest: String) : OpenBrowserResult()
}

object NoopBrowserNavigator : BrowserNavigator {
    override fun openBrowser(context: Context, url: String): OpenBrowserResult {
        return OpenBrowserResult.Success
    }

    override fun openJoinGroup(context: Context): OpenBrowserResult {
        return OpenBrowserResult.Success
    }

    override fun intentActionView(context: Context, url: String): OpenBrowserResult {
        return OpenBrowserResult.Success
    }
}
