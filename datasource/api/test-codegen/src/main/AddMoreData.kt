/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.datasources.api.test.codegen.main

import io.ktor.client.plugins.UserAgent
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import me.him188.ani.datasources.api.source.DownloadSearchQuery
import me.him188.ani.datasources.api.test.codegen.json
import me.him188.ani.datasources.api.topic.Topic
import me.him188.ani.datasources.api.topic.TopicCategory
import me.him188.ani.datasources.dmhy.impl.DmhyPagedSourceImpl
import me.him188.ani.datasources.dmhy.impl.protocol.Network
import me.him188.ani.utils.ktor.asScopedHttpClient
import me.him188.ani.utils.ktor.createDefaultHttpClient
import java.io.File


/**
 * 添加数据到 `testData` 目录中 (原始数据)
 */
suspend fun main() {
    val output = File("data-sources/api/test-codegen/testData").takeIf { it.exists() }
        ?: File("testData")

    val list = listOf(
        "怪兽8号", // 带数字
        "無職轉生～到了異世界就拿出真本事～", // 许多季度, 超长名字
        "樱Trick", // 带英文
        "终末列车去往何方",
        "迷宫饭",
        "吹响吧！上低音号",
        "间谍过家家",
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

        }.use { http ->
            val topics = DmhyPagedSourceImpl(
                DownloadSearchQuery(
                    keywords = name,
                    category = TopicCategory.ANIME,
                    allowAny = true,
                ),
                Network(http.asScopedHttpClient()),
            ).results.toList()
            println(topics)
            return topics
        }
    }

    fun save(
        list: List<Topic>,
        originalName: String,
        className: String
    ) {
        val output = saveDir.resolve("$className.json")
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