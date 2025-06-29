/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import kotlinx.coroutines.launch
import me.him188.ani.app.navigation.BrowserNavigator
import me.him188.ani.app.navigation.OpenBrowserResult
import me.him188.ani.app.platform.Context
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.logger

/**
 * Please use [rememberBrowserNavigator] instead of this directly.
 */
val LocalBrowserNavigator: ProvidableCompositionLocal<BrowserNavigator> = staticCompositionLocalOf {
    error("No BrowserNavigator provided")
}

private val logger = logger<BrowserNavigator>()

/**
 * Get [BrowserNavigator] which handles opening URLs asynchronously.
 * That means calling any of its methods always returns [OpenBrowserResult.Success] whether succeeded or failed.
 *
 * If operation failed, the URL will be copied to clipboard, and a toast will be shown.
 */
@Composable
@Suppress("DEPRECATION")
fun rememberBrowserNavigator(): DelegateBrowserNavigator {
    val navigator = LocalBrowserNavigator.current
    val toaster = LocalToaster.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    return remember {
        val delegate = object : BrowserNavigator {
            override fun openBrowser(context: Context, url: String): OpenBrowserResult {
                scope.launch {
                    val openResult = navigator.openBrowser(context, url)
                    if (openResult is OpenBrowserResult.Failure) {
                        clipboard.setText(AnnotatedString(openResult.dest))
                        toaster.toast("无法打开链接，已将链接复制到剪贴板，请打开浏览器访问")
                        logger.error(openResult.throwable) { "Failed to open browser" }
                    }
                }
                return OpenBrowserResult.Success
            }

            override fun openJoinGroup(context: Context): OpenBrowserResult {
                scope.launch {
                    val openResult = navigator.openJoinGroup(context)
                    if (openResult is OpenBrowserResult.Failure) {
                        clipboard.setText(AnnotatedString(openResult.dest))
                        toaster.toast("无法打开 QQ 群链接，已将加群链接复制到剪切板，请打开浏览器访问")
                        logger.error(openResult.throwable) { "Failed to open join QQ group" }
                    }
                }
                return OpenBrowserResult.Success
            }

            override fun intentActionView(context: Context, url: String): OpenBrowserResult {
                scope.launch {
                    val openResult = navigator.intentActionView(context, url)
                    if (openResult is OpenBrowserResult.Failure) {
                        clipboard.setText(AnnotatedString(openResult.dest))
                        toaster.toast("无法打开链接，已将链接复制到剪贴板，请打开浏览器访问")
                        logger.error(openResult.throwable) { "Failed to open intent action view" }
                    }
                }
                return OpenBrowserResult.Success
            }
        }

        DelegateBrowserNavigator(context, delegate)
    }
}

@Suppress("unused")
class DelegateBrowserNavigator(
    private val context: Context,
    private val delegate: BrowserNavigator,
) : BrowserNavigator by delegate {
    fun openBrowser(url: String): OpenBrowserResult {
        return delegate.openBrowser(context, url)
    }

    fun openJoinGroup(): OpenBrowserResult {
        return delegate.openJoinGroup(context)
    }

    fun openJoinTelegram(): OpenBrowserResult {
        return delegate.openJoinTelegram(context)
    }

    fun intentActionView(url: String): OpenBrowserResult {
        return delegate.intentActionView(context, url)
    }

    fun intentOpenVideo(url: String): OpenBrowserResult {
        return delegate.intentOpenVideo(context, url)
    }
}