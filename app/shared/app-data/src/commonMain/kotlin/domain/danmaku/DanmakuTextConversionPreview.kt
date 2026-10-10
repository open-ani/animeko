/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.danmaku

import me.him188.ani.app.domain.danmaku.DanmakuTextConversionPreview.sample
import me.him188.ani.danmaku.ui.DanmakuTextConversion

/**
 * 设置界面的一条示例: 原文与它在某个目标文字下的结果.
 */
data class DanmakuTextConversionSample(
    val sourceText: String,
    val convertedText: String,
) {
    /** 转换是否真的改变了文字. */
    val isChanged: Boolean get() = sourceText != convertedText
}

/**
 * 设置界面的示例文字: 展示"选了某个目标文字之后, 弹幕会变成什么样".
 *
 * 繁体 / 台繁 / 港繁 对多数用户难以区分, 给出真实转换结果比文字描述更直观,
 * 因此示例不写死, 而是用 [DanmakuTextConverter] 实际转换一条样例.
 *
 * 示例与弹幕来源无关: 转换结果只由目标文字决定, 所以这里只按 [target] 挑一条能看出差别的样例文字
 * (目标是简体时用台繁样例), 它不代表任何来源的真实文字.
 *
 * 样例选取:
 * - "波奇文件夹里的歌" 在四个目标文字下结果两两不同:
 *   繁 波奇文件夾裏的歌 / 台 波奇資料夾裡的歌 / 港 波奇資料夾裏的歌.
 *   它同时含两处差异 —— 「文件夹」台港都叫「資料夾」(用词差异, 异于一般繁体),
 *   「里」台写作「裡」而港写作「裏」(字形差异). 缺任一处都凑不齐四种结果,
 *   例如只含「里」或只含「网络」的句子, 港繁都会与繁体相同.
 *
 * 与播放共用同一套 sokkuri 实例缓存, 首次调用才加载词典 (约 65ms), 之后是纯查询.
 */
object DanmakuTextConversionPreview {
    private const val SAMPLE_SIMPLIFIED = "波奇文件夹里的歌"
    private const val SAMPLE_TAIWAN = "波奇資料夾裡的歌"

    private val converter = DanmakuTextConverter()

    /**
     * [target] 的一条示例. 转换失败 (例如词典缺失) 时结果是原文, 不会抛异常.
     */
    suspend fun sample(target: DanmakuTextConversion): DanmakuTextConversionSample {
        val sourceText = sourceSample(target)
        return DanmakuTextConversionSample(
            sourceText = sourceText,
            convertedText = converter.convert(listOf(sourceText), target).first(),
        )
    }

    /**
     * [sample] 的输入: 挑一条在 [target] 下能看出转换效果的样例文字.
     */
    fun sourceSample(target: DanmakuTextConversion): String = when (target) {
        // 简体是转换链的终点, 用台繁样例才能看出与原文的差别
        DanmakuTextConversion.ORIGINAL,
        DanmakuTextConversion.SIMPLIFIED,
            -> SAMPLE_TAIWAN

        DanmakuTextConversion.TRADITIONAL,
        DanmakuTextConversion.TAIWAN,
        DanmakuTextConversion.HONG_KONG,
            -> SAMPLE_SIMPLIFIED
    }
}
