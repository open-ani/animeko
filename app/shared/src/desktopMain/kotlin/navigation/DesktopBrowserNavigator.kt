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
import java.awt.Desktop
import java.net.URI

class DesktopBrowserNavigator : BrowserNavigator {
    override fun openBrowser(context: Context, url: String) {
        Desktop.getDesktop().browse(URI.create(url))
    }

    override fun openJoinGroup(context: Context) {
        openBrowser(
            context,
            "https://qm.qq.com/cgi-bin/qm/qr?_wv=1027&k=-6GULqAjYtA7HERBcFn9_Hz3789NUALP&authKey=Hsdzw9xBWcAaRKyt%2BmxYP%2FQElAPgOS0PY5pw2ld6YrN04YRY%2F6IWaVZn9CuhS7XR&noverify=0&group_code=927170241",
        )
    }

    override fun intentActionView(context: Context, url: String) {
        Desktop.getDesktop().browse(URI.create(url))
    }
}