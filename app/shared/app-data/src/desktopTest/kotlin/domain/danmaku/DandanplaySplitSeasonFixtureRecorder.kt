/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.danmaku

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import me.him188.ani.app.domain.danmaku.DandanplaySplitSeasonFixtures.ANI_SUBJECTS_FILE
import me.him188.ani.app.domain.danmaku.DandanplaySplitSeasonFixtures.DANDANPLAY_FILE
import me.him188.ani.app.domain.danmaku.DandanplaySplitSeasonFixtures.ENV_APP_ID
import me.him188.ani.app.domain.danmaku.DandanplaySplitSeasonFixtures.ENV_APP_SECRET
import me.him188.ani.app.domain.danmaku.DandanplaySplitSeasonFixtures.ENV_FIXTURE_DIR
import me.him188.ani.app.domain.danmaku.DandanplaySplitSeasonFixtures.INDEX_FILE
import me.him188.ani.app.domain.danmaku.DandanplaySplitSeasonFixtures.RecordedResponse
import me.him188.ani.app.domain.danmaku.DandanplaySplitSeasonFixtures.respondJson
import me.him188.ani.datasources.api.PackedDate
import java.io.File
import kotlin.test.Test

/**
 * 录制 `resources/dandanplay-split-season` 的条目与弹弹 play 数据. 只在设置了 [ENV_FIXTURE_DIR] 时运行, 用法见该目录的 README.
 *
 * 条目数据取自 Ani 服务端, 与 app 看到的相同. 弹弹 play 的数据由真正的 [me.him188.ani.danmaku.dandanplay.DandanplayDanmakuProvider]
 * 对每一集分别带着和不带拆分季信息各匹配一次时发出的请求得到, 所以回放时这两种匹配都不会缺少响应.
 * 不请求弹幕列表, 不使用文件匹配.
 */
class DandanplaySplitSeasonFixtureRecorder {
    @OptIn(ExperimentalSerializationApi::class)
    private val prettyJson = Json(DandanplaySplitSeasonFixtures.json) {
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    @Test
    fun record() = runBlocking {
        val dir = System.getenv(ENV_FIXTURE_DIR)?.let(::File) ?: return@runBlocking
        val appId = checkNotNull(System.getenv(ENV_APP_ID)) { "$ENV_APP_ID is required" }
        val appSecret = checkNotNull(System.getenv(ENV_APP_SECRET)) { "$ENV_APP_SECRET is required" }
        val index = DandanplaySplitSeasonFixtures.json.decodeFromString(
            DandanplaySplitSeasonFixtures.Index.serializer(),
            dir.resolve(INDEX_FILE).readText(),
        )
        val recordedAt = PackedDate.parseFromDate(index.recordedAt)

        HttpClient(OkHttp).use { http ->
            val aniSubjects = index.subjects.associate { case ->
                val body = http.get("$ANI_SERVER/v2/subjects/${case.subjectId}") {
                    header(HttpHeaders.UserAgent, USER_AGENT)
                }.also { check(it.status.isSuccess()) { "Failed to fetch subject ${case.subjectId}: ${it.status}" } }
                    .bodyAsText()
                case.subjectId.toString() to DandanplaySplitSeasonFixtures.trimAniSubject(
                    DandanplaySplitSeasonFixtures.json.parseToJsonElement(body),
                )
            }

            val recorded = sortedMapOf<String, RecordedResponse>()
            val provider = DandanplaySplitSeasonFixtures.createProvider(appId, appSecret, onFetchComments = {}) { request ->
                val key = DandanplaySplitSeasonFixtures.requestKey(request)
                val response = recorded[key] ?: run {
                    check(request.method == HttpMethod.Get) { "Only GET requests can be recorded: $key" }
                    delay(REQUEST_INTERVAL_MILLIS)
                    val live = http.request(request.url) {
                        method = request.method
                        headers.appendAll(request.headers)
                        header(HttpHeaders.UserAgent, USER_AGENT)
                    }
                    val text = live.bodyAsText()
                    val body = runCatching { DandanplaySplitSeasonFixtures.json.parseToJsonElement(text) }.getOrElse { JsonNull }
                    RecordedResponse(live.status.value, DandanplaySplitSeasonFixtures.trimDandanplayResponse(body))
                        .also { recorded[key] = it }
                }
                respondJson(response.body.toString(), HttpStatusCode.fromValue(response.status))
            }

            for (case in index.subjects) {
                val subject = DandanplaySplitSeasonFixtures.subjectOf(aniSubjects.getValue(case.subjectId.toString()))
                for (episode in subject.episodes) {
                    if (!episode.airDate.isValid || episode.airDate > recordedAt) continue
                    provider.fetchAutomatic(subject.fetchRequest(episode, withSplitSeason = true))
                    provider.fetchAutomatic(subject.fetchRequest(episode, withSplitSeason = false))
                }
            }

            dir.resolve(ANI_SUBJECTS_FILE).writeText(
                prettyJson.encodeToString(MapSerializer(String.serializer(), JsonElement.serializer()), aniSubjects) + "\n",
            )
            dir.resolve(DANDANPLAY_FILE).writeText(
                prettyJson.encodeToString(MapSerializer(String.serializer(), RecordedResponse.serializer()), recorded) + "\n",
            )
        }
    }

    private companion object {
        const val ANI_SERVER = "https://danmaku-cn.myani.org"
        const val USER_AGENT = "open-ani/animeko (dandanplay split season fixture recorder)"
        const val REQUEST_INTERVAL_MILLIS = 300L
    }
}
