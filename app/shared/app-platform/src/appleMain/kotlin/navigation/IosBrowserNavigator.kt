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

    override fun openBrowser(context: Context, url: String) {
        runCatching {
            openUrl(url)
        }.onFailure {
            logger.warn("Failed to open browser", it)
        }
    }

    override fun openMagnetLink(context: Context, url: String) {
        runCatching {
            openUrl(url)
        }.onFailure {
            logger.warn("Failed to open magnet link", it)
        }
    }

    override fun openJoinGroup(context: Context) {
        // The same QQ group URI used on Android; iOS QQ supports the mqqopensdkapi:// scheme as well
        runCatching {
            openUrl(QQ_GROUP)
        }.onFailure {
            logger.warn("Failed to open QQ group", it)
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

// Same QQ_GROUP URI constant used on Android:
private const val QQ_GROUP =
    "mqqopensdkapi://bizAgent/qm/qr?url=http%3A%2F%2Fqm.qq.com%2Fcgi-bin%2Fqm%2Fqr%3Ffrom%3Dapp%26p%3Dandroid%26jump_from%3Dwebapi%26k%3D" +
            "oiWgOz87g6x4Eskej1Ja0bKWYyZR_dPO"
