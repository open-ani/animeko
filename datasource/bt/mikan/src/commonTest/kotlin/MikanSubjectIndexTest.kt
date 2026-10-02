package me.him188.ani.datasources.mikan

import me.him188.ani.test.readTestResourceAsString
import me.him188.ani.utils.xml.Xml
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MikanSubjectIndexTest {

    @Test
    fun `can parse subject index`() {
        val ids = AbstractMikanMediaSource.parseMikanSubjectIdsFromSearch(
            Xml.parse(
                readTestResourceAsString("/mikan-search-无职转生.txt"),
            ),
        )
        assertEquals(listOf(3060, 2353, 2549, 3344).map { it.toString() }, ids)
    }

    @Test
    fun `can parse bangumi subject id`() {
        val id = AbstractMikanMediaSource.parseBangumiSubjectIdFromMikanSubjectDetails(
            Xml.parse(
                readTestResourceAsString("/mikan-bangumi-无职转生.txt"),
            ),
        )
        assertEquals("373247", id)
    }

    @Test
    fun `can parse subject rss`() {
        val list = AbstractMikanMediaSource.parseRssTopicList(
            Xml.parse(
                readTestResourceAsString("/mikan-subject-rss-无职转生.txt"),
            ),
            "",
        )
        assertEquals(318, list.size)
        assertTrue {
            list.all { it.originalLink.isNotBlank() }
        }
        assertTrue {
            list.all { it.topicId.isNotBlank() }
        }
        assertTrue {
            list.all { it.downloadLink.uri.isNotBlank() }
        }
    }
}