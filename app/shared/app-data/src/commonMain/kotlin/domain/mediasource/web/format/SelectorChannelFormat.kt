/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.web.format

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import me.him188.ani.app.domain.mediasource.web.SelectorMediaSourceEngine
import me.him188.ani.app.domain.mediasource.web.WebSearchEpisodeInfo
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.utils.xml.Element
import me.him188.ani.utils.xml.QueryParser
import me.him188.ani.utils.xml.parseSelectorOrNull
import org.intellij.lang.annotations.Language

/**
 * 决定如何匹配线路和剧集
 * @see SelectorMediaSourceEngine
 */
sealed class SelectorChannelFormat<in Config : SelectorFormatConfig>(override val id: SelectorFormatId) :
    SelectorFormat {
    /**
     * @return `null` for invalid config
     * @see baseUrl must not end with `/`
     */
    abstract fun select(
        page: Element,
        baseUrl: String,
        config: Config,
    ): SelectedChannelEpisodes?

    companion object {
        val entries by lazy { // 必须 lazy, 否则可能获取到 null
            listOf(SelectorChannelFormatNoChannel, SelectorChannelFormatIndexGrouped)
        }

        fun findById(id: SelectorFormatId): SelectorChannelFormat<*>? {
            return entries.find { it.id == id }
        }

        @Language("regexp")
        const val DEFAULT_MATCH_EPISODE_SORT_FROM_NAME = """第\s*(?<ep>.+)\s*[话集]"""

        private fun isPossiblyMovie(title: String): Boolean {
            // https://github.com/open-ani/animeko/pull/1171#issuecomment-2466115293
            if (title == "正片" || title == "高清版") return true

            return "2160P" in title || "1440P" in title || "2K" in title || "4K" in title || "1080P" in title || "720P" in title
        }

        fun convertSpecialEpisodes(name: String, raw: String?): EpisodeSort {
            val rawEp = raw?.let { EpisodeSort(it) }
                ?: EpisodeSort(name)
            return if ((rawEp !is EpisodeSort.Normal) && isPossiblyMovie(name)) {
                EpisodeSort(1) // 电影总是 01
            } else {
                rawEp
            }
        }
    }
}

data class SelectedChannelEpisodes(
    /**
     * `null` 表示该 format 不支持 (不考虑) channels
     */
    val channels: List<String>?,
    val episodes: List<WebSearchEpisodeInfo>,
)


/**
 * tab row + horizontal pager 形式
 */
data object SelectorChannelFormatIndexGrouped :
    SelectorChannelFormat<SelectorChannelFormatIndexGrouped.Config>(SelectorFormatId("index-grouped")) {
    @Immutable
    @Serializable
    data class Config(
        @param:Language("css")
        val selectChannelNames: String = ".anthology-tab > .swiper-wrapper a",
        @param:Language("regexp")
        val matchChannelName: String = """^(?<ch>.+?)(\d+)?$""", // empty to use full text
        @param:Language("css")
        val selectEpisodeLists: String = ".anthology-list-box",

        /**
         * 提取剧集名称. 如果是 `<a>`, 还能提取链接
         */
        @param:Language("css")
        val selectEpisodesFromList: String = "a",
        /**
         * 如果 [selectEpisodesFromList] 不返回 `<a>`, 则此项必须有
         */
        @param:Language("css")
        val selectEpisodeLinksFromList: String = "",

        @param:Language("regexp")
        val matchEpisodeSortFromName: String = DEFAULT_MATCH_EPISODE_SORT_FROM_NAME,
    ) : SelectorFormatConfig {
        override fun isValid(): Boolean {
            return selectChannelNames.isNotBlank() && selectEpisodeLists.isNotBlank() && selectEpisodesFromList.isNotBlank() && matchEpisodeSortFromName.isNotBlank()
        }
    }

    override fun select(
        page: Element,
        baseUrl: String,
        config: Config,
    ): SelectedChannelEpisodes? {
        val selectChannelNames = QueryParser.parseSelectorOrNull(config.selectChannelNames) ?: return null
        // null to use full text
        val matchChannelName = config.matchChannelName.let { expr ->
            if (expr.isEmpty()) {
                null
            } else {
                Regex.parseOrNull(expr) // this null means invalid regex, which is not acceptable
                    ?: return null
            }
        }
        val selectEpisodesFromList = QueryParser.parseSelectorOrNull(config.selectEpisodesFromList) ?: return null
        val selectLists = QueryParser.parseSelectorOrNull(config.selectEpisodeLists) ?: return null
        val matchEpisodeSortFromNameRegex = Regex.parseOrNull(config.matchEpisodeSortFromName) ?: return null

        // null means no match, will be filtered out
        val channelNames = page.select(selectChannelNames)
            .map { e ->
                val text = e.text().trim().takeIf { it.isNotBlank() } ?: return@map null
                if (matchChannelName == null) {
                    text
                } else {
                    matchChannelName.findGroupOrFullText(text, "ch") // null means no match
                }
            }

        val lists = page.select(selectLists)

        val episodes = lists.asSequence()
            .zip(channelNames.asSequence()) { list, channelName ->
                if (channelName == null) {
                    return@zip emptyList()
                }
                val links = selectLinksOrNull(config.selectEpisodeLinksFromList, list)

                list.select(selectEpisodesFromList).mapIndexedNotNull { index, a ->
                    val text = a.text()
//                if (text in channelNames) return@mapNotNull null

                    val href = links?.getOrNull(index) ?: a.attr("href")
                    WebSearchEpisodeInfo(
                        channel = channelName,
                        name = text,
                        episodeSortOrEp = convertSpecialEpisodes(
                            text,
                            raw = matchEpisodeSortFromNameRegex.findGroupOrFullText(text, "ep"),
                        ),
                        playUrl = SelectorHelpers.computeAbsoluteUrl(baseUrl, href),
                    )
                }
            }
            .flatten()

        return SelectedChannelEpisodes(
            channelNames.filterNotNull(),
            episodes.toList(),
        )
    }

    // 注释掉的是 flatten 实现, 名字包含 channel (但没有测试过)

//    override fun select(
//        page: Element,
//        baseUrl: String,
//        config: Config,
//    ): SelectedChannelEpisodes? {
//        val selectChannels = QueryParser.parseSelectorOrNull(config.selectChannelNames) ?: return null
//        val selectElements = QueryParser.parseSelectorOrNull(config.selectEpisodesFromList) ?: return null
//        val selectLists = QueryParser.parseSelectorOrNull(config.selectLists) ?: return null
//        val matchEpisodeSortFromNameRegex = Regex.parseOrNull(config.matchEpisodeSortFromName) ?: return null
//
//        val channels = page.select(selectChannels)
//            .map { e -> e.text().trim() }
//
//        fun parseEps(ep: Element, channel: String?): List<WebSearchEpisodeInfo> {
//            return ep.select(selectElements).mapNotNull { a ->
//                val text = a.text()
//                if (text in channels) return@mapNotNull null
//
//                val href = a.attr("title").takeIf { it.isNotBlank() } ?: a.attr("href")
//                WebSearchEpisodeInfo(
//                    channel = channel,
//                    name = text,
//                    episodeSort = matchEpisodeSortFromNameRegex.find(text)?.groups?.get("ep")?.value
//                        ?.let { EpisodeSort(it) }
//                        ?: EpisodeSort(text),
//                    playUrl = SelectorHelpers.computeAbsoluteUrl(baseUrl, href),
//                )
//            }
//        }
//
//        return SelectedChannelEpisodes(
//            channels,
//            page.select(selectLists)
//                .flatMapIndexed { i, e ->
//                    val channel = channels.getOrNull(i)
//                    parseEps(e, channel)
//                },
//        )
//    }
}

