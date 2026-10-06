/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.danmaku

import com.generalk1ng.sokkuri.SokkuriConfig
import com.generalk1ng.sokkuri.Sokkuri
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
 * 根据 (来源文字, 目标文字) 选择具体的 sokkuri 转换配置.
 *
 * 返回 `null` 表示无需转换 (目标为 [DanmakuTextConversion.ORIGINAL] 或与来源文字相同).
 * 台湾/香港之间没有直接的转换链, 近似为先转换到通用繁体.
 */
internal fun resolveSokkuriConfig(
    source: DanmakuSourceScript,
    target: DanmakuTextConversion,
): SokkuriConfig? = when (source) {
    DanmakuSourceScript.SIMPLIFIED -> when (target) {
        DanmakuTextConversion.ORIGINAL,
        DanmakuTextConversion.SIMPLIFIED,
        -> null

        DanmakuTextConversion.TRADITIONAL -> SokkuriConfig.S2T
        DanmakuTextConversion.TAIWAN -> SokkuriConfig.S2TWP
        DanmakuTextConversion.HONG_KONG -> SokkuriConfig.S2HKP
    }

    DanmakuSourceScript.TRADITIONAL -> when (target) {
        DanmakuTextConversion.ORIGINAL,
        DanmakuTextConversion.TRADITIONAL,
        -> null

        DanmakuTextConversion.SIMPLIFIED -> SokkuriConfig.T2S
        DanmakuTextConversion.TAIWAN -> SokkuriConfig.T2TW
        DanmakuTextConversion.HONG_KONG -> SokkuriConfig.T2HK
    }

    DanmakuSourceScript.TAIWAN -> when (target) {
        DanmakuTextConversion.ORIGINAL,
        DanmakuTextConversion.TAIWAN,
        -> null

        DanmakuTextConversion.SIMPLIFIED -> SokkuriConfig.TW2SP
        // 没有 tw -> hk 的直接转换链, 近似为规范化到通用繁体
        DanmakuTextConversion.TRADITIONAL,
        DanmakuTextConversion.HONG_KONG,
        -> SokkuriConfig.TW2T
    }

    DanmakuSourceScript.HONG_KONG -> when (target) {
        DanmakuTextConversion.ORIGINAL,
        DanmakuTextConversion.HONG_KONG,
        -> null

        DanmakuTextConversion.SIMPLIFIED -> SokkuriConfig.HK2SP
        // 没有 hk -> tw 的直接转换链, 近似为规范化到通用繁体
        DanmakuTextConversion.TRADITIONAL,
        DanmakuTextConversion.TAIWAN,
        -> SokkuriConfig.HK2T
    }
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
     * 批量转换 [texts]. 无需转换时原样返回.
     */
    suspend fun convert(
        texts: List<String>,
        serviceId: DanmakuServiceId,
        target: DanmakuTextConversion,
    ): List<String> {
        if (texts.isEmpty()) return texts
        val config = resolveSokkuriConfig(serviceId.defaultSourceScript(), target) ?: return texts

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
        val texts = convert(list.map { it.text }, serviceId, target)
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
