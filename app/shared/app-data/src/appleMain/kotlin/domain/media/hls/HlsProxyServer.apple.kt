/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.hls

import platform.Foundation.NSURL

internal actual val PlatformHlsProxyServerFactory: HlsProxyServerFactory get() = KtorNetworkHlsProxyServer.Factory

internal actual fun resolveHlsUri(baseUri: String, uri: String): String {
    val parsed = NSURL.URLWithString(uri) ?: return uri
    if (parsed.scheme != null) return uri
    val base = NSURL.URLWithString(baseUri) ?: return uri
    return NSURL.URLWithString(uri, relativeToURL = base)?.absoluteString ?: uri
}
