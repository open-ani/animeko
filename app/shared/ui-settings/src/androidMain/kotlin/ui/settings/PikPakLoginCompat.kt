/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings

import io.ktor.client.HttpClient
import me.him188.ani.torrent.pikpak.testPikPakLogin

internal actual suspend fun testPikPakLoginCompat(
    username: String,
    password: String,
    refreshToken: String,
    httpClient: HttpClient,
): Boolean = testPikPakLogin(username, password, refreshToken, httpClient)
