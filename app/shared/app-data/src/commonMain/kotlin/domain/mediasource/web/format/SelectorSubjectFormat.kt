/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.web.format

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import me.him188.ani.app.domain.mediasource.web.WebSearchSubjectInfo
import me.him188.ani.utils.jsonpath.JsonPath
import me.him188.ani.utils.jsonpath.compileOrNull
import me.him188.ani.utils.jsonpath.resolveOrNull
import me.him188.ani.utils.xml.Element
import me.him188.ani.utils.xml.QueryParser
import me.him188.ani.utils.xml.parseSelectorOrNull
import org.intellij.lang.annotations.Language
import kotlin.contracts.contract

/**
 * 决定如何从搜索结果页列出条目. 结果顺序与页面一致; 自动匹配阶段的排序见 `SelectorAutoMatchConfig.preferShorterName`.
 */
sealed class SelectorSubjectFormat<in Config : SelectorFormatConfig>(override val id: SelectorFormatId) :
    SelectorFormat { // 方便改名

    /**
     * `null` means invalid config
     */
    abstract fun select(
        document: Element,
        baseUrl: String,
        config: Config,
    ): List<WebSearchSubjectInfo>?

    companion object {
        val entries by lazy { // 必须 lazy, 否则可能获取到 null
            @Suppress("RedundantRequireNotNullCall")
            listOf(
                checkNotNull(SelectorSubjectFormatA),
                checkNotNull(SelectorSubjectFormatIndexed),
                checkNotNull(SelectorSubjectFormatJsonPathIndexed),
            ) // checkNotNull is needed to be fail-fast
        }

        fun findById(id: SelectorFormatId): SelectorSubjectFormat<*>? {
            // reflection is not supported in Kotlin/Native
            return entries.find { it.id == id }
        }
    }
}

/**
 * Select 出一些 `<a>`, text 作为 name, `href` 作为 url
 */
data object SelectorSubjectFormatA : SelectorSubjectFormat<SelectorSubjectFormatA.Config>(SelectorFormatId("a")) {
    @Immutable
    @Serializable
    data class Config(
        @param:Language("css")
        val selectLists: String = "div.video-info-header > a",
        /**
         * 详情页链接模板. 把 `href` 代入 `{value}` 得到详情页地址. 留空则直接用 `href`.
         */
        val linkTemplate: String? = null,
        /**
         * 旧格式字段, 已移到 `SelectorAutoMatchConfig.preferShorterName`, 只为读写旧 JSON 保留. 解析时不使用.
         */
        @Deprecated("moved to SelectorAutoMatchConfig.preferShorterName")
        val preferShorterName: Boolean = true,
    ) : SelectorFormatConfig {
        override fun isValid(): Boolean {
            return selectLists.isNotBlank()
        }
    }

    override fun select(
        document: Element,
        baseUrl: String,
        config: Config,
    ): List<WebSearchSubjectInfo>? {
        val selectLists = QueryParser.parseSelectorOrNull(config.selectLists) ?: return null
        val elements = document.select(selectLists)
        return elements.mapTo(ArrayList(elements.size)) { a ->
            val name = a.attr("title").takeIf { it.isNotBlank() } ?: a.text()
            val href = a.attr("href")
            buildSubject(name, href, baseUrl, config.linkTemplate, origin = a)
        }
    }
}


/**
 * 一个语句 select 出所有的名字, 然后一个语句 select 所有的按钮 `<a>`, 按顺序对应
 */
data object SelectorSubjectFormatIndexed :
    SelectorSubjectFormat<SelectorSubjectFormatIndexed.Config>(SelectorFormatId("indexed")) {
    @Immutable
    @Serializable
    data class Config(
        @param:Language("css")
        val selectNames: String = ".search-box .thumb-content > .thumb-txt",
        @param:Language("css")
        val selectLinks: String = ".search-box .thumb-menu > a",
        /**
         * 详情页链接模板. 把 [selectLinks] 抽出的原始链接代入 `{value}` 得到详情页地址. 留空则直接用原始链接.
         */
        val linkTemplate: String? = null,
        /**
         * 旧格式字段, 已移到 `SelectorAutoMatchConfig.preferShorterName`, 只为读写旧 JSON 保留. 解析时不使用.
         */
        @Deprecated("moved to SelectorAutoMatchConfig.preferShorterName")
        val preferShorterName: Boolean = true,
    ) : SelectorFormatConfig {
        override fun isValid(): Boolean {
            return selectNames.isNotBlank()
        }
    }

    override fun select(
        document: Element,
        baseUrl: String,
        config: Config,
    ): List<WebSearchSubjectInfo>? {
        val selectNames = QueryParser.parseSelectorOrNull(config.selectNames) ?: return null
        val selectLinks = QueryParser.parseSelectorOrNull(config.selectLinks) ?: return null


        val names: List<String> = document.select(selectNames).mapNotNull { a ->
            a.text().takeIf { it.isNotBlank() }
        }

        val links = document.select(selectLinks).mapNotNull { a ->
            val href = a.attr("href")
            href.takeIf { it.isNotBlank() }
        }

        return names.fastZipNotNullToMutable(links) { name, href ->
            buildSubject(name, href, baseUrl, config.linkTemplate, origin = null)
        }
    }
}

