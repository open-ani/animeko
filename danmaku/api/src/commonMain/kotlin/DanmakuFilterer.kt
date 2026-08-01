/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.danmaku.api

/**
 * 按 [DanmakuFilterSpec] 过滤弹幕.
 *
 * 有状态: 缓存已编译的 [Regex] 和已归一化的关键词, 避免每次弹幕列表变化都重新编译一遍正则.
 * 不是线程安全的, 每个会话用自己的一个实例 (在同一个协程里串行使用).
 */
class DanmakuFilterer {
    private val regexCache = HashMap<Pair<ZhConversion, String>, Regex>()

    private var preparedSpec: DanmakuFilterSpec? = null
    private var regexes: List<Regex> = emptyList()
    private var normalizedKeywords: List<String> = emptyList()

    /**
     * 实际编译 [Regex] 的次数. 仅用于测试缓存是否生效.
     */
    internal var compileCount: Int = 0
        private set

    fun filter(list: List<DanmakuInfo>, spec: DanmakuFilterSpec): List<DanmakuInfo> {
        if (spec.isEmpty) return list
        prepare(spec)

        val regexes = this.regexes
        val keywords = this.normalizedKeywords
        return list.filter { danmaku ->
            // 所有过滤器都没有匹配到, 才算通过
            if (regexes.any { it.find(danmaku.text) != null }) {
                return@filter false
            }
            if (keywords.isNotEmpty()) {
                val normalized = DanmakuTextNormalizer.normalize(danmaku.text)
                if (keywords.any { normalized.contains(it) }) {
                    return@filter false
                }
            }
            true
        }
    }

    private fun prepare(spec: DanmakuFilterSpec) {
        if (preparedSpec == spec) return
        preparedSpec = spec

        if (regexCache.size > MAX_CACHE_SIZE) {
            regexCache.clear()
        }
        regexes = spec.regexPatterns.map { compile(it, spec.zhConversion) }
        // 关键词按归一化之后的形式做子串匹配, 所以要和弹幕文本用同一套归一化规则
        normalizedKeywords = spec.keywords
            .map { DanmakuTextNormalizer.normalize(ZhConverter.convert(it, spec.zhConversion)) }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    /**
     * 弹幕文本在加载时已经被转换成目标字形, 所以正则也要转换成同一种字形.
     * 转换表只含 CJK 字符, 不会影响正则的元字符.
     */
    private fun compile(pattern: String, conversion: ZhConversion): Regex {
        val key = conversion to pattern
        regexCache[key]?.let { return it }

        val regex = Regex(ZhConverter.convert(pattern, conversion), option = RegexOption.IGNORE_CASE)
        compileCount++
        regexCache[key] = regex
        return regex
    }

    private companion object {
        private const val MAX_CACHE_SIZE = 256
    }
}
