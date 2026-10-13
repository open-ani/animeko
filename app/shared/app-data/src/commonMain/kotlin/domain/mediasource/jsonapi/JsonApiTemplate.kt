/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.jsonapi

import io.ktor.http.encodeURLParameter

/**
 * JSON API 数据源配置里的 `{var}` 模板.
 *
 * 模板用于三处: 请求地址、请求体、以及条目和剧集的页面地址. 同一套语法, 代入时按所在位置转义 ([Escaping]),
 * 用户在模板里不需要也不应该自己转义.
 *
 * 页面地址模板还要能反过来从地址还原变量 ([extract]): 浏览协议只把条目地址 (`BrowseSubject.url`) 交回数据源,
 * 数据源要从中拿回站点 id 才能发起下一步请求. 反解按字面文本分隔变量, 因此模板里相邻的两个变量之间必须有字面文本.
 */
object JsonApiTemplate {
    private val VARIABLE = Regex("""\{([A-Za-z_][A-Za-z0-9_]*)\}""")

    /**
     * 代入值的转义方式, 由模板所在位置决定.
     */
    enum class Escaping {
        /** 原样代入. 用于页面地址等不需要转义的位置. */
        NONE,

        /** 作为 URL 的一个片段 (路径段或查询参数值) 百分号编码. */
        URL_COMPONENT,

        /** 作为 JSON 字符串的内容转义 (不含两侧引号). 数字等不含特殊字符的值代入后不变, 所以同样适用于 `"page": {page}` 这种数字位置. */
        JSON_STRING,
    }

    /**
     * 代入 [variables]. [template] 里没有定义的变量原样保留, 以便在测试面板里看出配置缺了什么.
     */
    fun substitute(template: String, variables: Map<String, String>, escaping: Escaping): String {
        if (!template.contains('{')) return template
        return VARIABLE.replace(template) { match ->
            val value = variables[match.groupValues[1]] ?: return@replace match.value
            when (escaping) {
                Escaping.NONE -> value
                Escaping.URL_COMPONENT -> value.encodeURLParameter()
                Escaping.JSON_STRING -> escapeJsonString(value)
            }
        }
    }

    /**
     * 模板里出现的变量名, 按出现顺序, 去重.
     */
    fun variableNames(template: String): List<String> =
        VARIABLE.findAll(template).map { it.groupValues[1] }.distinct().toList()

    /**
     * 模板里引用了但 [known] 没有的变量名, 按出现顺序. 编辑器用它标出拼错的变量; 代入时未知变量原样保留.
     */
    fun unknownVariables(template: String, known: Set<String>): List<String> =
        variableNames(template).filter { it !in known }

    /**
     * 从按 [template] 生成的 [text] 还原变量.
     *
     * 字面部分必须逐字相同; 每个变量匹配至少一个字符, 前面的变量尽量短 (`{a}-{b}` 对 `1-2-3` 得到 `a=1`, `b=2-3`).
     * 同一个变量出现多次时各处的值必须相同. 模板没有变量时, [text] 必须等于 [template].
     *
     * @return 不匹配时为 `null`.
     */
    fun extract(template: String, text: String): Map<String, String>? {
        val names = ArrayList<String>()
        val pattern = buildString {
            append('^')
            var last = 0
            for (match in VARIABLE.findAll(template)) {
                append(Regex.escape(template.substring(last, match.range.first)))
                append("([\\s\\S]+?)") // 不用 DOT_MATCHES_ALL: Kotlin/Native 没有这个选项
                names += match.groupValues[1]
                last = match.range.last + 1
            }
            append(Regex.escape(template.substring(last)))
            append('$')
        }
        if (names.isEmpty()) return if (text == template) emptyMap() else null
        val match = Regex(pattern).matchEntire(text) ?: return null
        val result = LinkedHashMap<String, String>(names.size)
        for ((index, name) in names.withIndex()) {
            val value = match.groupValues[index + 1]
            val existing = result[name]
            if (existing != null && existing != value) return null
            result[name] = value
        }
        return result
    }

    private fun escapeJsonString(value: String): String {
        if (value.none { it == '"' || it == '\\' || it < ' ' }) return value
        return buildString(value.length + 8) {
            for (c in value) {
                when (c) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    '\b' -> append("\\b")
                    '\u000C' -> append("\\f")
                    else -> if (c < ' ') {
                        append("\\u")
                        append(c.code.toString(16).padStart(4, '0'))
                    } else {
                        append(c)
                    }
                }
            }
        }
    }
}
