/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.android.navigation

import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import me.him188.ani.app.navigation.BrowserNavigator
import me.him188.ani.app.platform.Context
import me.him188.ani.utils.logging.logger

class AndroidBrowserNavigator : BrowserNavigator {
    private val logger = logger<AndroidBrowserNavigator>()

    override fun openBrowser(context: Context, url: String) {
        runCatching {
            launchChromeTab(context, url)
        }.recoverCatching {
            view(url, context)
        }.onFailure {
            logger.warn("Failed to open tab", it)
        }
    }

    private fun launchChromeTab(context: Context, url: String) {
        val intent = CustomTabsIntent.Builder().build()
        intent.launchUrl(context, Uri.parse(url))
    }

    override fun intentActionView(context: Context, url: String) {
        kotlin.runCatching {
            view(url, context)
        }.onFailure {
            logger.warn("Failed to open browser", it)
        }
    }

    override fun intentOpenVideo(context: Context, url: String) {
        kotlin.runCatching {
            val browserIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(url.toUri(), "video/*")
            }
            context.startActivity(
                Intent.createChooser(browserIntent, "选择播放器"),
            )
        }.onFailure {
            logger.warn("Failed to open browser", it)
        }
    }

    private fun view(url: String, context: Context) {
        val browserIntent = Intent(Intent.ACTION_VIEW).apply {
            setData(Uri.parse(url))
        }
        context.startActivity(browserIntent)
    }

    override fun openJoinGroup(context: Context) {
        val browserIntent = Intent(Intent.ACTION_VIEW).apply {
            setData(Uri.parse(QQ_GROUP))
        }
        kotlin.runCatching {
            context.startActivity(browserIntent)
        } // 未安装 QQ
    }
}

// https://qun.qq.com/#/handy-tool/join-group
private const val QQ_GROUP =
    "mqqopensdkapi://bizAgent/qm/qr?url=http%3A%2F%2Fqm.qq.com%2Fcgi-bin%2Fqm%2Fqr%3Ffrom%3Dapp%26p%3Dandroid%26jump_from%3Dwebapi%26k%3D" + "oiWgOz87g6x4Eskej1Ja0bKWYyZR_dPO"
 