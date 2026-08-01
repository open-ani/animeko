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
 * 加载弹幕时做的一次性预处理配置. 只在整集弹幕列表变化时生效, 播放过程中不会再重新计算.
 */
data class DanmakuPreprocessConfig(
    /**
     * 是否合并内容重复的弹幕. 见 [DanmakuMerger].
     */
    val enableMerge: Boolean = false,
    /**
     * 合并重复弹幕的时间窗口.
     */
    val mergeWindowMillis: Long = DanmakuMerger.DEFAULT_WINDOW_MILLIS,
) {
    companion object {
        val Default = DanmakuPreprocessConfig()
    }
}

/**
 * 对整集弹幕列表做的预处理. 纯函数, 方便测试.
 */
object DanmakuPreprocessor {
    /**
     * @param list 必须已按 [DanmakuInfo.playTimeMillis] 升序排序.
     */
    fun preprocess(
        list: List<DanmakuInfo>,
        config: DanmakuPreprocessConfig,
    ): List<DanmakuInfo> {
        var result = list
        if (config.enableMerge) {
            result = DanmakuMerger.merge(result, config.mergeWindowMillis)
        }
        return result
    }
}
