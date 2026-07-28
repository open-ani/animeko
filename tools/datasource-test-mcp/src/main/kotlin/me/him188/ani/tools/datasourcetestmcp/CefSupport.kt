/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tools.datasourcetestmcp

import me.him188.ani.app.platform.AniCefApp
import java.io.File

/**
 * MCP 内所有需要 JCEF 的能力 (视频解析、验证码浏览器) 共用同一个 CefApp 与同一份工作目录 ——
 * 共用 cache 目录意味着浏览器解验证码留下的 cookie 在进程内一直有效.
 *
 * [AniCefApp.initialize] 幂等, 各能力按需各自调用即可; server 启动时不初始化, 不用浏览器就不付这份代价.
 */
object McpCefApp {
    fun defaultWorkDir(): File = File(System.getProperty("java.io.tmpdir"))
        .resolve("ani-datasource-test-mcp")
        .resolve("cef")

    suspend fun initialize(workDir: File = defaultWorkDir()) {
        AniCefApp.initialize(
            logDir = workDir.resolve("logs").also(File::mkdirs),
            cacheDir = workDir.resolve("cache").also(File::mkdirs),
        )
    }
}
