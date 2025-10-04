/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.network

import me.him188.ani.app.domain.foundation.ServerListFeatureConfig
import me.him188.ani.client.apis.AutoSkipAniApi
import me.him188.ani.client.apis.BangumiAniApi
import me.him188.ani.client.apis.BangumiOAuthAniApi
import me.him188.ani.client.apis.DanmakuAniApi
import me.him188.ani.client.apis.PeerFilterRuleAniApi
import me.him188.ani.client.apis.ScheduleAniApi
import me.him188.ani.client.apis.SubjectRelationsAniApi
import me.him188.ani.client.apis.SubjectsAniApi
import me.him188.ani.client.apis.SubscriptionsAniApi
import me.him188.ani.client.apis.TrendsAniApi
import me.him188.ani.client.apis.UpdatesAniApi
import me.him188.ani.client.apis.UserAniApi
import me.him188.ani.client.apis.UserAuthenticationAniApi
import me.him188.ani.client.apis.UserProfileAniApi
import me.him188.ani.utils.ktor.ApiInvoker
import me.him188.ani.utils.ktor.ScopedHttpClient

class AniApiProvider(
    @PublishedApi
    internal val client: ScopedHttpClient,
) {
    val trendsApi = ApiInvoker(client) { TrendsAniApi(baseurl, it) }
    val scheduleApi = ApiInvoker(client) { ScheduleAniApi(baseurl, it) }
    val oauthApi = ApiInvoker(client) { BangumiOAuthAniApi(baseurl, it) }
    val subjectRelationsApi = ApiInvoker(client) { SubjectRelationsAniApi(baseurl, it) }
    val danmakuApi = ApiInvoker(client) { DanmakuAniApi(baseurl, it) }
    val pfRuleApi = ApiInvoker(client) { PeerFilterRuleAniApi(baseurl, it) }
    val autoSkipApi = ApiInvoker(client) { AutoSkipAniApi(baseurl, it) }

    val bangumiApi = ApiInvoker(client) { BangumiAniApi(baseurl, it) }
    val subjectApi = ApiInvoker(client) { SubjectsAniApi(baseurl, it) }
    val updateApi = ApiInvoker(client) { UpdatesAniApi(baseurl, it) }
    val userApi = ApiInvoker(client) { UserAniApi(baseurl, it) }
    val userAuthApi = ApiInvoker(client) { UserAuthenticationAniApi(baseurl, it) }
    val userProfileApi = ApiInvoker(client) { UserProfileAniApi(baseurl, it) }
    val subscriptionApi = ApiInvoker(client) { SubscriptionsAniApi(baseurl, it) }

    private inline val baseurl get() = ServerListFeatureConfig.Companion.MAGIC_ANI_SERVER
}
