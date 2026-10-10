/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.danmaku

import com.generalk1ng.sokkuri.Sokkuri
import com.generalk1ng.sokkuri.SokkuriConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.him188.ani.danmaku.api.DanmakuInfo
import me.him188.ani.danmaku.api.DanmakuServiceId
import me.him188.ani.danmaku.ui.DanmakuTextConversion
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.logger

/**
 * 根据目标文字选择 sokkuri 转换配置, 返回 `null` 表示无需转换.
 *
 * 只由目标文字决定, 不对弹幕来源的文字体系做任何假设: 同一个来源里可能混着简体、繁体、
 * 台繁、港繁 (例如弹弹play 的分类桶里就混着多个站点的弹幕), 按来源猜文字一旦猜错, 结果就会错。
 *
 * 这些链都不要求输入文字正确 —— 每条链的终点都是目标文字 (转简体的 [SokkuriConfig.TW2SP]
 * 以"传统字 -> 简体"收尾, 转台繁的 [SokkuriConfig.S2TWP] 以"简体 -> 传统"开头),
 * 输入若是别的文字, 顶多是不发生转换, 不会把结果带到目标之外.
 * 见 `DanmakuTextConverterTest.result is always in the target script`.
 *
 * 带用词转换的链对少数已是简体的词也会改写 (文件夹 -> 文档夹, 程序 -> 进程, OpenCC
 * 词表如此). 换成逐字转换 ([SokkuriConfig.T2S]) 就没有这个问题, 但 滑鼠 / 資料夾 这类
 * 台湾用词也不会被转换, 对弹幕来说得不偿失, 因此保留用词转换.
 */
internal fun resolveSokkuriConfig(target: DanmakuTextConversion): SokkuriConfig? = when (target) {
    DanmakuTextConversion.ORIGINAL -> null
    DanmakuTextConversion.SIMPLIFIED -> SokkuriConfig.TW2SP
    DanmakuTextConversion.TRADITIONAL -> SokkuriConfig.S2T
    DanmakuTextConversion.TAIWAN -> SokkuriConfig.S2TWP
    DanmakuTextConversion.HONG_KONG -> SokkuriConfig.S2HKP
}

/**
 * 弹幕文本简繁转换器, 基于 sokkuri (OpenCC 的 KMP 移植).
 *
 * - [Sokkuri] 实例按转换配置懒创建并常驻缓存; 同一实例线程安全, 可并发转换.
 * - 每条文本的转换结果按配置分别记忆, 切换目标文字后再切回来是命中缓存的纯查询,
 *   播放中切换不会有明显的重转换耗时.
 * - 任何转换失败 (例如词典资源缺失) 都会降级为返回原始文本, 绝不影响弹幕的正常显示.
 *
 * 实例由 [me.him188.ani.app.domain.episode.EpisodeDanmakuLoader] 持有,
 * 缓存随播放会话销毁, 不会无限增长.
 */
class DanmakuTextConverter {
    private class ConverterHolder(
        val sokkuri: Sokkuri,
    ) {
        val memo: HashMap<String, String> = HashMap()
    }

    private val mutex = Mutex()
    private val holders = HashMap<SokkuriConfig, ConverterHolder>()
    private val brokenConfigs = HashSet<SokkuriConfig>()

    /**
     * 批量转换 [texts] 到 [target]. 无需转换时原样返回 (同一个 list 实例).
     *
     * 与来源无关: 同样的文本在任何来源下都会得到同样的结果.
     */
    suspend fun convert(
        texts: List<String>,
        target: DanmakuTextConversion,
    ): List<String> {
        if (texts.isEmpty()) return texts
        val config = resolveSokkuriConfig(target) ?: return texts

        return withContext(Dispatchers.Default) {
            mutex.withLock {
                val holder = holderFor(config)
                if (holder == null) {
                    texts
                } else {
                    try {
                        texts.map { text ->
                            if (text.isEmpty()) text
                            else holder.memo.getOrPut(text) { holder.sokkuri.convert(text) }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // 转换中途失败: 禁用此配置并降级为原始文本, 保证弹幕可用
                        holders.remove(config)
                        brokenConfigs.add(config)
                        logger.error(e) { "Danmaku text conversion failed for $config, disabled" }
                        texts
                    }
                }
            }
        }
    }

    /**
     * 按 [textConversion] 批量转换一批弹幕的文本, 仅在需要转换时才会实际转换.
     *
     * 保留 [DanmakuInfo] 的其他字段, 只替换文本. 无需转换时原样返回 (同一个 list 实例).
     */
    suspend fun convertDanmakuInfos(
        list: List<DanmakuInfo>,
        serviceId: DanmakuServiceId,
        textConversion: DanmakuTextConversionSettings,
    ): List<DanmakuInfo> {
        if (list.isEmpty()) return list
        val target = textConversion.targetFor(serviceId)
        if (target == DanmakuTextConversion.ORIGINAL) return list
        val texts = convert(list.map { it.text }, target)
        return list.mapIndexed { index, danmaku ->
            danmaku.copy(content = danmaku.content.copy(text = texts[index]))
        }
    }

    private fun holderFor(config: SokkuriConfig): ConverterHolder? {
        holders[config]?.let { return it }
        if (config in brokenConfigs) return null
        return try {
            ConverterHolder(Sokkuri.create(config)).also { holders[config] = it }
        } catch (e: Exception) {
            brokenConfigs.add(config)
            logger.error(e) { "Failed to create Sokkuri converter for $config, danmaku text conversion disabled for this config" }
            null
        }
    }

    private companion object {
        private val logger = logger<DanmakuTextConverter>()
    }
}
