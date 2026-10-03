/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.ktor

import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.*
import io.ktor.client.engine.darwin.*
import io.ktor.client.statement.HttpResponse

actual fun getPlatformKtorEngine(): HttpClientEngineFactory<*> = Darwin

/**
 * 不做设置. NSURLSession 的 `HTTPMaximumConnectionsPerHost` 限制的是连接数 (iOS 默认 4), 不是请求数:
 * HTTP/2 的请求在一条连接上多路复用, 不受它约束; HTTP/1.1 的请求在这几条连接上排队, 默认值正是想要的上限.
 * 调高它只会让 HTTP/1.1 源站同时收到更多条连接.
 */
actual fun HttpClientConfig<*>.engineMaxRequestsPerHost(value: Int) {
}

/**
 * 恒为 true: NSURLSession 自己限制每个 host 的连接数, 见 [engineMaxRequestsPerHost].
 * 不看 [HttpResponse.version], Ktor 的 Darwin 引擎总是报告 HTTP/1.1, 与实际协商的协议无关.
 */
actual fun HttpResponse.sharesConnectionsAcrossRequests(): Boolean = true
