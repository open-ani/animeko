/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.foundation

import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.http.HttpStatusCode

/**
 * Installs a global catcher for 426 Upgrade Required.
 * When encountered, forwards to [GlobalHttpEventBus] so application layer can react.
 */
val VersionExpiryFeature = ScopedHttpClientFeatureKey<Boolean>("VersionExpiry")

data object VersionExpiryFeatureHandler : ScopedHttpClientFeatureHandler<Boolean>(VersionExpiryFeature) {
    override fun applyToClient(client: HttpClient, value: Boolean) {
        if (!value) return
        client.plugin(HttpSend).intercept { request ->
            fun handleResp(resp: io.ktor.client.statement.HttpResponse) {
                val latest = resp.headers["X-Latest-Version"]
                GlobalHttpEventBus.onVersionExpired(latest)
            }

            val call = try {
                execute(request)
            } catch (e: ClientRequestException) {
                if (e.response.status == HttpStatusCode.UpgradeRequired) {
                    handleResp(e.response)
                }
                throw e
            }

            if (call.response.status == HttpStatusCode.UpgradeRequired) {
                handleResp(call.response)
            }

            call
        }
    }
}

