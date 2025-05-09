/*
 * Ani
 * Copyright (C) 2022-2024 Him188
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.him188.ani.datasources.dmhy.impl.protocol

import me.him188.ani.datasources.api.topic.Alliance
import me.him188.ani.datasources.api.topic.Author
import me.him188.ani.datasources.api.topic.FileSize
import me.him188.ani.datasources.api.topic.FileSize.Companion.bytes
import me.him188.ani.datasources.api.topic.FileSize.Companion.gigaBytes
import me.him188.ani.datasources.api.topic.FileSize.Companion.kiloBytes
import me.him188.ani.datasources.api.topic.FileSize.Companion.megaBytes
import me.him188.ani.datasources.dmhy.DmhyCategory
import me.him188.ani.datasources.dmhy.DmhyTopic
import me.him188.ani.datasources.dmhy.impl.cache.Cache
import me.him188.ani.datasources.dmhy.impl.cache.getOrSet
import org.jsoup.nodes.Document
import org.jsoup.select.Elements
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

internal class ParseException(
    override val message: String?,
    override val cause: Throwable? = null,
) : Exception()

internal object ListParser {
    @JvmInline
    private value class Row(val elements: Elements) {
        val date: String get() = elements[0].child(0).text().trim()
        val categoryName: String get() = elements[1].child(0).text().trim()
        val categoryLink: String get() = elements[1].child(0).attr("href") // https://www.dmhy.org/topics/list/sort_id/2
        val allianceName: String? get() = elements[2].selectFirst("span.tag")?.select("a")?.text()?.trim()
        val allianceLink: String? get() = elements[2].select("span.tag").select("a").attr("href")
        val title: String
            get() {
                // alliance, title, comments count
                return elements[2].select("a").text().trim()
            }
        val commentsCount: String?
            get() {
                val element = elements[2]
                return if (allianceName.isNullOrEmpty()) {
                    if (element.childrenSize() == 2) {
                        element.child(1).text().trim()
                    } else {
                        null
                    }
                } else {
                    if (element.childrenSize() == 3) {
                        element.child(2).text().trim()
                    } else {
                        null
                    }
                }
            }
        val link: String get() = elements[2].select("a").last()!!.attr("href")
        val magnetLink: String get() = elements[3].child(0).attr("href")
        val size: String get() = elements[4].text()
        val authorName: String get() = elements.last()!!.text()
        val authorLink: String get() = elements.last()!!.select("a").attr("href")

        companion object {
            private val formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")
        }

        fun toTopic(context: Cache): DmhyTopic {
            val id = link.substringAfterLast('/')
            val categoryId = categoryLink.substringAfterLast('/')
            val authorId = authorLink.substringAfterLast('/')
            val allianceId = allianceLink?.substringAfterLast('/')
            val allianceName = allianceName
            return DmhyTopic(
                id = id,
                publishedTimeMillis = LocalDateTime.parse(date, formatter).toEpochSecond(ZoneOffset.ofHours(+8)) * 1000,
//                category = TopicCategory.ANIME,
                category = context.categories.getOrSet(categoryId) { DmhyCategory(categoryId, categoryName) },
                alliance = if (allianceId != null && allianceName != null)
                    context.alliances.getOrSet(allianceId) { Alliance(allianceId, allianceName) }
                else null,
                rawTitle = title.removePrefix(allianceName ?: "").trimStart(),
                commentsCount = commentsCount?.filter { it.isDigit() }?.toIntOrNull() ?: 0,
                magnetLink = magnetLink,
                size = createFileSize(size),
                author = context.users.getOrSet(authorId) { Author(authorId, authorName) },
                link = "https://dmhy.org/${link.removePrefix("/")}",
            )
        }

        private fun createFileSize(size: String): FileSize {
            // 430.1 MB
            return when {
                size.endsWith("GB") -> size.removeSuffix("GB").toFloat().gigaBytes
                size.endsWith("MB") -> size.removeSuffix("MB").toFloat().megaBytes
                size.endsWith("KB") -> size.removeSuffix("KB").toFloat().kiloBytes
                size.endsWith("B") -> size.removeSuffix("B").toFloat().bytes
                else -> throw IllegalArgumentException("Unrecognized size pattern: $size")
            }
        }
    }

    @Throws(ParseException::class)
    fun parseList(context: Cache, document: Document): List<DmhyTopic>? {
        val tbody = document.select("table.tablesorter").singleOrNull()
            ?: return null
        return tbody.select("tbody").single().children().map { Row(it.children()).toTopic(context) }
    }
}