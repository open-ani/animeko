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
import me.him188.ani.utils.logging.logger
import platform.Foundation.NSURL
import platform.UIKit.*

class IosBrowserNavigator : BrowserNavigator {
    private val logger = logger<IosBrowserNavigator>()

    override fun openBrowser(context: Context, url: String): OpenBrowserResult {
        try {
            openUrl(url)
            return OpenBrowserResult.Success
        } catch (ex: Exception) {
            logger.warn("Failed to open browser", ex)
            return OpenBrowserResult.Failure(ex, url)
        }
    }

    override fun intentActionView(context: Context, url: String): OpenBrowserResult {
        try {
            openUrl(url)
            return OpenBrowserResult.Success
        } catch (ex: Exception) {
            logger.warn("Failed to open browser", ex)
            return OpenBrowserResult.Failure(ex, url)
        }
    }

    private fun openUrl(url: String) {
        val nsUrl = NSURL.URLWithString(url) ?: return
        UIApplication.sharedApplication.openURL(
            nsUrl, options = emptyMap<_, Any?>(),
            completionHandler = { _ ->
                // ignored
            },
        )
    }
}