/**
 * 没有线路, 或者相当于只有一个线路. 只有一个 list of episodes
 */
data object SelectorChannelFormatNoChannel :
    SelectorChannelFormat<SelectorChannelFormatNoChannel.Config>(SelectorFormatId("no-channel")) {
    @Immutable
    @Serializable
    data class Config(
        /**
         * 提取剧集名称. 如果是 `<a>`, 还能提取链接
         */
        @param:Language("css")
        val selectEpisodes: String = "#glist-1 > div.module-blocklist.scroll-box.scroll-box-y > div > a",
        /**
         * 如果 [selectEpisodes] 不返回 `<a>`, 则此项必须有
         */
        @param:Language("css")
        val selectEpisodeLinks: String = "",
        @param:Language("regexp")
        val matchEpisodeSortFromName: String = DEFAULT_MATCH_EPISODE_SORT_FROM_NAME,
    ) : SelectorFormatConfig {
        val matchEpisodeSortFromNameRegex by lazy(LazyThreadSafetyMode.PUBLICATION) {
            Regex.parseOrNull(matchEpisodeSortFromName)
        }

        override fun isValid(): Boolean {
            return selectEpisodes.isNotBlank() && matchEpisodeSortFromName.isNotBlank()
        }
    }

    override fun select(
        page: Element,
        baseUrl: String,
        config: Config,
    ): SelectedChannelEpisodes? {
        val regex = config.matchEpisodeSortFromNameRegex ?: return null
        val selectEpisodes = QueryParser.parseSelectorOrNull(config.selectEpisodes) ?: return null

        val links = selectLinksOrNull(config.selectEpisodeLinks, page)

        return SelectedChannelEpisodes(
            null,
            page.select(selectEpisodes).mapIndexed { index, a ->
                val text = a.text()
                val href = links?.getOrNull(index) ?: a.attr("href")
                WebSearchEpisodeInfo(
                    channel = null,
                    name = text,
                    episodeSortOrEp = convertSpecialEpisodes(
                        text,
                        raw = regex.findGroupOrFullText(text, "ep"),
                    ),
                    playUrl = SelectorHelpers.computeAbsoluteUrl(baseUrl, href),
                )
            },
        )
    }
}

private fun selectLinksOrNull(selectEpisodeLinks: String, parent: Element): List<String>? {
    return selectEpisodeLinks.takeIf { it.isNotBlank() }
        ?.let {
            QueryParser.parseSelectorOrNull(it)
        }
        ?.let { evaluator ->
            parent.select(evaluator).mapNotNull { element ->
                element.attr("href").takeIf { it.isNotBlank() }
            }
        }
}

/**
 * 匹配并提取 group [groupName]. 如果没有 group, 就返回整个 text.
 *
 * 当未匹配到时返回 `null`.
 */
private fun Regex.findGroupOrFullText(
    text: String,
    groupName: String,
): String? {
    val result = find(text) ?: return null
    // matched
    result.groups.getOrNull(groupName)?.let { group ->
        return group.value
    }
    return text
}


private fun MatchGroupCollection.getOrNull(name: String): MatchGroup? {
    return try {
        get(name)
    } catch (_: IllegalArgumentException) {
        return null
    }
} 
