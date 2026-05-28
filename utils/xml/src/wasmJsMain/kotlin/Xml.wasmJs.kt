package me.him188.ani.utils.xml

import kotlinx.io.Source
import kotlinx.io.readString

private class SimpleDocument(private val content: String) : Document() {
    override fun text(): String = content
}

actual object Xml {
    actual fun parse(string: String): Document = SimpleDocument(string)
    actual fun parse(string: String, baseUrl: String): Document = SimpleDocument(string)
    actual fun parse(source: Source): Document = SimpleDocument(source.readString())
    actual fun parse(source: Source, baseUrl: String): Document = SimpleDocument(source.readString())
}

actual object Html {
    actual fun parse(string: String): Document = SimpleDocument(string)
    actual fun parse(string: String, baseUrl: String): Document = SimpleDocument(string)
    actual fun parse(source: Source): Document = SimpleDocument(source.readString())
    actual fun parse(source: Source, baseUrl: String): Document = SimpleDocument(source.readString())
}

private object EmptyEvaluator : Evaluator()

actual object QueryParser {
    actual fun parseSelector(selector: String): Evaluator = EmptyEvaluator
}
