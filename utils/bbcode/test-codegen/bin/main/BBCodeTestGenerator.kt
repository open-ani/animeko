package me.him188.ani.utils.bbcode

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.TypeSpec
import org.intellij.lang.annotations.Language
import java.io.File
import kotlin.math.absoluteValue

fun main() {
    val dir =
        System.getProperty("user.dir").let(::File).resolve("utils/bbcode/src/commonTest/kotlin/")
    check(dir.exists()) {
        "Directory not found: $dir"
    }


    val contextTags =
        listOf("b", "i", "u", "s", "url", "img", "quote", "code", "mask")
            .flatMap {
                listOf(it, it.uppercase())
            }

    BBCodeTestGenerator("Basics").apply {
        case("[b]Hello World![/b]")
        case("[url]https://example.com[/url]")
        case("[URL]https://example.com[/URL]")
        case("[url=https://example.com]Hello World![/url]")
        case("[url=http://example.com]Hello World![/url]")
        case("""[url="http://example.com"]Hello World![/url]""")
        case("""[url="http://example.com\n"]Hello World![/url]""")
        case("[url=invalidurl]Hello World![/url]")
        case("[URL=http://example.com]Hello World![/URL]")
        case("[URL=invalidurl]Hello World![/URL]")
        case("[img]http://example.com[/img]")
        case("[IMG]http://example.com[/IMG]")
        case("[size=1]Hello World![/size]")
        case("[color=red]Hello World![/color]")
        case("[color=#AFAFAF]Hello World![/color]")
        case("[color=#AFAFAFFF]Hello World![/color]")
        case("(=v=) Hello World! (-w=)")
        case("(bgm123) Hello World! (bgm2)")
        case("(bgm 2)")
        case("(bgm 2")
        case("Hello (bgm 2")
        case("Hello(=v=)")
    }.writeTo(dir)

    BBCodeTestGenerator("Spaces").apply {
        for (contextTag in contextTags) {
            case("[$contextTag]Hello World![/$contextTag]")
            case("[$contextTag][/$contextTag]")
            case("[$contextTag] [/$contextTag]")
            case("[$contextTag] /[][/]Hello [/$contextTag]")
        }
    }.writeTo(dir)

    BBCodeTestGenerator("NestedContext").apply {
        case("[b]Hello [i]World![/i][/b]")
        case("[url=https://example.com]Hello [b][i]World![/i][/b][/url]")
        case("[url=https://example.com]Hello [b][i]World![/i][/b] [b][i]Again![/i][/b][/url]")
        case("[quote]Hello [b][i]World![/i][/b][/quote]")
        case("[size=1][size=2]Hello World![/size][/size]")
        case("[size=1]Hello[size=2]World[/size]![/size]")
        case("[size=1]Hello[b][size=2]World[/size]![/b][/size]")
        case("[color=red][size=1]Hello[b][size=2]World[/size]![/b][/size][/color]")
    }.writeTo(dir)

    BBCodeTestGenerator("Specials").apply {
        for (contextTag in contextTags) {
            case("[$contextTag] /[][/]Hello [/$contextTag]")
        }
    }.writeTo(dir)

    dir.walk().filter { it.name.startsWith("Gen") && it.extension == "kt" }
        .forEach {
            it.writeText(
                """
                // @formatter:off
                @file:Suppress("RedundantVisibilityModifier")
                """.trimIndent() + "\n\n" + it.readText() + "\n\n// @formatter:on\n",
            )
        }
}


class BBCodeTestGenerator(
    name: String,
) {
    private val className = "GenBB${name}Test"

    private val classBuilder = TypeSpec.classBuilder(className)
        .superclass(ClassName.bestGuess("me.him188.ani.utils.bbcode.BBCodeParserTestHelper"))

    /**
     * 每行一个测试用例
     */
    fun casesForEachLine(@Language("bbcode") cases: String) {
        cases.lines().forEach { case(it) }
    }


    // Install IDE plugin "BBCode"
    fun case(@Language("bbcode") case: String) {
        val parsed = BBCode.parse(case)
        classBuilder.addFunction(
            FunSpec.builder("parse${case.hashCode().absoluteValue}")
                .addAnnotation(ClassName.bestGuess("kotlin.test.Test"))
                .addCode("BBCode.parse(%S)\n.run {\n", case)
                .apply {
                    generateAssertions(parsed.elements)
                }
                .addStatement("}")
                .build(),
        )
    }

    private fun FunSpec.Builder.generateAssertions(elements: List<RichElement>) {
        addCode("⇥")
        try {
            if (elements.isEmpty()) {
                addStatement("kotlin.test.assertEquals(0, elements.size)")
                return
            }
            elements.forEachIndexed { index, element ->
                when (element) {
                    is RichElement.BangumiSticker -> {
                        addCode("assertBangumiSticker(elements.at($index), id=%L", element.id)
                        element.jumpUrl?.let { addCode(", jumpUrl=%S", it) }
                        addStatement(")")
                    }

                    is RichElement.Image -> {
                        addCode("assertImage(elements.at($index), imageUrl=%S", element.imageUrl)
                        element.jumpUrl?.let { addCode(", jumpUrl=%S", it) }
                        addStatement(")")
                    }

                    is RichElement.Kanmoji -> {
                        addCode("assertKanmoji(elements.at($index), id=%S", element.id)
                        element.jumpUrl?.let { addCode(", jumpUrl=%S", it) }
                        addStatement(")")
                    }

                    is RichElement.Quote -> {
                        addCode("assertQuote(elements.at($index)")
                        element.jumpUrl?.let { addCode(", jumpUrl=%S", it) }
                        addStatement(") {")
                        generateAssertions(element.contents.elements)
                        addStatement("}")
                    }

                    is RichElement.Text -> {
                        addCode(
                            "assertText(elements.at($index), value=%S",
                            element.value,
                        )
                        element.jumpUrl?.let { addCode(", jumpUrl=%S", it) }
                        if (element.size != RichElement.Text.DEFAULT_SIZE) addCode(
                            ", size=%L",
                            element.size,
                        )
                        if (element.color != null) addCode(", color=%S", element.color)
                        if (element.italic) addCode(", italic=%L", true)
                        if (element.underline) addCode(", underline=%L", true)
                        if (element.strikethrough) addCode(", strikethrough=%L", true)
                        if (element.bold) addCode(", bold=%L", true)
                        if (element.mask) addCode(", mask=%L", true)
                        if (element.code) addCode(", code=%L", true)
                        addStatement(")")
                    }
                }
            }
        } finally {
            addCode("⇤")
        }
    }

    fun writeTo(dir: File) {
        FileSpec.builder("me.him188.ani.utils.bbcode", className)
            .addFileComment("Generated by ${BBCodeTestGenerator::class.qualifiedName}")
            .addType(classBuilder.build())
            .indent("    ")
            .build()
            .writeTo(dir)
    }
}