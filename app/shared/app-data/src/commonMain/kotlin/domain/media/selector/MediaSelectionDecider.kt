/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.selector

import androidx.compose.ui.util.fastFirstOrNull
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.data.models.preference.MediaPreference.Companion.ANY_FILTER
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.topic.hasSeason

/** Pure preference matching shared by [MediaSelector] operations and [MediaAutoSelector]. */
internal object MediaSelectionDecider {
    fun findByPreference(
        candidates: List<MaybeExcludedMedia.Included>,
        mergedPreference: MediaPreference,
        availableAlliances: List<String>,
        mediaSelectorContext: MediaSelectorContext,
        mediaSelectorSettings: MediaSelectorSettings,
    ): Media? {
        val selectedSubtitleLanguageId = mergedPreference.subtitleLanguageId
        val selectedResolution = mergedPreference.resolution
        val selectedAlliance = mergedPreference.alliance
        val selectedMediaSource = mergedPreference.mediaSourceId
        val allianceRegexes = mergedPreference.alliancePatterns.orEmpty().map { it.toRegex() }
        val shouldPreferSeasons = mediaSelectorContext.subjectFinished == true
                && mediaSelectorSettings.preferSeasons

        val preferKind = mediaSelectorSettings.preferKind

        val languageIds = sequence {
            selectedSubtitleLanguageId?.let {
                yield(it)
                return@sequence
            }
            yieldAll(mergedPreference.fallbackSubtitleLanguageIds.orEmpty())
        }
        val resolutions = sequence {
            selectedResolution?.let {
                yield(it)
                return@sequence
            }
            yieldAll(mergedPreference.fallbackResolutions.orEmpty())
        }
        val alliances = sequence {
            selectedAlliance?.let {
                yield(it)
                return@sequence
            }
            if (allianceRegexes.isEmpty()) {
                yield(ANY_FILTER)
            } else {
                for (regex in allianceRegexes) {
                    for (alliance in availableAlliances) {
                        // lazy 匹配, 但没有 cache, 若 `alliances` 反复访问则会进行多次匹配
                        if (regex.find(alliance) != null) yield(alliance)
                    }
                }
            }
        }
        val mediaSources = sequence {
            selectedMediaSource?.let {
                yield(it)
                return@sequence
            }
            val fallback = mediaSelectorContext.mediaSourcePrecedence
            if (fallback != null) {
                yieldAll(fallback) // 如果有设置, 那就优先使用设置的
            }
            yield(null) // 最后 (未匹配到时) 总是任意选一个
        }

        // For rules discussion, see #174

        // 选择顺序
        // 1. 分辨率
        // 2. 字幕语言
        // 3. 字幕组
        // 4. 数据源

        // 规则:
        // - 分辨率最高优先: 1080P >> 720P, 但不能为了要 4K 而选择不想要的字幕语言
        // - 不要为了选择偏好字幕组而放弃其他字幕组的更好的语言

        // 实际上这些 loop 都只需要跑一次, 除了分辨率. 而这也只需要多遍历两次 list 而已.
        // 例如: 4K (无匹配) -> 2K (无匹配) -> 1080P -> 简中 -> 桜都 -> Mikan

        fun selectAny(list: List<Media>): Media? {
            if (list.isEmpty()) {
                return null
            }
            if (shouldPreferSeasons) {
                return list.fastFirstOrNull { it.episodeRange?.hasSeason() == null }
                    ?: list.first()
            }
            return list.first()
        }

        fun selectAny(candidates: List<MaybeExcludedMedia.Included>) =
            selectAny(candidates.map { it.result })

        // TODO: too complex, should refactor

        fun selectImpl(candidates: List<Media>): Media? {
            for (resolution in resolutions) { // DFS 尽可能匹配第一个分辨率
                val filteredByResolution =
                    if (resolution == ANY_FILTER) candidates
                    else candidates.filter { resolution == it.properties.resolution }
                if (filteredByResolution.isEmpty()) continue

                for (languageId in languageIds) {
                    val filteredByLanguage =
                        if (languageId == ANY_FILTER) filteredByResolution
                        else filteredByResolution.filter { languageId in it.properties.subtitleLanguageIds }
                    if (filteredByLanguage.isEmpty()) continue

                    for (alliance in alliances) { // 能匹配第一个最好
                        // 这里是消耗最大的地方, 因为有正则匹配
                        val filteredByAlliance =
                            if (alliance == ANY_FILTER) filteredByLanguage
                            else filteredByLanguage.filter { alliance == it.properties.alliance }
                        if (filteredByAlliance.isEmpty()) continue

                        for (mediaSource in mediaSources) {
                            val filteredByMediaSource =
                                if (mediaSource == ANY_FILTER) filteredByAlliance
                                else filteredByAlliance.filter {
                                    mediaSource == null || mediaSource == it.mediaSourceId
                                }
                            if (filteredByMediaSource.isEmpty()) continue
                            return selectAny(filteredByMediaSource)
                        }
                    }

                    // 字幕组没匹配到, 但最好不要换更差语言

                    for (mediaSource in mediaSources) {
                        val filteredByMediaSource =
                            if (mediaSource == ANY_FILTER) filteredByLanguage
                            else filteredByLanguage.filter {
                                mediaSource == null || mediaSource == it.mediaSourceId
                            }
                        if (filteredByMediaSource.isEmpty()) continue
                        return selectAny(filteredByMediaSource)
                    }
                }

                // 该分辨率下无字幕语言, 换下一个分辨率
            }
            return null
        }

        fun selectImpl(maybeExcludedMedia: List<MaybeExcludedMedia.Included>) =
            selectImpl(maybeExcludedMedia.map { it.result })

        if (preferKind != null) {
            val preferred = candidates.filter { it.result.kind == preferKind }
            if (preferKind == MediaSourceKind.WEB) {
                // 如果用户倾向于 WEB, 优先从相似度足够高的项目中选择.
                //  否则会导致快速选择数据源时选择了高优先数据源中的错误资源, 而放弃了低优先数据源中的正确资源. #1521
                selectImpl(preferred.filter { it.similarity > 80 })?.let {
                    return it
                }
            }
            selectImpl(preferred)?.let {
                return it
            }
        }

        if (shouldPreferSeasons) {
            val seasons = candidates.filter { it.result.episodeRange?.hasSeason() == true }
            selectImpl(seasons)?.let {
                return it
            }
        }
        selectImpl(candidates)?.let { return it }
        return selectAny(candidates)
    }
}
