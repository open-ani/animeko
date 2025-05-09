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

package me.him188.ani.datasources.dmhy

import me.him188.ani.datasources.api.topic.Alliance
import me.him188.ani.datasources.api.topic.Author
import me.him188.ani.datasources.api.topic.FileSize
import me.him188.ani.datasources.api.topic.titles.ParsedTopicTitle
import me.him188.ani.datasources.api.topic.titles.RawTitleParser

class DmhyCategory(
    val id: String,
    val name: String,
)

data class DmhyTopic(
    val id: String,
    val publishedTimeMillis: Long,
    val category: DmhyCategory,
    val alliance: Alliance?,
    val rawTitle: String,
    val commentsCount: Int,
    val magnetLink: String,
    val size: FileSize,
    val author: Author,
    val link: String,
) {
    val details: ParsedTopicTitle? by lazy {
        ParsedTopicTitle.Builder().apply {
            RawTitleParser.getDefault().parse(rawTitle, alliance?.name, this)
        }.build()
    }
}

private fun String.truncated(length: Int, truncated: String = "..."): String {
    return if (this.length > length) {
        this.take(length) + truncated
    } else {
        this
    }
}
