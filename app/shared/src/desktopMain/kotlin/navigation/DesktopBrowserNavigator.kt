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
    override fun openBrowser(context: Context, url: String): OpenBrowserResult {
        try {
            Desktop.getDesktop().browse(URI.create(url))
            return OpenBrowserResult.Success
        } catch (ex: Exception) {
            return OpenBrowserResult.Failure(ex, url)
        }
    }

    override fun openJoinGroup(context: Context): OpenBrowserResult {
        return openBrowser(context, QQ_GROUP_JOIN_LINK)
    }

    override fun intentActionView(context: Context, url: String): OpenBrowserResult {
        return openBrowser(context, url)
    }
}