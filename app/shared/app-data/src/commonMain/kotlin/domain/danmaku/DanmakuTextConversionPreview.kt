/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.danmaku

import me.him188.ani.danmaku.api.DanmakuServiceId
import me.him188.ani.danmaku.ui.DanmakuTextConversion

/**
 * 设置界面的示例文字: 展示"这个来源的弹幕, 在这条转换下会变成什么样".
 *
 * 繁体 / 台繁 / 港繁 对多数用户难以区分, 给出真实转换结果比文字描述更直观,
 * 因此示例不写死, 而是用 [DanmakuTextConverter] 实际转换一条与来源文字相符的样例.
 *
 * 样例选取:
 * - "波奇文件夹里的歌" 在四个目标文字下结果两两不同:
 *   繁 波奇文件夾裏的歌 / 台 波奇資料夾裡的歌 / 港 波奇資料夾裏的歌.
 *   它同时含两处差异 —— 「文件夹」台港都叫「資料夾」(用词差异, 异于一般繁体),
 *   「里」台写作「裡」而港写作「裏」(字形差异). 缺任一处都凑不齐四种结果,
 *   例如只含「里」或只含「网络」的句子, 港繁都会与繁体相同.
 * - 输入文字与来源文字一致, 避免"用简体样例展示繁体来源"造成的假象.
 *
 * 与播放共用同一套 sokkuri 实例缓存, 首次调用才加载词典 (约 65ms), 之后是纯查询.
 */
object DanmakuTextConversionPreview {
    private const val SAMPLE_SIMPLIFIED = "波奇文件夹里的歌"
    private const val SAMPLE_TRADITIONAL = "波奇文件夾裏的歌"
    private const val SAMPLE_TAIWAN = "波奇資料夾裡的歌"
    private const val SAMPLE_HONG_KONG = "波奇資料夾裏的歌"

    private val converter = DanmakuTextConverter()

    /**
     * 该来源的一条示例弹幕在 [target] 下显示成什么.
     *
     * 转换失败 (例如词典缺失) 时返回样例原文, 不会抛异常.
     */
    suspend fun sample(serviceId: DanmakuServiceId, target: DanmakuTextConversion): String =
        converter.convert(listOf(sourceSample(serviceId)), serviceId, target).first()

    /**
     * [sample] 的输入: 与来源文字相符的示例弹幕原文.
     */
    fun sourceSample(serviceId: DanmakuServiceId): String =
        when (serviceId.defaultSourceScript()) {
            DanmakuSourceScript.SIMPLIFIED -> SAMPLE_SIMPLIFIED
            DanmakuSourceScript.TRADITIONAL -> SAMPLE_TRADITIONAL
            DanmakuSourceScript.TAIWAN -> SAMPLE_TAIWAN
            DanmakuSourceScript.HONG_KONG -> SAMPLE_HONG_KONG
        }
}
