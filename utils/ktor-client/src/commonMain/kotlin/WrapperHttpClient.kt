/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.ktor

import io.ktor.client.HttpClient
import kotlin.contracts.contract

abstract class WrapperHttpClient {
    /**
     * 使用一个满足需求的 [HttpClient] 实例.
     *
     * 在调用此方法时, client 一定会有效. 但是在调用结束后, client 可能会被销毁, 因此不要将对 client 的引用带出此方法.
     */
    @OptIn(UnsafeWrapperHttpClientApi::class)
    inline fun <R> use(
        action: HttpClient.() -> R,
    ): R {
        contract {
            callsInPlace(action, kotlin.contracts.InvocationKind.EXACTLY_ONCE)
        }
        val client = borrow()
        try {
            return action(client)
        } finally {
            returnClient(client)
        }
    }

    @UnsafeWrapperHttpClientApi
    abstract fun borrow(): HttpClient

    /**
     * 永久持有一个 [HttpClient] 实例 (只要不调用 [returnClient]), 此实例永远不会被销毁.
     *
     * 效果跟 [borrow] 一样, 但是为了突出此实例永久持有的特性.
     */
    @UnsafeWrapperHttpClientApi
    fun borrowForever(): HttpClient = borrow()

    @UnsafeWrapperHttpClientApi
    abstract fun returnClient(client: HttpClient)
}

@RequiresOptIn(
    message = "This operates on unsafe reference counter. Incorrect usage may cause memory leak.",
    level = RequiresOptIn.Level.ERROR,
)
annotation class UnsafeWrapperHttpClientApi

fun HttpClient.asWrapperHttpClient(): WrapperHttpClient = object : WrapperHttpClient() {
    @UnsafeWrapperHttpClientApi
    override fun borrow(): HttpClient = this@asWrapperHttpClient

    @UnsafeWrapperHttpClientApi
    override fun returnClient(client: HttpClient) = Unit
}

