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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 用录制的 Ani 条目数据和弹弹 play 响应 (`resources/dandanplay-split-season`) 回放拆分季各集的弹幕自动匹配,
 * 不访问网络. 每一集的正确答案按标题和播出日期人工核对, 见该目录的 README.
 *
 * 每个条目回放两遍: 录制的响应, 以及剧集标题只留 "第x话" 的响应 (弹弹刚收录新一集还没有标题时), 两遍的答案相同.
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

    @Test
    fun `ReZero S2 part 2`() = replay(316247)

    @Test
    fun `ReZero S3 part 2`() = replay(510728)

    @Test
    fun `ReZero S4 part 1`() = replay(547888)

    /**
     * #3414
     */
    @Test
    fun `ReZero S4 part 2`() = replay(633836)

    @Test
    fun `Mushoku Tensei S1 part 2`() = replay(325585)

    @Test
    fun `Mushoku Tensei S2 part 2`() = replay(444557)

    @Test
    fun `Attack on Titan S3 part 2`() = replay(263750)

    @Test
    fun `Attack on Titan final season part 2`() = replay(331752)

    @Test
    fun `Spy x Family part 2`() = replay(373267)

    @Test
    fun `Ancient Magus Bride S2 part 2`() = replay(442523)

    @Test
    fun `86 part 2`() = replay(331887)

    @Test
    fun `Moriarty the Patriot part 2`() = replay(319252)

    @Test
    fun `Haikyuu To the Top part 2`() = replay(295421)

    @Test
    fun `Fire Force S3 part 2`() = replay(502876)

    @Test
    fun `Sakamoto Days part 2`() = replay(524005)

    @Test
    fun `JoJo Stone Ocean part 1`() = replay(332261)

    @Test
    fun `JoJo Stone Ocean part 2`() = replay(375913)

    @Test
    fun `JoJo Stone Ocean part 3`() = replay(395782)

    @Test
    fun `Misfit of Demon King Academy S2 part 2`() = replay(455981)

    private fun replay(subjectId: Int) = runTest {
        val case = index.subjects.single { it.subjectId == subjectId }
        val subject = subjects.getValue(subjectId)
        val failures = buildList {
            for (expectation in case.episodes) {
                val episode = subject.episodes.single { it.sort == EpisodeSort(expectation.sort) }
                for (withTitles in listOf(true, false)) {
                    val actual = match(subject.fetchRequest(episode), withTitles)
                    val correct = if (expectation.expected.isEmpty()) actual == null else actual in expectation.expected
                    if (!correct) {
                        add(
                            "sort ${expectation.sort} (ep ${episode.ep}, ${episode.name})" +
                                    (if (withTitles) "" else " without Dandanplay titles") +
                                    ": expected ${expectation.expected}, actual $actual",
                        )
                    }
                }
            }
        }
        assertTrue(failures.isEmpty(), "${case.subjectId} ${case.name}:\n" + failures.joinToString("\n"))
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
