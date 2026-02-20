/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.datasources.api.test.codegen.main

import com.squareup.kotlinpoet.FileSpec
import me.him188.ani.datasources.api.test.codegen.TestGenerator
import me.him188.ani.datasources.api.test.codegen.json
import me.him188.ani.datasources.api.topic.titles.PatternBasedRawTitleParser
import me.him188.ani.datasources.api.topic.titles.RawTitleParser
import me.him188.ani.datasources.api.topic.titles.parse
import java.io.File

/**
 * 从 `testData` 目录中生成单元测试
 */
fun main(args: Array<String>) { // 直接 run 就行
    val inputDir = File(args.getOrNull(0) ?: "testData")
    val outputDirSkiko = File(args.getOrNull(1) ?: "../src/skikoTest/kotlin/title/generated")
    val outputDirAndroid = File(args.getOrNull(1) ?: "../src/androidDeviceTest/kotlin/title/generated")

    val suites = inputDir.walk().filter { it.extension == "json" }
        .map {
            try {
                json.decodeFromString<TestData>(it.readText())
            } catch (e: Exception) {
                throw IllegalStateException("Failed to parse ${it.name}", e)
            }
        }
        .toList()

    println("Found ${suites.size} test data, total ${suites.sumOf { it.topics.size }} topics")

    val output = buildString {
        appendLine(
            "raw\tchinese_title\tother_title\ttags",
        )
        val parser = PatternBasedRawTitleParser()
        for (data in suites) {
            for (topic in data.topics) {
                val parsed = parser.parse(topic.rawTitle)
                appendLine(
                    "${topic.rawTitle}\t" +
                            "${parsed.chineseTitle}\t" +
                            "${parsed.otherTitles.getOrNull(0).orEmpty()}\t" +
                            parsed.tags.joinToString(","),
                )
            }
        }
    }
    outputDirSkiko.resolve("dataset.tsv").writeText(output)

    TestGenerator(RawTitleParser.getDefault()).run {
        for (data in suites) {
//            val out = outputDir.resolve(data.kotlinClassName + ".kt")
            println("Generating suite '${data.kotlinClassName}'")
            val suite = createSuite(data)
            generateSuite(suite, chunkSize = 1).generateTo(outputDirSkiko)
            // 安卓需要把 10 个 test 合并成一个来减少 test 函数数量. 否则 instrumented test 会报错.
            generateSuite(suite, chunkSize = 10).generateTo(outputDirAndroid)
        }
    }
}

private fun FileSpec.generateTo(outputDir: File) {
    writeTo(outputDir)
    outputDir.resolve("$name.kt").run {
        writeText("// @formatter:off\n" + readText() + "\n// @formatter:on\n")
    }
}