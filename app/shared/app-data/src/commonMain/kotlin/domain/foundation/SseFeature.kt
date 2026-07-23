/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.foundation

import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.sse.SSE

val SseFeature = ScopedHttpClientFeatureKey<Boolean>("SSE")

data object SseFeatureHandler : ScopedHttpClientFeatureHandler<Boolean>(SseFeature) {
    override fun applyToConfig(config: HttpClientConfig<*>, value: Boolean) {
        if (!value) return
        config.install(SSE)
    }
}
