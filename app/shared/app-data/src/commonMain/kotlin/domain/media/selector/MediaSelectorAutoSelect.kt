/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.selector

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.transformWhile
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.app.domain.media.fetch.MediaFetchSession
import me.him188.ani.app.domain.media.fetch.MediaSourceFetchResult
import me.him188.ani.app.domain.media.fetch.MediaSourceFetchState
import me.him188.ani.app.domain.media.fetch.awaitCompletedResults
import me.him188.ani.app.domain.media.fetch.awaitCompletion
import me.him188.ani.app.domain.media.fetch.isFinal
import me.him188.ani.app.domain.mediasource.codec.MediaSourceTier
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.utils.logging.debug
import me.him188.ani.utils.logging.logger
import kotlin.concurrent.Volatile
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * 访问 [MediaSelector] 的自动选择功能
 */
inline val MediaSelector.autoSelect get() = MediaSelectorAutoSelect(this)

/**
 * [MediaSelector] 自动选择功能.
 *
 * 有关数据源选择算法, 参阅 [MediaSelector], 尤其是 "快速选择" 部分.
 */
class MediaSelectorAutoSelect(
    private val mediaSelector: MediaSelector,
) {
    /**
     * 等待所有数据源查询完成, 然后根据用户的偏好设置自动选择.
     *
     * 返回成功选择的 [Media] 对象. 当用户已经手动选择过一个别的 [Media], 或者没有可选的 [Media] 时返回 `null`.
     *
     * @param waitForKind 等待此数据源类型完成后, 才执行选择. 如果为 `null`, 则等待所有数据源查询完成.
     */
    suspend fun awaitCompletedAndSelectDefault(
        mediaFetchSession: MediaFetchSession,
        waitForKind: Flow<MediaSourceKind?> = flowOf(null)
    ): Media? {
        // 等全部加载完成
        mediaFetchSession.awaitCompletion { completedConditions ->
            return@awaitCompletion waitForKind.first()?.let {
                completedConditions[it]
            } ?: completedConditions.allCompleted()
        }
        if (mediaSelector.selected.value == null) {
            val selected = mediaSelector.trySelectDefault()
            return selected
        }
        return null
    }

    /**
     * 快速选择 Web 数据源的 [Media]. 逻辑详见 [MediaSelector] 中 "快速选择" 部分的说明.
     *
     * 算法是一个响应式循环: 观察 (当前阶段, 各 WEB 源的查询状态, 候选流), 任一变化就按当前阶段的规则
     * 调用一次 [MediaSelector.trySelectFromMediaSources], 选中即返回. 阶段随时间推进:
     *
     * 1. [FastSelectPhase.INSTANT]: 只选有效 tier 不超过 [instantSelectTierThreshold] 且条目名称精确匹配的资源;
     * 2. [FastSelectPhase.EXACT_ONLY] (经过 [lowTierToleranceDuration] 后): 任意 tier, 但只选精确匹配, 按 tier 升序;
     * 3. [FastSelectPhase.FUZZY] (经过 [fuzzyFallbackDuration] 后, 或所有 WEB 源都已结束查询):
     *    先按 tier 升序选精确匹配, 再按 tier 升序选模糊匹配.
     *
     * 返回成功选择的 [Media] 对象. 以下情况返回 `null`:
     * - 会话中没有 WEB 源;
     * - [overrideUserSelection] 为 `false` 且用户已经选择了一个 [Media];
     * - [overrideUserSelection] 为 `true` 但等待期间选择被外部改变;
     * - 所有 WEB 源都已结束查询, 且在允许模糊匹配的前提下仍然选不出任何资源.
     *
     * 只要还有 WEB 源在查询中, 此函数就会一直挂起等待.
     *
     * @param overrideUserSelection 是否覆盖用户选择.
     * 若为 `true`, 则会忽略调用时的选择, 使用此函数的结果替换选择. 两个例外:
     * 当前选择本身已满足某一档的条件时, 到这一档就保留它 (返回它) 而不是换成同档的别的资源;
     * 等待期间选择被别人 (通常是用户) 改掉时, 放弃覆盖并返回 `null`.
     * 若为 `false`, 如果用户已经选择了一个 media, 则此函数不会做任何事情.
     * @param blacklistMediaIds 黑名单, 这些 media 不会被选择. 如果遇到黑名单中的 media, 将会跳过.
     * @param lowTierToleranceDuration 第一段超时: 之后放开 tier 限制, 只要求精确匹配.
     * @param fuzzyFallbackDuration 第二段超时: 之后允许模糊匹配兜底. 小于 [lowTierToleranceDuration] 时视为与其相等.
     * @param instantSelectTierThreshold Low Tier 与 High Tier 的分界线, 小于等于此 Tier 的资源可被立即选择.
     */ // #1323
    @OptIn(UnsafeOriginalMediaAccess::class) // 仅用 original 读 mediaId 判断候选流是否已吸收查询结果, 不参与选择
    suspend fun fastSelectWebSources(
        mediaFetchSession: MediaFetchSession,
        sourceTiers: MediaSelectorSourceTiers,
        overrideUserSelection: Boolean = false,
        blacklistMediaIds: Set<String> = emptySet(),
        lowTierToleranceDuration: Duration = 5.seconds,
        fuzzyFallbackDuration: Duration = DefaultFuzzyFallbackDuration,
        instantSelectTierThreshold: MediaSourceTier = InstantSelectTierThreshold,
    ): Media? {
        val webResults = mediaFetchSession.mediaSourceResults.filter { it.kind == MediaSourceKind.WEB }
        if (webResults.isEmpty()) return null

        val rules = FastSelectRules(sourceTiers, instantSelectTierThreshold)

        val timerPhase = flow {
            emit(FastSelectPhase.INSTANT)
            delay(lowTierToleranceDuration)
            emit(FastSelectPhase.EXACT_ONLY)
            delay(maxOf(fuzzyFallbackDuration, lowTierToleranceDuration) - lowTierToleranceDuration)
            emit(FastSelectPhase.FUZZY)
        }

        val sourceStates = combine(webResults.map { result -> result.state.map { result to it } }) { it.toList() }

        // 开始时的选择. overrideUserSelection 时, 若之后选择被别人 (通常是用户) 改掉了, 就不再覆盖.
        val initialSelected = mediaSelector.selected.value

        return combine(
            timerPhase,
            sourceStates,
            mediaSelector.filteredCandidates,
        ) { phase, states, candidates ->
            val succeededSources = states.filter { (_, state) -> state is MediaSourceFetchState.Succeed }.map { it.first }
            val allFinal = states.all { (_, state) -> state.isFinal }
            // 候选流是从查询结果异步派生的, 源状态变成 Succeed 时候选流可能还没吸收它的结果.
            // 这样的快照是不一致的, 跳过它等下一次候选流更新即可 (候选流是结果的 1:1 map, 更新一定会来).
            val absorbedMediaIds = candidates.mapTo(HashSet()) { it.original.mediaId }
            val consistent = succeededSources.all { source ->
                source.awaitCompletedResults().all { it.mediaId in absorbedMediaIds }
            }
            FastSelectSnapshot(
                // 所有源都结束了就没有必要再等, 直接进入最宽松的阶段
                phase = if (allFinal && consistent) FastSelectPhase.FUZZY else phase,
                succeededSources = succeededSources,
                allFinal = allFinal,
                consistent = consistent,
                candidates = candidates.filterIsInstance<MaybeExcludedMedia.Included>(),
            )
        }.map { snapshot ->
            // 用 map 而不是 mapLatest: 一次选择尝试很快, 且不应在 CAS 成功后被取消而丢失返回值.
            val selected = when {
                !snapshot.consistent -> null
                // 覆盖模式下选择已被别人改掉: 不再尝试, 交给下面的终止判断
                overrideUserSelection && mediaSelector.selected.value != initialSelected -> null
                else -> trySelect(snapshot, rules, overrideUserSelection, blacklistMediaIds)
            }
            snapshot to selected
        }.transformWhile { (snapshot, selected) ->
            when {
                selected != null -> {
                    logger.debug { "fastSelect: selected $selected in phase ${snapshot.phase}" }
                    emit(selected)
                    false
                }

                !snapshot.consistent -> true

                !overrideUserSelection && mediaSelector.selected.value != null -> {
                    logger.debug { "fastSelect: user has already selected, give up" }
                    emit(null)
                    false
                }

                overrideUserSelection && mediaSelector.selected.value != initialSelected -> {
                    logger.debug { "fastSelect: selection changed externally while overriding, give up" }
                    emit(null)
                    false
                }

                snapshot.allFinal -> {
                    logger.debug { "fastSelect: all web sources completed but nothing selectable" }
                    emit(null)
                    false
                }

                else -> true
            }
        }.first()
    }

    private suspend fun trySelect(
        snapshot: FastSelectSnapshot,
        rules: FastSelectRules,
        overrideUserSelection: Boolean,
        blacklistMediaIds: Set<String>,
    ): Media? {
        if (snapshot.succeededSources.isEmpty()) return null
        val sourceIds = snapshot.succeededSources.map { it.mediaSourceId }
        val eligible = snapshot.candidates.filter {
            it.result.mediaSourceId in sourceIds && it.result.mediaId !in blacklistMediaIds
        }
        // overrideUserSelection 时, 当前选择若本身就满足某一档的条件, 到这一档就应该保留它, 而不是因为
        // trySelectFromMediaSources "选到了同一个" 返回 null 而继续降级到更差的一档.
        val current = if (overrideUserSelection) {
            mediaSelector.selected.value?.let { selected -> eligible.firstOrNull { it.result.mediaId == selected.mediaId } }
        } else null
        for (filter in rules.attemptsFor(snapshot.phase, eligible)) {
            if (current != null && filter(current)) return current.result
            mediaSelector.trySelectFromMediaSources(
                sourceIds,
                overrideUserSelection = overrideUserSelection,
                blacklistMediaIds = blacklistMediaIds,
                allowNonPreferred = true, // 快速选择源是 web 源, 可以不考虑偏好.
                candidateMediaFilter = filter,
            )?.let { return it }
        }
        return null
    }

    /**
     * 从用户偏好的 web 数据源快速选择媒体, 如果没有则返回 null
     */
    @OptIn(UnsafeOriginalMediaAccess::class) // 仅用 original 读 mediaSourceId 判断该源是否已进入候选流, 不参与选择
    suspend fun trySelectPreferredWebSource(
        mediaFetchSession: MediaFetchSession,
        preferredWebMediaSourceId: String?,
    ): Media? {
        if (preferredWebMediaSourceId == null) return null

        // 等待该源查询完成. 若该源不存在则直接返回.
        val result = mediaFetchSession.mediaSourceResults
            .firstOrNull { it.mediaSourceId == preferredWebMediaSourceId && it.kind == MediaSourceKind.WEB }
            ?: return null
        result.awaitCompletion()

        suspend fun trySelect(): Media? = mediaSelector.trySelectFromMediaSources(
            listOf(preferredWebMediaSourceId),
            overrideUserSelection = false,
            blacklistMediaIds = emptySet(),
            allowNonPreferred = false, // 只从这一个源里选
        )

        // 先做一次快照选择. 候选流已就绪时立即成功, 保持与旧实现一致的时序 (不引入额外挂起, 以免在
        // 上层 select {} 竞争中把该源的选择让给兜底 clause).
        trySelect()?.let { return it }

        // 快照落空有两种可能:
        // 1) 该源确实没有可选结果;
        // 2) awaitCompletion 只保证该源自身状态完成, 而 DefaultMediaSelector 的候选流 (filteredCandidates /
        //    preferredCandidates) 是经 combine + shareIn(replay=1) 从 cumulativeResults 异步派生的, 传播有延迟,
        //    此刻 shareIn 回放给 .first() 的还是不含该源结果的旧快照 (生产环境的实际竞态).
        // 先排除 (1): 该源没有任何结果就无需选择.
        if (result.awaitCompletedResults().isEmpty()) return null

        // 处理 (2): 挂起等候选流吸收该源的结果后再快照一次; 若仍为空说明该源确实没有可选项.
        // filterMediaList 是 1:1 map (Included/Excluded), 该源既然有结果就一定会出现在 filteredCandidates 里,
        // 所以此处不会永久挂起.
        mediaSelector.filteredCandidates.first { candidates ->
            candidates.any { it.original.mediaSourceId == preferredWebMediaSourceId }
        }
        return trySelect()
    }

    /**
     * 自动选择第一个 [MediaSourceKind.LocalCache] [Media].
     *
     * 当成功选择了一个 [Media] 时返回它. 若已经选择了一个别的, 或没有 [MediaSourceKind.LocalCache] 类型的 [Media] 供选择, 返回 `null`.
     */
    suspend fun selectCached(
        mediaFetchSession: MediaFetchSession,
        maxAttempts: Int = Int.MAX_VALUE,
    ): Media? {
        val isSuccess = object {
            @Volatile
            var value: Media? = null

            @Volatile
            var attempted = 0
        }
        combine(
            mediaFetchSession.cumulativeResults,
        ) { _ ->
            if (mediaSelector.selected.value != null) {
                // 用户已经选择了
                isSuccess.value = null
                return@combine STOP
            }

            val selected = mediaSelector.trySelectCached()
            if (selected != null) {
                isSuccess.value = selected
                STOP
            } else {
                if (++isSuccess.attempted >= maxAttempts) {
                    // 尝试次数过多
                    STOP
                } else {
                    // 继续等待
                    !STOP
                }
            }
        }.takeWhile { it == !STOP }.collect()
        return isSuccess.value
    }

    // #355 播放时自动启用上次临时启用选择的数据源
    suspend fun autoEnableLastSelected(mediaFetchSession: MediaFetchSession) {
        val lastSelectedId = mediaSelector.mediaSourceId.finalSelected.first()
        val lastSelected = mediaFetchSession.mediaSourceResults.firstOrNull {
            it.mediaSourceId == lastSelectedId
        } ?: return
        lastSelected.enable()
    }

    companion object {
        /**
         * 快速选择第一阶段的 tier 阈值: 有效 tier 小于等于此值且条目名称精确匹配的资源, 在其数据源查询成功后不经等待立即选择.
         *
         * @see MediaSelector
         */
        val InstantSelectTierThreshold = MediaSourceTier(0u)

        /**
         * 快速选择第二段超时的默认值: 从快速选择开始计时, 超过此时长后才允许选择条目名称模糊匹配的资源.
         *
         * @see MediaSelector
         */
        val DefaultFuzzyFallbackDuration = 15.seconds
    }
}

