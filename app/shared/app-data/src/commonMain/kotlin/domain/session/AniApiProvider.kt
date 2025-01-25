/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.session

import io.ktor.client.HttpClient
import me.him188.ani.app.platform.currentAniBuildConfig
import me.him188.ani.client.apis.BangumiOAuthAniApi
import me.him188.ani.client.apis.ScheduleAniApi
import me.him188.ani.client.apis.SubjectRelationsAniApi
import me.him188.ani.client.apis.TrendsAniApi
import me.him188.ani.utils.ktor.WrapperHttpClient

class AniApiProvider(
    @PublishedApi
    internal val client: WrapperHttpClient,
) {
    val trendsApi = AniApiInvoker(client) { TrendsAniApi(baseurl, it) }
    val scheduleApi = AniApiInvoker(client) { ScheduleAniApi(baseurl, it) }
    val oauthApi = AniApiInvoker(client) { BangumiOAuthAniApi(baseurl, it) }
    val subjectRelationsApi = AniApiInvoker(client) { SubjectRelationsAniApi(baseurl, it) }

    @PublishedApi
    internal val baseurl = currentAniBuildConfig.aniAuthServerUrl
}

interface AniApiInvoker<Api> {
    suspend operator fun <R> invoke(action: suspend Api.() -> R): R
}

fun <Api> AniApiInvoker(
    client: WrapperHttpClient,
    getApi: (HttpClient) -> Api,
): AniApiInvoker<Api> = object : AniApiInvoker<Api> {
    override suspend fun <R> invoke(action: suspend Api.() -> R): R =
        client.use {
            action(
                getApi(this),
            )
        }
}
