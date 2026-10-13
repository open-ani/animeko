/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.web

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.utils.ktor.asScopedHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 测试 [SelectorMediaSourceEngine.selectMedia] 把剧集转成 media 时使用的集号, 以及
 * [SelectorSearchConfig.filterByEpisodeSort] 的过滤结果.
 *
 * 用例取自实测: https://www.yinghua2.com 上的剧场版条目页把同一部作品的不同配音列成多条,
 * 名称只有画质与语言 (如「铃芽之旅」的 9 条线路里有 5 条名为 HD高清国语版 / HD高清原声版 / HD中字),
 * 集号只能解析成 EpisodeSort.Unknown, 开着 filterByEpisodeSort 时这些线路全被过滤掉.
 */
class SelectorMediaSourceEngineSelectMediaTest {
    // selectMedia 不发请求, client 只是构造 engine 用
    private val engine = DefaultSelectorMediaSourceEngine(
        HttpClient(MockEngine { respond("") }).asScopedHttpClient(),
    )

    /** 站点只标画质与语言的条目 */
    private fun labeled(label: String) = WebSearchEpisodeInfo(
        channel = "线路1",
        name = label,
        episodeSortOrEp = EpisodeSort(label),
        playUrl = "https://example.com/$label",
    )

    private fun numbered(sort: Int) = WebSearchEpisodeInfo(
        channel = "线路1",
        name = "第0${sort}集",
        episodeSortOrEp = EpisodeSort(sort),
        playUrl = "https://example.com/$sort",
    )

    private fun selectMedia(
        episodes: List<WebSearchEpisodeInfo>,
        episodeSort: EpisodeSort = EpisodeSort(1),
    ) = engine.selectMedia(
        episodes.asSequence(),
        SelectorSearchConfig.Empty,
        SelectorSearchQuery(
            subjectName = SUBJECT_NAME,
            allSubjectNames = setOf(SUBJECT_NAME),
            episodeSort = episodeSort,
            episodeEp = episodeSort,
            episodeName = null,
        ),
        mediaSourceId = "test",
        subjectName = SUBJECT_NAME,
    )

    @Test
    fun `whole work labels are matched as episode 01`() {
        val result = selectMedia(listOf(labeled("HD高清国语版"), labeled("HD高清原声版")))
        assertEquals(2, result.filteredList.size)
        assertEquals(
            listOf(EpisodeRange.single(EpisodeSort(1)), EpisodeRange.single(EpisodeSort(1))),
            result.filteredList.map { it.episodeRange },
        )
    }

    @Test
    fun `whole work labels are not matched for other episodes`() {
        val result = selectMedia(listOf(labeled("HD中字")), episodeSort = EpisodeSort(5))
        assertEquals(1, result.originalList.size)
        assertEquals(emptyList(), result.filteredList)
    }

    @Test
    fun `a label carrying more than quality and language is not a whole work`() {
        // 「剧场版01」的集号在字符串里, 要靠站点自己的集号正则; 「全集」是整季合集;
        // 「铃芽之旅（普通话版）」带作品名 —— 都不能当成第 1 集
        val page = listOf(labeled("剧场版01"), labeled("全集"), labeled("铃芽之旅（普通话版）"))
        val result = selectMedia(page)
        assertEquals(3, result.originalList.size)
        assertEquals(emptyList(), result.filteredList)
    }

    @Test
    fun `parsed sorts are not affected`() {
        val result = selectMedia(listOf(numbered(1), numbered(2), numbered(3)), episodeSort = EpisodeSort(2))
        assertEquals(1, result.filteredList.size)
        assertEquals("第02集", result.filteredList.single().properties.episodeName)
    }

    private companion object {
        private const val SUBJECT_NAME = "铃芽之旅"
    }
}
