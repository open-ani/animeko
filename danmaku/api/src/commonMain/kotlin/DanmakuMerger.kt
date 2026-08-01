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
 * 合并内容重复的弹幕. 思路参考 [pakku.js](https://github.com/xmcp/pakku.js), 但做了简化:
 * 只按"归一化之后完全相同"来判定重复, 不做编辑距离/拼音相似度.
 *
 * 合并后只保留一簇中**第一条**弹幕 (保留它的时间/颜色/位置), 并在显示文本后面追加 `×N` 计数;
 * 其余弹幕直接丢弃.
 */
object DanmakuMerger {
    /**
     * 默认的合并时间窗口. 和 pakku 的默认阈值一致.
     */
    const val DEFAULT_WINDOW_MILLIS: Long = 40_000L

    /**
     * @param list 必须已按 [DanmakuInfo.playTimeMillis] 升序排序.
     * @param windowMillis 时间窗口. 一条弹幕只有在与所属簇的**代表弹幕**相距不超过该窗口时才会被合并进去;
     * 超出窗口就会另起一簇. 用代表弹幕 (而不是簇里最后一条) 做锚点, 是为了让"整集刷屏"的弹幕每隔一个窗口
     * 仍会出现一次, 而不是全部塌缩到第一条上.
     *
     * @return 合并后的列表, 顺序仍然按时间升序. 如果没有任何一簇达到 2 条, 直接返回 [list] 本身.
     */
    fun merge(
        list: List<DanmakuInfo>,
        windowMillis: Long = DEFAULT_WINDOW_MILLIS,
    ): List<DanmakuInfo> {
        if (list.size < 2) return list

        val representatives = ArrayList<DanmakuInfo>(list.size)
        val counts = ArrayList<Int>(list.size)
        // 归一化文本 -> 当前所属簇在 representatives 里的下标
        val clusters = HashMap<String, Int>()
        var merged = false

        for (danmaku in list) {
            val normalized = DanmakuTextNormalizer.normalize(danmaku.text)
            if (normalized.isEmpty()) {
                // 无法比较, 当作独一无二
                representatives.add(danmaku)
                counts.add(1)
                continue
            }

            val index = clusters[normalized]
            if (index != null &&
                danmaku.playTimeMillis - representatives[index].playTimeMillis <= windowMillis
            ) {
                counts[index]++
                merged = true
            } else {
                clusters[normalized] = representatives.size
                representatives.add(danmaku)
                counts.add(1)
            }
        }

        if (!merged) return list

        return representatives.mapIndexed { index, danmaku ->
            val count = counts[index]
            if (count < 2) {
                danmaku
            } else {
                danmaku.copy(
                    content = danmaku.content.copy(text = "${danmaku.text} ×$count"),
                )
            }
        }
    }
}
