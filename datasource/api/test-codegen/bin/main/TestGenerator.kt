/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.datasources.api.test.codegen

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.TypeSpec
import kotlinx.serialization.json.Json
import me.him188.ani.datasources.api.test.codegen.main.TestData
import me.him188.ani.datasources.api.topic.Topic
import me.him188.ani.datasources.api.topic.titles.ParsedTopicTitle
import me.him188.ani.datasources.api.topic.titles.RawTitleParser
import me.him188.ani.datasources.api.topic.titles.parse

val json = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
}

class TopicSet(
    val topics: List<Topic>,
)

class TestSuite(
    val originalName: String,
    val dataSource: String,
    val name: String,
    val cases: List<TestCase>
)

class TestCase(
    val name: String,
    val title: String,
    val parsed: ParsedTopicTitle,
)

class TestGenerator(
    private val parser: RawTitleParser
) {
    fun createSuite(testData: TestData): TestSuite {
        fun String.sanitize() = replace("%", "_")
            .replace(".", "_")
            .replace("-", "_")
            .replace("!", "_")
            .replace("！", "_")
            .replace("～", "_")

        return TestSuite(
            originalName = testData.originalName.sanitize(),
            name = testData.kotlinClassName.sanitize(),
            dataSource = testData.dataSource,
            cases = testData.topics.map {
                TestCase(
                    name = it.id.removeSuffix(".html").sanitize(),
                    title = it.rawTitle,
                    parsed = parser.parse(it.rawTitle),
                )
            },
        )
    }

    // 这库真是各种难用
    /**
     * @param chunkSize Every chunk of [chunkSize] cases are fused into one function.
     */
    fun generateSuite(suite: TestSuite, chunkSize: Int = 1): FileSpec = FileSpec.builder(
        "", // 他会创建目录层级
        "PatternTitleParserTest${suite.name}",
    ).apply {
        addAnnotation(
            AnnotationSpec.builder(ClassName.bestGuess("Suppress")).apply {
                addMember("\"FunctionName\"")
                addMember("\"ClassName\"")
                addMember("\"RedundantVisibilityModifier\"")
                addMember("\"PackageDirectoryMismatch\"")
                addMember("\"NonAsciiCharacters\"")
                addMember("\"SpellCheckingInspection\"")
            }.build(),
        )
        addImport("me.him188.ani.datasources.api.title", "PatternBasedTitleParserTestSuite")
        addImport("kotlin.test", "assertEquals") // 它不允许 "*"
        addImport("me.him188.ani.datasources.api", "SubtitleKind") // 它不允许 "*"
        addType(
            TypeSpec.classBuilder(
                ClassName(
                    "me.him188.ani.datasources.api.title.generated", // 这库并不会写出 package
                    "PatternTitleParserTest${suite.name}",
                ),
            ).apply {
                addKdoc(
                    """
                        原名: `${suite.originalName}`
                        数据源: `${suite.dataSource}`
                        
                        由 `test-codegen` 的 `GenerateTests.kt` 生成, 不要手动修改!
                        如果你优化了解析器, 这些 test 可能会失败, 请检查是否它是因为以前解析错误而现在解析正确了. 
                        如果是, 请更新测试数据: 执行 `GenerateTests.kt`.
                """.trimIndent(),
                )
                superclass(ClassName("me.him188.ani.datasources.api.title", "PatternBasedTitleParserTestSuite"))
                for (chunk in suite.cases.chunked(chunkSize)) {
                    val functionName = if (chunk.size == 1) {
                        chunk.single().name
                    } else {
                        chunk.joinToString("-") { it.name.substringBefore("_") }
                    }
                    val functionBuilder = FunSpec.builder(functionName)
                        .addAnnotation(ClassName.bestGuess("kotlin.test.Test"))

                    for (case in chunk) with(case.parsed) {
                        // 这库会自动 wrap code, 如果不写 %S 就可能出问题
                        // 他不会自动换行, 必须要有 + "\n"
                        functionBuilder.addCode("kotlin.run {\n")
                        functionBuilder.addCode(
                            """val r = parse(%S)""" + "\n", case.title,
                        )
                            .addCode("assertEquals(%S, r.episodeRange.toString())" + "\n", episodeRange.toString())
                            .addCode(
                                "assertEquals(%S, r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })" + "\n",
                                subtitleLanguages.sortedBy { it.id }.joinToString { it.id },
                            )
                            .addCode(
                                "assertEquals(%S, r.resolution.toString())" + "\n",
                                resolution.toString(),
                            )
                            .run {
                                if (subtitleKind == null) {
                                    addCode("assertEquals(null, r.subtitleKind)" + "\n")
                                } else {
                                    addCode(
                                        "assertEquals(SubtitleKind.%L, r.subtitleKind)" + "\n",
                                        subtitleKind,
                                    )
                                }
                            }
                        functionBuilder.addCode("}\n")
                    }

                    addFunction(
                        functionBuilder
                            .build(),
                    )
                }
            }.build(),
        )
    }.build()
}

private const val Q = "\"\"\""