data object SelectorSubjectFormatJsonPathIndexed :
    SelectorSubjectFormat<SelectorSubjectFormatJsonPathIndexed.Config>(SelectorFormatId("json-path-indexed")) {

    @Serializable
    data class Config(
        @param:Language("jsonpath") // install IDE plugin "jsonpath"
        val selectLinks: String = "$[*]['url', 'link']",
        @param:Language("jsonpath")
        val selectNames: String = "$[*]['title','name']",
        /**
         * 详情页链接模板. API 只返回裸 id 时(如 `{"list":[{"id":"5395"}]}`), 用模板把 id 拼成详情页地址
         * (如 `/GV{value}/`), 其中 `{value}` 为 [selectLinks] 抽出的值. 留空则直接用抽出值作为链接.
         */
        val linkTemplate: String? = null,
        /**
         * 旧格式字段, 已移到 `SelectorAutoMatchConfig.preferShorterName`, 只为读写旧 JSON 保留. 解析时不使用.
         */
        @Deprecated("moved to SelectorAutoMatchConfig.preferShorterName")
        val preferShorterName: Boolean = true,
    ) : SelectorFormatConfig {
        override fun isValid(): Boolean {
            return selectLinks.isNotBlank() && selectNames.isNotBlank()
        }
    }

    override fun select(document: Element, baseUrl: String, config: Config): List<WebSearchSubjectInfo>? {
        val selectUrls = JsonPath.compileOrNull(config.selectLinks) ?: return null
        val selectNames = JsonPath.compileOrNull(config.selectNames) ?: return null
        val json = try {
            Json.parseToJsonElement(document.text())
        } catch (e: Exception) {
            return emptyList()
        }

        try {
            val urls = json.resolveOrNull(selectUrls)?.values?.mapNotNull { e ->
                e.getSingleStringValueOrNull()?.takeIf { it.isNotBlank() }
            }?.toList() ?: return emptyList()

            val names = json.resolveOrNull(selectNames)?.values?.mapNotNull { e ->
                e.getSingleStringValueOrNull()?.takeIf { it.isNotBlank() }
            }?.toList() ?: return emptyList()

            return names.fastZipNotNullToMutable(urls) { name, href ->
                buildSubject(name, href, baseUrl, config.linkTemplate, origin = null)
            }
        } catch (e: Exception) {
            return null
        }
    }
}

// Supports:
// - `$[*]['title', 'name']` which returns array of objects,
// - `$[*].title` which returns array of strings
private val JsonElement.values: Sequence<JsonElement>
    get() = when (this) {
        is JsonArray -> asSequence()
        is JsonObject -> values.asSequence()
        is JsonPrimitive -> sequenceOf(this)
    }

private fun JsonElement.getSingleStringValueOrNull(): String? {
    return when (this) {
        is JsonArray -> firstOrNull()?.getSingleStringValueOrNull()
        is JsonObject -> values.firstOrNull()?.getSingleStringValueOrNull()
        is JsonPrimitive -> content
    }
}

/**
 * 把 select 出的原始链接 [rawHref] 代入 [linkTemplate] 得到详情页链接.
 * 模板中的 `{value}` 会被替换为 [rawHref]; 未配置模板 (`null` 或空) 时返回 [rawHref] 本身.
 * 模板可以是相对路径(以 baseUrl 解析为绝对地址)或绝对地址.
 */
private fun applyLinkTemplate(linkTemplate: String?, rawHref: String): String {
    if (linkTemplate.isNullOrBlank()) return rawHref
    return linkTemplate.replace("{value}", rawHref)
}

/**
 * 由 select 出的 [name] 与原始链接 [rawHref] 构造一个条目. 详情页链接取 [applyLinkTemplate] 的结果
 * (相对路径会以 [baseUrl] 解析为绝对地址), 未配置模板时直接用 [rawHref].
 */
private fun buildSubject(
    name: String,
    rawHref: String,
    baseUrl: String,
    linkTemplate: String?,
    origin: Element?,
): WebSearchSubjectInfo {
    val href = applyLinkTemplate(linkTemplate, rawHref)
    return WebSearchSubjectInfo(
        internalId = guessIdFromUrl(href),
        name = name,
        fullUrl = SelectorHelpers.computeAbsoluteUrl(baseUrl, href),
        partialUrl = href,
        origin = origin,
    )
}

private fun guessIdFromUrl(href: String) =
    href.removeSuffix("/").substringBeforeLast(".html").substringAfterLast("/")

private inline fun <T, R, V : Any> List<T>.fastZipNotNullToMutable(
    other: List<R>,
    transform: (a: T, b: R) -> V?
): MutableList<V> {
    contract { callsInPlace(transform) }
    val minSize = minOf(size, other.size)
    val target = ArrayList<V>(minSize)
    for (i in 0 until minSize) {
        val res = transform(get(i), other[i])
        if (res != null) {
            target += res
        }
    }
    return target
}
