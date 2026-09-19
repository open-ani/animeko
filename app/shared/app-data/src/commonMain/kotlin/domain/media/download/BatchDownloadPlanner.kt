/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.download

import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.datasources.api.EpisodeType
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.topic.contains
import me.him188.ani.datasources.api.topic.isSingleEpisode

/**
 * 本条目已有的一条下载记录.
 */
data class ExistingDownload(
    /**
     * 记录的来源资源.
     */
    val origin: Media,
    val episodeId: Int,
)

/**
 * 批量下载中一集的处置.
 */
sealed interface EpisodeDownloadPlan {
    /**
     * 本集已有下载记录, 不再创建.
     */
    data object AlreadyDownloaded : EpisodeDownloadPlan

    /**
     * 复用已有下载中覆盖本集的合集资源: 为本集新增一条记录, 与已有记录共用同一个下载会话.
     */
    data class Reuse(val media: Media) : EpisodeDownloadPlan

    /**
     * 用候选中的资源为本集创建下载.
     */
    data class Create(val media: Media) : EpisodeDownloadPlan

    /**
     * 候选中没有覆盖本集的资源.
     */
    data object Uncovered : EpisodeDownloadPlan
}

/**
 * 将要为该集创建记录时使用的资源; 不创建时为 `null`.
 */
val EpisodeDownloadPlan.mediaOrNull: Media?
    get() = when (this) {
        is EpisodeDownloadPlan.Reuse -> media
        is EpisodeDownloadPlan.Create -> media
        EpisodeDownloadPlan.AlreadyDownloaded, EpisodeDownloadPlan.Uncovered -> null
    }

/**
 * 为 [episodes] 规划批量下载. 纯函数.
 *
 * 对每一集依次判定:
 * 1. 已有本集的记录 → [EpisodeDownloadPlan.AlreadyDownloaded];
 * 2. 用户点选的 [pinned] 固定给 [pinnedEpisodeId] (它已有覆盖它的合集时也用点选的);
 * 3. 已有下载里已知集数且覆盖本集的合集 → [EpisodeDownloadPlan.Reuse]; [pinned] 是已知集数的合集时, 它覆盖的其他集也用它
 *    (只知道整季的合集未必真含其他集, 只固定给 [pinnedEpisodeId]);
 * 4. 候选中的合集做贪心集合覆盖: 每轮取覆盖最多未处置集的合集, 已知集数的合集优先于只知道整季的;
 *    合集至少覆盖两个未处置的集才压倒单集. 多个合集可以拼接;
 * 5. 否则取候选中第一个覆盖本集的单集资源; 没有单集时取覆盖本集的合集, 已知集数的优先;
 * 6. 否则 [EpisodeDownloadPlan.Uncovered].
 *
 * 只要候选中有资源覆盖某集, 该集就总是可下载: 预览时可下载的集, 确认时无论勾选了哪些集都仍然可下载.
 *
 * 特别篇只按 sort 精确匹配, 不用 ep, 也不视为被只知道整季的合集覆盖.
 *
 * @param episodes 要规划的集; 返回的 map 保持此顺序.
 * @param candidates 同一 (数据源, 字幕组 / 线路) 组内的条目级候选, 已按选择器排序, 不含本地缓存.
 * @param existing 本条目已有的下载, 包括本会话刚创建的.
 */
fun planBatchDownload(
    episodes: List<EpisodeInfo>,
    candidates: List<Media>,
    existing: List<ExistingDownload>,
    pinned: Media? = null,
    pinnedEpisodeId: Int? = null,
): Map<Int, EpisodeDownloadPlan> {
    val plan = HashMap<Int, EpisodeDownloadPlan>(episodes.size)
    val downloadedIds = existing.mapTo(HashSet()) { it.episodeId }
    // 只知道整季的已有合集未必真含其他集, 不复用; 用户为某集点选它时只建那一集 (见下方 pinned)
    val existingPacks = existing.map { it.origin }.filter { it.isPack() && it.episodeRange?.isKnown == true }.distinctBy { it.mediaId }
    val uncovered = ArrayList<EpisodeInfo>(episodes.size)

    for (episode in episodes) {
        if (episode.episodeId in downloadedIds) {
            plan[episode.episodeId] = EpisodeDownloadPlan.AlreadyDownloaded
            continue
        }
        if (pinned != null && episode.episodeId == pinnedEpisodeId) {
            plan[episode.episodeId] = EpisodeDownloadPlan.Create(pinned)
            continue
        }
        val reusable = existingPacks.firstOrNull { it.covers(episode) }
        if (reusable != null) {
            plan[episode.episodeId] = EpisodeDownloadPlan.Reuse(reusable)
        } else {
            uncovered += episode
        }
    }

    if (pinned != null && pinned.isPack() && pinned.episodeRange?.isKnown == true) {
        uncovered.removeAll { episode ->
            val use = pinned.covers(episode)
            if (use) plan[episode.episodeId] = EpisodeDownloadPlan.Create(pinned)
            use
        }
    }

    val packs = candidates.filter { it.isPack() && it.mediaId != pinned?.mediaId }
    while (uncovered.isNotEmpty()) {
        var best: Media? = null
        var bestCovered: List<EpisodeInfo> = emptyList()
        for (pack in packs) {
            val covered = uncovered.filter { pack.covers(it) }
            if (covered.size < 2) continue
            if (best == null || isBetterPack(pack, covered, best, bestCovered)) {
                best = pack
                bestCovered = covered
            }
        }
        val chosen = best ?: break
        for (episode in bestCovered) plan[episode.episodeId] = EpisodeDownloadPlan.Create(chosen)
        uncovered.removeAll(bestCovered.toSet())
    }

    for (episode in uncovered) {
        val media = candidates.firstOrNull { it.isSingle() && it.covers(episode) }
            ?: packs.filter { it.covers(episode) }.maxByOrNull { it.episodeRange?.isKnown == true }
        plan[episode.episodeId] = if (media != null) EpisodeDownloadPlan.Create(media) else EpisodeDownloadPlan.Uncovered
    }

    return episodes.associateTo(LinkedHashMap(episodes.size)) { it.episodeId to plan.getValue(it.episodeId) }
}

/**
 * 已知集数的合集优先于只知道整季的; 其次覆盖更多集的优先. 都相同时保持候选顺序.
 */
private fun isBetterPack(
    pack: Media,
    covered: List<EpisodeInfo>,
    best: Media,
    bestCovered: List<EpisodeInfo>,
): Boolean {
    val packKnown = pack.episodeRange?.isKnown == true
    val bestKnown = best.episodeRange?.isKnown == true
    if (packKnown != bestKnown) return packKnown
    return covered.size > bestCovered.size
}

private fun Media.isPack(): Boolean = episodeRange?.let { !it.isSingleEpisode() } == true

private fun Media.isSingle(): Boolean = episodeRange?.isSingleEpisode() == true

/**
 * 本篇按 sort 或 ep 匹配, 只知道整季的合集也算覆盖; 特别篇只按 sort 精确匹配:
 * 序号相同的本篇资源 (如 "01" 之于 SP01) 与整季合集都不算覆盖, 否则批量下载会把本篇当作特别篇下载.
 */
private fun Media.covers(episode: EpisodeInfo): Boolean {
    val range = episodeRange ?: return false
    val mainStory = episode.type == EpisodeType.MainStory
    if (range.contains(episode.sort, allowSeason = mainStory, allowSpecial = false)) return true
    return mainStory && episode.ep?.let { range.contains(it, allowSeason = false, allowSpecial = false) } == true
}
