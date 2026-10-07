/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */
package me.him188.ani.app.domain.mediasource.api

import kotlinx.serialization.json.Json
import me.him188.ani.datasources.api.source.MediaSourceTier

/** Public website configurations that users can add and edit as JSON API sources. */
object ApiMediaSourcePresets {
    // The publishable key is shipped in the site's publicly accessible frontend bundle.
    private const val XIFAN_PUBLIC_KEY = "sb_publishable_OBIVAWACIX6lPXrO98_z24_HcsmalkA"

    val xifanNext = ApiMediaSourceArguments(
        name = "稀饭动漫 Next",
        websiteUrl = "https://next.xifanacg.com",
        iconUrl = "https://next.xifanacg.com/favicon.ico",
        headers = mapOf(
            "apikey" to XIFAN_PUBLIC_KEY,
            "Authorization" to "Bearer $XIFAN_PUBLIC_KEY",
            "Origin" to "https://next.xifanacg.com",
        ),
        search = ApiSearch(
            request = ApiRequest(
                url = "https://api.xifanacg.com/rest/v1/rpc/search_animes",
                method = "POST",
                body = Json.parseToJsonElement("""{
                    "search_term":"{keyword}","page_number":"{page}","items_per_page":"{pageSize}",
                    "sort_by":"bangumi_score","sort_order":"desc","filter_only_published":true
                }"""),
            ),
            aliasesPath = "$['title_original','aliases']",
            subjectUrl = "https://next.xifanacg.com/anime/{subjectId}",
            paginate = true,
        ),
        detail = ApiDetail(
            request = ApiRequest(
                url = "https://api.xifanacg.com/rest/v1/rpc/get_anime_detail",
                method = "POST",
                body = Json.parseToJsonElement("""{"p_id":"{subjectId}"}"""),
            ),
            channelIdPath = "$.code",
            episodeNumberPath = "$.episode_number",
            episodeKindPath = "$.kind",
            episodeKindPrefixes = mapOf(
                "main" to "", "special" to "SP", "sp" to "SP", "ova" to "OVA",
                "oad" to "OAD", "op" to "OP", "ed" to "ED", "pv" to "PV",
            ),
            episodeUrl = "https://next.xifanacg.com/anime/{subjectId}/play/{episodeId}?source={channelId}",
        ),
        playback = ApiPlayback(
            request = ApiRequest(
                url = "https://api.xifanacg.com/functions/v1/issue-web-playback",
                method = "POST",
                body = Json.parseToJsonElement("""{"action":"fallback","episode_id":"{episodeId}","source":"{channelId}"}"""),
            ),
            successPath = "$.ok",
            candidatesPath = "$.candidates[*]",
            candidateIdPath = "$.source_code",
            candidateUrlPath = "$.url",
        ),
        tier = MediaSourceTier(0u),
        channelTiers = mapOf("稀饭新番主线-2" to MediaSourceTier(0u), "稀饭旧番主线-1" to MediaSourceTier(1u)),
    )

    val all = listOf(xifanNext)
}
