package me.him188.ani.datasources.api.test.codegen.main

import io.ktor.client.HttpClient
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.ContentConverter
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.reflect.TypeInfo
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.charsets.Charset
import io.ktor.utils.io.charsets.decode
import io.ktor.utils.io.jvm.javaio.toInputStream
import io.ktor.utils.io.streams.asInput
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.him188.ani.datasources.api.source.DownloadSearchQuery
import me.him188.ani.datasources.api.test.codegen.json
import me.him188.ani.datasources.api.topic.Topic
import me.him188.ani.datasources.api.topic.TopicCategory
import me.him188.ani.datasources.dmhy.impl.DmhyPagedSourceImpl
import me.him188.ani.datasources.dmhy.impl.protocol.Network
import me.him188.ani.utils.ktor.getPlatformKtorEngine
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
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
        HttpClient(getPlatformKtorEngine()) {
            followRedirects = true

            install(UserAgent) {
                agent = "open-ani/ani/3.0.0-beta01 (debug) (https://github.com/open-ani/ani)"
            }

            install(HttpCookies)

            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                    },
                )
                register(
                    ContentType.Text.Html,
                    object : ContentConverter {
                        override suspend fun deserialize(
                            charset: Charset,
                            typeInfo: TypeInfo,
                            content: ByteReadChannel
                        ): Any? {
                            if (typeInfo.type.qualifiedName != Document::class.qualifiedName) return null
                            content.awaitContent()
                            val decoder = Charsets.UTF_8.newDecoder()
                            val string = decoder.decode(content.toInputStream().asInput())
                            return Jsoup.parse(string, charset.name())
                        }

                        override suspend fun serializeNullable(
                            contentType: ContentType,
                            charset: Charset,
                            typeInfo: TypeInfo,
                            value: Any?
                        ): OutgoingContent? = null
                    },
                ) {}
            }

        }.use { http ->
            val topics = DmhyPagedSourceImpl(
                DownloadSearchQuery(
                    keywords = name,
                    category = TopicCategory.ANIME,
                    allowAny = true,
                ),
                Network(http),
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