/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.datasources.api.test.codegen.main

import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import me.him188.ani.datasources.api.source.DownloadSearchQuery
import me.him188.ani.datasources.api.test.codegen.json
import me.him188.ani.datasources.api.topic.Topic
import me.him188.ani.datasources.api.topic.TopicCategory
import me.him188.ani.datasources.dmhy.impl.DmhyPagedSourceImpl
import me.him188.ani.datasources.dmhy.impl.protocol.Network
import me.him188.ani.utils.ktor.asScopedHttpClient
import me.him188.ani.utils.ktor.createDefaultHttpClient
import java.io.File
import kotlin.time.Clock


/**
 * 添加数据到 `testData` 目录中 (原始数据)
 */
suspend fun main() {
    val output = File("data-sources/api/test-codegen/testData").takeIf { it.exists() }
        ?: File("testData")

    val list = listOf(
        "约会大作战",
    )

    TopicFetcher(output, "dmhy").run {
        list.forEach {
            fetchAndSave(it)
        }
    }
}

class TopicFetcher(
    private val saveDir: File,
    private val dataSource: String,
) {
    suspend fun fetchTopics(name: String): List<Topic> {
        createDefaultHttpClient {
            followRedirects = true
            install(UserAgent) {
                agent = "open-ani/ani/3.0.0-beta01 (debug) (https://github.com/open-ani/ani)"
            }
            install(HttpTimeout) {
                connectTimeoutMillis = 30_000
                socketTimeoutMillis = 30_000
                requestTimeoutMillis = 30_000
            }
        }.use { http ->
            var count = 0
            val topics = DmhyPagedSourceImpl(
                DownloadSearchQuery(
                    keywords = name,
                    category = TopicCategory.ANIME,
                    allowAny = true,
                ),
                Network(http.asScopedHttpClient()),
            ).results.onEach {
                println("Fetched ${++count} topics")
            }.take(3000).toList()
            println(topics)
            return topics
        }
    }

    fun save(
        list: List<Topic>,
        originalName: String,
        className: String
    ) {
        val date = getCurrentDateString()
        val output = saveDir.resolve(if (className.isEmpty()) "mostRecentData${date}.json" else "$className.json")
        output.writeText(
            json.encodeToString(
                TestData(
                    originalName, dataSource = dataSource, className,
                    list.map {
                        TopicInfo(it.topicId, it.rawTitle)
                    },
                ),
            ),
        )
        println("'$className' total ${list.size} topics, saved to $output")
    }

    private fun getCurrentDateString(): String {
        val format = LocalDate.Format {
            year()
            monthNumber()
            dayOfMonth()
        }
        val date = format.format(Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date)
        return date
    }

    suspend fun fetchAndSave(originalName: String, className: String = originalName): List<Topic> {
        val topics = fetchTopics(originalName)
        save(topics, originalName, className)
        return topics
    }
}

@Serializable
class TestData(
    val originalName: String,
    val dataSource: String = "dmhy",
    /**
     * 不要太怪, 他会变成 `class XXX`
     */
    val kotlinClassName: String,
    val topics: List<TopicInfo>,
)

@Serializable
class TopicInfo(
    val id: String,
    val rawTitle: String,
)