/**
 * 快速选择的阶段. 顺序有语义: 越靠后越宽松.
 */
enum class FastSelectPhase {
    /** 只选低 tier 且精确匹配的资源, 数据源一查询成功就选. */
    INSTANT,

    /** 任意 tier, 只选精确匹配, 按 tier 升序. */
    EXACT_ONLY,

    /** 先精确后模糊, 各自按 tier 升序. */
    FUZZY,
}

/**
 * 快速选择某一时刻观察到的全部输入.
 */
private class FastSelectSnapshot(
    val phase: FastSelectPhase,
    /** 状态为 [MediaSourceFetchState.Succeed] 的 WEB 源. */
    val succeededSources: List<MediaSourceFetchResult>,
    /** 所有 WEB 源都已结束 (完成或禁用). */
    val allFinal: Boolean,
    /** 候选流是否已经吸收了所有 [succeededSources] 的全部结果. 为 `false` 的快照不参与选择. */
    val consistent: Boolean,
    /** 候选流快照中未被排除的资源. */
    val candidates: List<MaybeExcludedMedia.Included>,
)

/**
 * 各阶段的选择规则. 独立成类以便直接测试规则本身.
 */
internal class FastSelectRules(
    private val sourceTiers: MediaSelectorSourceTiers,
    private val instantSelectTierThreshold: MediaSourceTier,
) {
    private fun MaybeExcludedMedia.Included.effectiveTier(): MediaSourceTier =
        sourceTiers.get(result.mediaSourceId, result.properties.alliance)

    private fun MaybeExcludedMedia.Included.isExactMatch(): Boolean =
        metadata.subjectMatchKind == MatchMetadata.SubjectMatchKind.EXACT

    /**
     * 按 tier 升序, 为 [candidates] 中每个出现过的有效 tier 生成一个过滤器. 一次只尝试一档, 以保证严格的 tier 优先级,
     * 不受 [MediaSelector] 内部按数据源顺序选择的影响.
     */
    private fun byTierAscending(
        candidates: List<MaybeExcludedMedia.Included>,
        matcher: (MaybeExcludedMedia.Included) -> Boolean,
    ): List<(MaybeExcludedMedia.Included) -> Boolean> {
        return candidates.asSequence()
            .filter(matcher)
            .map { it.effectiveTier() }
            .distinct()
            .sorted()
            .map { tier -> { media: MaybeExcludedMedia.Included -> matcher(media) && media.effectiveTier() == tier } }
            .toList()
    }

    /**
     * 返回本阶段应依次尝试的 media 级过滤器. 调用方按顺序逐个调用 [MediaSelector.trySelectFromMediaSources], 第一个成功的即为结果.
     * 空列表表示当前没有任何满足本阶段条件的候选.
     */
    fun attemptsFor(
        phase: FastSelectPhase,
        candidates: List<MaybeExcludedMedia.Included>,
    ): List<(MaybeExcludedMedia.Included) -> Boolean> = when (phase) {
        FastSelectPhase.INSTANT -> {
            val instant: (MaybeExcludedMedia.Included) -> Boolean =
                { it.isExactMatch() && it.effectiveTier() <= instantSelectTierThreshold }
            if (candidates.any(instant)) listOf(instant) else emptyList()
        }

        FastSelectPhase.EXACT_ONLY -> byTierAscending(candidates) { it.isExactMatch() }

        FastSelectPhase.FUZZY -> byTierAscending(candidates) { it.isExactMatch() } +
                byTierAscending(candidates) { !it.isExactMatch() }
    }
}

private const val STOP = true

// 日常没啥用, 只有出 bug 了才会用到
private val logger = /*SilentLogger*/logger<MediaSelectorAutoSelect>()
