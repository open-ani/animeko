/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.danmaku

import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import me.him188.ani.app.domain.danmaku.DandanplaySplitSeasonFixtures.ANI_SUBJECTS_FILE
import me.him188.ani.app.domain.danmaku.DandanplaySplitSeasonFixtures.DANDANPLAY_FILE
import me.him188.ani.app.domain.danmaku.DandanplaySplitSeasonFixtures.INDEX_FILE
import me.him188.ani.app.domain.danmaku.DandanplaySplitSeasonFixtures.RESOURCE_DIR
import me.him188.ani.app.domain.danmaku.DandanplaySplitSeasonFixtures.RecordedResponse
import me.him188.ani.app.domain.danmaku.DandanplaySplitSeasonFixtures.respondJson
import me.him188.ani.danmaku.api.provider.DanmakuFetchRequest
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.test.TestFactory
import me.him188.ani.test.runDynamicTests
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 用录制的 Ani 条目数据和弹弹 play 响应 (`resources/dandanplay-split-season`) 回放拆分季各集的弹幕自动匹配,
 * 不访问网络. 每一集的正确答案按标题和播出日期人工核对, 见该目录的 README.
 *
 * 按 `index.json` 每集回放两遍, 各是一个测试: 录制的响应, 以及剧集标题只留 "第x话" 的响应 (弹弹刚收录新一集还没有标题时),
 * 两遍的答案相同.
 */
class DandanplaySplitSeasonReplayTest {
    private val index = DandanplaySplitSeasonFixtures.json.decodeFromString(
        DandanplaySplitSeasonFixtures.Index.serializer(),
        readResource(INDEX_FILE),
    )
    private val subjects = DandanplaySplitSeasonFixtures.json.decodeFromString(
        MapSerializer(String.serializer(), JsonElement.serializer()),
        readResource(ANI_SUBJECTS_FILE),
    ).mapKeys { it.key.toInt() }.mapValues { DandanplaySplitSeasonFixtures.subjectOf(it.value) }
    private val responses = DandanplaySplitSeasonFixtures.json.decodeFromString(
        MapSerializer(String.serializer(), RecordedResponse.serializer()),
        readResource(DANDANPLAY_FILE),
    )

    @Test
    fun `every aired episode has an expectation`() {
        val recordedAt = PackedDate.parseFromDate(index.recordedAt)
        for (case in index.subjects) {
            val aired = subjects.getValue(case.subjectId).episodes
                .filter { it.airDate.isValid && it.airDate <= recordedAt }
                .map { it.sort }
            assertEquals(aired, case.episodes.map { EpisodeSort(it.sort) }, "Episodes of ${case.subjectId} ${case.name}")
        }
    }

    @TestFactory
    fun `replay every episode`() = runDynamicTests {
        for (case in index.subjects) {
            val subject = subjects.getValue(case.subjectId)
            for (expectation in case.episodes) {
                val episode = subject.episodes.single { it.sort == EpisodeSort(expectation.sort) }
                for (withTitles in listOf(true, false)) {
                    val name = "${case.subjectId} ${case.name} sort ${expectation.sort} ep ${episode.ep}" +
                            if (withTitles) "" else " without Dandanplay titles"
                    add(name) {
                        runTest {
                            val actual = match(subject.fetchRequest(episode), withTitles)
                            val correct = if (expectation.expected.isEmpty()) actual == null else actual in expectation.expected
                            assertTrue(correct, "${episode.name}: expected ${expectation.expected}, actual $actual")
                        }
                    }
                }
            }
        }
    }

    /**
     * 匹配到的弹弹 episodeId, 匹配不到为 `null`.
     *
     * @param withTitles 为 `false` 时剧集标题只留 "第x话".
     */
    private suspend fun match(request: DanmakuFetchRequest, withTitles: Boolean): Long? {
        var fetched: Long? = null
        val provider = DandanplaySplitSeasonFixtures.createProvider(
            appId = "replay",
            appSecret = "replay",
            onFetchComments = { fetched = it },
        ) { request ->
            val key = DandanplaySplitSeasonFixtures.requestKey(request)
            val response = checkNotNull(responses[key]) {
                "No recorded Dandanplay response for '$key'. Record the fixtures again, see $RESOURCE_DIR/README.md"
            }
            val body = if (withTitles) response.body else DandanplaySplitSeasonFixtures.withoutEpisodeTitles(response.body)
            respondJson(body.toString(), HttpStatusCode.fromValue(response.status))
        }
        provider.fetchAutomatic(request)
        return fetched
    }

    private fun readResource(name: String): String =
        checkNotNull(DandanplaySplitSeasonReplayTest::class.java.getResourceAsStream("/$RESOURCE_DIR/$name")) {
            "Missing resource $RESOURCE_DIR/$name"
        }.use { it.readBytes().decodeToString() }
}
