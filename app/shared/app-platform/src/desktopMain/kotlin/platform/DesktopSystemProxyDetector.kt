/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.win32.W32APIOptions
import io.ktor.http.Url
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger

sealed class DesktopSystemProxyDetector : SystemProxyDetector

class WindowsSystemProxyDetector : DesktopSystemProxyDetector() {
    private val logger = logger<WindowsSystemProxyDetector>()

    private val winHttp: Result<WinHttp> =
        runCatching {
            Native.load("winhttp", WinHttp::class.java, W32APIOptions.DEFAULT_OPTIONS) as WinHttp
        }

    override fun detect(): SystemProxyInfo? {
        val proxyConfig = getWindowsProxySettings()
            ?: return null
        var urlString = pointerToString(proxyConfig.lpszProxy) ?: return null
        if (urlString.startsWith("http=")) {
            // io.ktor.http.URLParserException: Fail to parse url: http=http://127.0.0.1:7890;https=http://127.0.0.1:7890
            // https://github.com/open-ani/animeko/issues/2095#issuecomment-2866556548

            urlString = urlString.removePrefix("http=").substringBefore(";")
        }

        val url = if (urlString.contains("://")) {
            Url(urlString)
        } else {
            @Suppress("HttpUrlsUsage")
            Url("http://$urlString")
        }

        logger.info { "Detected system proxy: $urlString" }
        return SystemProxyInfo(url)
    }


    // Define the structure for the proxy config
    @Suppress("ClassName", "SpellCheckingInspection", "unused")
    class WINHTTP_CURRENT_USER_IE_PROXY_CONFIG : Structure() {
        @JvmField
        var fAutoDetect: Boolean = false

        @JvmField
        var lpszAutoConfigUrl: Pointer? = null

        @JvmField
        var lpszProxy: Pointer? = null

        @JvmField
        var lpszProxyBypass: Pointer? = null

        override fun getFieldOrder(): List<String> {
            return listOf("fAutoDetect", "lpszAutoConfigUrl", "lpszProxy", "lpszProxyBypass")
        }
    }

    // Define the WinHTTP library interface
    interface WinHttp : Library {
        @Suppress("FunctionName")
        fun WinHttpGetIEProxyConfigForCurrentUser(pProxyConfig: WINHTTP_CURRENT_USER_IE_PROXY_CONFIG): Boolean
    }

    // Helper function to convert a pointer to a string
    private fun pointerToString(ptr: Pointer?): String? {
        return ptr?.getWideString(0)
    }

    // Function to get the proxy settings
    private fun getWindowsProxySettings(): WINHTTP_CURRENT_USER_IE_PROXY_CONFIG? {
        val proxyConfig = WINHTTP_CURRENT_USER_IE_PROXY_CONFIG()
        val success = winHttp.getOrThrow().WinHttpGetIEProxyConfigForCurrentUser(proxyConfig)

        return if (success) proxyConfig else null
    }
}
