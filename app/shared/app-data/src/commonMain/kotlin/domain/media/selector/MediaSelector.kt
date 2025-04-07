/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.selector

import androidx.compose.ui.util.fastFirstOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.data.models.preference.MediaPreference.Companion.ANY_FILTER
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.app.domain.media.selector.filter.MediaSelectorFilterSortAlgorithm
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.isLocalCache
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.topic.hasSeason
import kotlin.coroutines.CoroutineContext

/**
 * 用于管理一组 [Media]，通过对其进行过滤、应用用户偏好以及上下文信息，
 * 最终选择出单个 [Media] 资源的选择器接口。
 *
 * 本接口主要包含以下三个阶段：
 * 1. **过滤**：基于 [MediaSelectorSettings] 等信息，产出 [filteredCandidates]（包含排除原因）与
 *    [filteredCandidatesMedia]（仅保留未被排除的资源）。过滤逻辑常考虑字幕是否存在、完结番是否隐藏单集资源等。
 * 2. **偏好**：提供 [alliance]、[resolution]、[subtitleLanguageId]、[mediaSourceId] 等偏好项，通过
 *    [MediaPreferenceItem] 合并用户在本次会话中的设置 [MediaPreferenceItem.userSelected] 与用户在系统设置中配置的全局默认值 [MediaPreferenceItem.defaultSelected]，
 *    从而进一步缩小为 [preferredCandidates] 和 [preferredCandidatesMedia]。
 * 3. **选择**：支持手动或自动方式来选中某个 [Media]：
 *    - 手动调用 [select]。
 *    - 自动通过 [trySelectDefault]、[trySelectCached] 或 [trySelectFromMediaSources] 等方法完成。
 *
 *    最终选定的资源会存入 [selected]，并通过 [events] 广播变更。
 *
 * ### 1. 过滤阶段
 *
 * - [filteredCandidates]：包含所有被发现的资源，以及若被排除则附带 [MediaExclusionReason]。
 * - [filteredCandidatesMedia]：仅包含未排除的资源，用于后续处理。
 *
 * ### 2. 偏好阶段
 *
 * - 偏好项 [alliance]、[resolution]、[subtitleLanguageId]、[mediaSourceId] 分别对应字幕组、分辨率、字幕语言、数据源 ID 等。
 * - 系统和用户偏好合并后形成最终的可用值，再对 [filteredCandidatesMedia] 做“偏好筛选”，得到更精简的 [preferredCandidates]、[preferredCandidatesMedia]。
 *
 * ### 3. 选择阶段
 *
 * - [selected]：当前被选中的资源，若尚未选择则为 `null`。
 * - [select]：手动选择某个 [Media]，并更新相关偏好，触发对应事件。
 * - [unselect]：清除当前选择。
 * - [trySelectDefault]：若尚无选择，则基于 [preferredCandidatesMedia] 自动挑选最优资源。
 * - [trySelectCached]：若本地存在可用缓存且尚未选择，则优先选用缓存资源。
 * - [trySelectFromMediaSources]：根据给定的数据源优先级或黑名单等信息进行自动选择。
 *
 * 在选择时, 会触发 [MediaSelectorEvents.onChangePreference] 和 [MediaSelectorEvents.onSelect] 事件, 事件可用于保存用户偏好等操作.
 *
 * ## 数据源阶级
 *
 * 每个数据源 [me.him188.ani.datasources.api.source.MediaSource] 都拥有阶级 [me.him188.ani.app.domain.mediasource.codec.MediaSourceTier]. 阶级会影响排序.
 * 阶级值越低, 数据源排序越靠前.
 *
 * ### 快速选择的情况
 *
 * 如果快速选择数据源功能为启用状态 ([MediaSelectorSettings.fastSelectWebKind]), 将会考虑如下因素:
 *
 * - 当任意 T0 数据源查询完成并且满足用户的字幕组偏好时, [MediaSelectorAutoSelect] 将会立即选择该数据源.
 * - 在等待 [MediaSelectorSettings.fastSelectWebKindAllowNonPreferredDelay] 时长后, 将会立即选择阶级小于 [MediaSelectorAutoSelect.InstantSelectTierThreshold] 的数据源.
 *
 * ## 使用示例
 *
 * 以下是一个演示如何观察各 Flow 并进行选择的示例代码（伪代码）：
 *
 * ```
 * suspend fun usageExample(mediaSelector: MediaSelector) {
 *     // 观察过滤后但包含排除原因的候选
 *     mediaSelector.filteredCandidates.collect { allCandidates ->
 *         println("所有候选资源(含排除原因): $allCandidates")
 *     }
 *
 *     // 观察过滤后可用的字幕组选项
 *     mediaSelector.alliance.available.collect { alliances ->
 *         println("可选的字幕组列表: $alliances")
 *     }
 *
 *     // 如果尚未选择任何资源，尝试自动选择一个符合当前偏好的资源
 *     val autoSelected = mediaSelector.trySelectDefault()
 *     if (autoSelected != null) {
 *         println("已自动选择: ${autoSelected.mediaId}")
 *     }
 *
 *     // 用户在 UI 中手动指定某个资源
 *     val userChosenMedia: Media = /* 由用户在界面中选取 */
 *     mediaSelector.select(userChosenMedia)
 *
 *     // 最终获取当前选中的资源
 *     println("当前选中的资源: ${mediaSelector.selected.value}")
 * }
 * ```
 *
 * @see Media
 * @see MediaSelectorSettings
 * @see MediaPreference
 * @see me.him188.ani.datasources.api.source.MediaSource
 * @see MediaSelectorFilterSortAlgorithm
 */
interface MediaSelector {
    /**
     * 搜索到的全部的列表, 经过了设置 [MediaSelectorSettings] 筛选.
     *
     * 返回 [MaybeExcludedMedia] 列表, 包含了被排除的原因.
     * @see MediaSelectorFilterSortAlgorithm
     */
    val filteredCandidates: Flow<List<MaybeExcludedMedia>>

    /**
     * 搜索到的全部的列表, 经过了设置 [MediaSelectorSettings] 筛选.
     * @see MediaSelectorFilterSortAlgorithm
     */
    val filteredCandidatesMedia: Flow<List<Media>>

    /**
     * 用户的偏好字幕组设置
     */
    val alliance: MediaPreferenceItem<String>

    /**
     * 用户的偏好分辨率设置
     */
    val resolution: MediaPreferenceItem<String>

    /**
     * 用户的偏好字幕语言设置
     */
    val subtitleLanguageId: MediaPreferenceItem<String>

    /**
     * 用户的偏好数据源 ID 设置
     */
    val mediaSourceId: MediaPreferenceItem<String>

    /**
     * [filteredCandidatesMedia] 经过 [alliance], [resolution], [subtitleLanguageId] 和 [mediaSourceId] 筛选后的列表.
     */
    val preferredCandidates: Flow<List<MaybeExcludedMedia>>

    /**
     * [filteredCandidatesMedia] 经过 [alliance], [resolution], [subtitleLanguageId] 和 [mediaSourceId] 筛选后的列表.
     */
    val preferredCandidatesMedia: Flow<List<Media>>

    /**
     * 目前选中的项目. 它不一定是 [preferredCandidatesMedia] 中的一个项目.
     */
    val selected: StateFlow<Media?>

    /**
     * 用于监听 [select] 等事件
     * @see eventHandling
     */
    val events: MediaSelectorEvents

    /**
     * 选择一个 [Media]. 该 [Media] 可以是位于 [preferredCandidatesMedia] 中的, 也可以不是.
     * 将会更新 [selected] 并广播事件 [MediaSelectorEvents.onChangePreference] 和 [MediaSelectorEvents.onSelect].
     *
     * 该操作优先级高于任何其他的选择. 即会覆盖 [trySelectDefault] 和 [trySelectCached] 的结果.
     *
     * 重复 [select] 同一个 [Media] 时, 本函数立即返回 `false`, 不会做重复广播事件等.
     *
     * @return 当成功将 [selected] 更新为 [candidate] 时返回 `true`. 当 [selected] 已经是 [candidate] 时返回 `false`.
     */
    suspend fun select(candidate: Media): Boolean

    /**
     * 清除当前的选择, 不会更新配置
     */
    fun unselect()

    /**
     * 尝试使用目前的偏好设置, 自动选择一个. 当已经有用户选择或默认选择时返回 `null`.
     *
     * @return 成功选择且已经记录的 [Media]. 返回 `null` 时表示没有选择.
     * @see autoSelect
     */
    suspend fun trySelectDefault(): Media?

    /**
     * 根据提供的顺序 [mediaSourceOrder], 尝试选择一个 media.
     *
     * @param mediaSourceOrder 数据源顺序. 会优先选择 index 小的数据源中的 media.
     * @param overrideUserSelection 是否覆盖用户选择.
     * 若为 `true`, 则会忽略用户目前的选择, 使用此函数的结果替换选择.
     * 若为 `false`, 如果用户已经选择了一个 media, 则此函数不会做任何事情.
     * @param blacklistMediaIds 黑名单, 这些 media 不会被选择. 如果遇到黑名单中的 media, 将会跳过.
     * @param allowNonPreferred 是否允许选择不满足用户偏好设置的项目. 如果为 `false`, 将只会从 [preferredCandidatesMedia] 中选择.
     * 如果为 `true`, 则放弃用户偏好, 只根据数据源顺序选择.
     *
     * @return 成功选择且已经记录的 [Media]. 返回 `null` 时表示没有选择.
     */
    suspend fun trySelectFromMediaSources(
        mediaSourceOrder: List<String>,
        overrideUserSelection: Boolean = false,
        blacklistMediaIds: Set<String> = emptySet(),
        allowNonPreferred: Boolean = false,
    ): Media?

    /**
     * 尝试选择缓存 ([MediaSourceKind.LocalCache]) 作为默认选择, 如果没有缓存则不做任何事情
     * @return 成功选择且已经记录的缓存, 若没有缓存或用户已经手动选择了一个则返回 `null`
     * @see autoSelect
     */
    suspend fun trySelectCached(): Media?

    /**
     * 逐渐取消选择, 直到 [preferredCandidatesMedia] 有至少一个元素.
     */
    suspend fun removePreferencesUntilFirstCandidate()
}


/**
 * 一个筛选项目
 * @param T 例如字幕语言
 */
interface MediaPreferenceItem<T : Any> {
    /**
     * 目前搜索到的列表
     */
    val available: Flow<List<T>>

    /**
     * 用户在本次会话中的选择, 可能为空.
     */
    val userSelected: Flow<OptionalPreference<T>>

    /**
     * 默认的选择, 为空表示没有默认的选择.
     * 这将会是用户在系统设置中配置的全局默认值.
     */
    val defaultSelected: Flow<T?>

    /**
     * [userSelected] 与 [defaultSelected] 合并考虑的选择. 不必是 [available] 里面的选项.
     */
    val finalSelected: Flow<T?> // 注意, autoEnableLastSelected 依赖 "不必是 [available] 里面的选项" 这个性质.

    /**
     * 用户选择
     */
    suspend fun prefer(value: T)

    /**
     * 删除已有的选择
     */
    suspend fun removePreference()
}

/**
 * @see MediaSelector
 */
class DefaultMediaSelector(
    mediaSelectorContextNotCached: Flow<MediaSelectorContext>,
    mediaListNotCached: Flow<List<Media>>,
    /**
     * 数据库中的用户偏好. 仅当用户在本次会话中没有设置新的偏好时, 才会使用此偏好 (跟随 flow 更新). 不能为空 flow, 否则 select 会一直挂起.
     */
    savedUserPreference: Flow<MediaPreference>,
    /**
     * 若 [savedUserPreference] 未指定某个属性的偏好, 则使用此默认值. 不能为空 flow, 否则 select 会一直挂起.
     */
    private val savedDefaultPreference: Flow<MediaPreference>,
    mediaSelectorSettings: Flow<MediaSelectorSettings>,
    /**
     * context for flow
     */
    private val flowCoroutineContext: CoroutineContext = Dispatchers.Default,
    /**
     * 是否将 [savedDefaultPreference] 和计算工作缓存. 这会导致有些许延迟. 在测试时需要关闭.
     */
    private val enableCaching: Boolean = true,
    private val algorithm: MediaSelectorFilterSortAlgorithm = MediaSelectorFilterSortAlgorithm(),
) : MediaSelector {
    private fun <T> Flow<T>.cached(): Flow<T> {
        if (!enableCaching) return this
        // TODO: 2025/1/5 We need to correctly handle lifecycle. Let DefaultMediaSelector's caller control it.
        return this.shareIn(CoroutineScope(flowCoroutineContext), SharingStarted.WhileSubscribed(5_000), replay = 1)
    }

    private val mediaSelectorSettings = mediaSelectorSettings.cached()
    private val mediaSelectorContext = mediaSelectorContextNotCached.cached()

    @OptIn(UnsafeOriginalMediaAccess::class)
    override val filteredCandidates: Flow<List<MaybeExcludedMedia>> = combine(
        mediaListNotCached.cached(), // cache 是必要的, 当 newPreferences 变更的时候不能重新加载 media list (网络)
        savedDefaultPreference, // 只需要使用 default, 因为目前不能覆盖生肉设置
        // 如果依赖 merged pref, 会产生循环依赖 (mediaList -> mediaPreferenceItem -> newPreferences -> mediaList)
        this.mediaSelectorSettings,
        this.mediaSelectorContext,
    ) { list, pref, settings, context ->
        algorithm.filterMediaList(list, pref, settings, context)
            .let { algorithm.sortMediaList(it, settings, context) }
    }.cached()

    override val filteredCandidatesMedia: Flow<List<Media>> = filteredCandidates.map { list ->
        list.mapNotNull { it.result }
    }.flowOn(flowCoroutineContext)

    private val savedUserPreferenceNotCached = savedUserPreference
    private val savedUserPreference: Flow<MediaPreference> = savedUserPreference.cached()

    override val alliance = mediaPreferenceItem(
        "alliance",
        getFromMediaList = { list ->
            list.mapTo(HashSet(list.size)) { it.properties.alliance }
                .sortedBy { it }
        },
        getFromPreference = { it.alliance },
    )
    override val resolution = mediaPreferenceItem(
        "resolution",
        getFromMediaList = { list ->
            list.mapTo(HashSet(list.size)) { it.properties.resolution }
                .sortedBy { it }
        },
        getFromPreference = { it.resolution },
    )
    override val subtitleLanguageId = mediaPreferenceItem(
        "subtitleLanguage",
        getFromMediaList = { list ->
            list.flatMapTo(HashSet(list.size)) { it.properties.subtitleLanguageIds }
                .sortedByDescending {
                    when (it.uppercase()) {
                        "8K", "4320P" -> 6
                        "4K", "2160P" -> 5
                        "2K", "1440P" -> 4
                        "1080P" -> 3
                        "720P" -> 2
                        "480P" -> 1
                        "360P" -> 0
                        else -> -1
                    }
                }
        },
        getFromPreference = { it.subtitleLanguageId },
    )
    override val mediaSourceId = mediaPreferenceItem(
        "mediaSource",
        getFromMediaList = { list ->
            list.mapTo(HashSet(list.size)) { it.properties.resolution }
                .sortedBy { it }
        },
        getFromPreference = { it.mediaSourceId },
    )

    /**
     * 当前会话中的生效偏好
     */
    private val newPreferences = combine(
        savedDefaultPreference,
        alliance.finalSelected,
        resolution.finalSelected,
        subtitleLanguageId.finalSelected,
        mediaSourceId.finalSelected,
    ) { default, alliance, resolution, subtitleLanguage, mediaSourceId ->
        default.copy(
            alliance = alliance,
            resolution = resolution,
            subtitleLanguageId = subtitleLanguage,
            mediaSourceId = mediaSourceId,
        )
    }.flowOn(flowCoroutineContext) // must not cache

    // collect 一定会计算
    private val preferredCandidatesNotCached =
        combine(this.filteredCandidates, newPreferences) { mediaList, mergedPreferences ->
            algorithm.filterByPreference(mediaList, mergedPreferences)
        }

    override val preferredCandidates: Flow<List<MaybeExcludedMedia>> = preferredCandidatesNotCached.cached()
    override val preferredCandidatesMedia: Flow<List<Media>> =
        preferredCandidates.map { list -> list.mapNotNull { it.result } }

    override val selected: MutableStateFlow<Media?> = MutableStateFlow(null)
    override val events = MutableMediaSelectorEvents()

    override suspend fun select(candidate: Media): Boolean {
        return selectImpl(candidate, updatePreference = true)
    }

    /**
     * 选择一个 [Media].设置为 [selected], 并广播事件.
     * @param force 是否强制选择, 即使 [selected] 已经是 [candidate] 时也会选择.
     */
    private suspend fun selectImpl(candidate: Media, updatePreference: Boolean, force: Boolean = false): Boolean {
        events.onBeforeSelect.emit(SelectEvent(candidate, null))
        if (selected.value == candidate && !force) return false
        selected.value = candidate // MSF, will not trigger new emit

        if (updatePreference) {
            alliance.preferWithoutBroadcast(candidate.properties.alliance)
            resolution.preferWithoutBroadcast(candidate.properties.resolution)
            mediaSourceId.preferWithoutBroadcast(candidate.mediaSourceId)
            candidate.properties.subtitleLanguageIds.singleOrNull()?.let {
                subtitleLanguageId.preferWithoutBroadcast(it)
            }

            broadcastChangePreference()
        }

        // Publish events
        events.onSelect.emit(SelectEvent(candidate, null))
        return true
    }

    override fun unselect() {
        selected.value = null
    }

    private fun selectDefault(candidate: Media): Media? {
        if (!selected.compareAndSet(null, candidate)) return null
        // 自动选择时不更新 preference
        return candidate
    }

    private suspend fun broadcastChangePreference(overrideLanguageId: String? = null) {
        if (events.onChangePreference.subscriptionCount.value == 0) return // 没人监听, 就不用算新的 preference 了
        val savedUserPreference = savedUserPreferenceNotCached.first()
        val preference = newPreferences.first() // must access un-cached
        events.onChangePreference.emit(
            savedUserPreference.copy(
                alliance = preference.alliance,
                resolution = preference.resolution,
                subtitleLanguageId = overrideLanguageId ?: preference.subtitleLanguageId,
                mediaSourceId = preference.mediaSourceId,
            ),
        )
    }

    /**
     * 参照用户偏好和各种限制设置, 从 [candidates] 中选择出最合适的 media.
     * 不会调用 [selectImpl] nor [selectDefault], 也就是说不会更新 [selected]
     */
    private suspend fun findUsingPreferenceFromCandidates(
        candidates: List<MaybeExcludedMedia.Included>,
        mergedPreference: MediaPreference,
    ): Media? {
        val selectedSubtitleLanguageId = mergedPreference.subtitleLanguageId
        val selectedResolution = mergedPreference.resolution
        val selectedAlliance = mergedPreference.alliance
        val selectedMediaSource = mergedPreference.mediaSourceId
        val allianceRegexes = mergedPreference.alliancePatterns.orEmpty().map { it.toRegex() }
        val availableAlliances = alliance.available.first()


        val mediaSelectorContext = mediaSelectorContext.filter {
            it.allFieldsLoaded()
        }.first()
        val mediaSelectorSettings = mediaSelectorSettings.first()


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

    override suspend fun trySelectDefault(): Media? {
        if (selected.value != null) return null
        val candidates = preferredCandidates.first()
        if (candidates.none { it is MaybeExcludedMedia.Included }) return null
        val mergedPreference = newPreferences.first()
        return findUsingPreferenceFromCandidates(
            candidates.filterIsInstance<MaybeExcludedMedia.Included>(),
            mergedPreference,
        )?.let {
            selectDefault(it)
        }
    }

    override suspend fun trySelectFromMediaSources(
        mediaSourceOrder: List<String>,
        overrideUserSelection: Boolean,
        blacklistMediaIds: Set<String>,
        allowNonPreferred: Boolean
    ): Media? {
        if (mediaSourceOrder.isEmpty()) return null

        val selected = run {
            val mergedPreference = newPreferences.first()

            fun bake(candidates: List<MaybeExcludedMedia.Included>): List<MaybeExcludedMedia.Included> {
                return candidates.filter { it.result.mediaSourceId in mediaSourceOrder && it.result.mediaId !in blacklistMediaIds }
                    .sortedBy { mediaSourceOrder.indexOf(it.result.mediaSourceId) }
            }

            findUsingPreferenceFromCandidates(
                bake(preferredCandidates.first().filterIsInstance<MaybeExcludedMedia.Included>()),
                mergedPreference.copy(
                    alliance = ANY_FILTER,
                ),
            )?.let { return@run it } // 先考虑用户偏好

            if (allowNonPreferred) {

                // 如果用户偏好里面没有, 并且允许选择非偏好的, 才考虑全部列表
                findUsingPreferenceFromCandidates(
                    bake(filteredCandidates.first().filterIsInstance<MaybeExcludedMedia.Included>()),
                    mergedPreference.copy(
                        alliance = ANY_FILTER,
                        resolution = ANY_FILTER,
                        subtitleLanguageId = ANY_FILTER,
                        mediaSourceId = ANY_FILTER,
                    ),
                )?.let { return@run it }
            }
            null
        }
        // 实际上 this.selected 已经更新了

        return selected?.let {
            if (overrideUserSelection) {
                if (selectImpl(it, updatePreference = false)) {
                    it
                } else {
                    null
                }
            } else {
                selectDefault(it)
            }
        }
    }

    @OptIn(UnsafeOriginalMediaAccess::class)
    override suspend fun trySelectCached(): Media? {
        if (selected.value != null) return null
        // 不管这个 media 能不能播放, 只要缓存了就行. 所以我们直接使用 `MaybeExcludedMedia.original`

        // 尽量选择满足用户偏好的缓存, 否则再随便挑一个缓存.
        val cached = preferredCandidates.first().firstOrNull { it.original.isLocalCache() }
            ?: filteredCandidates.first().firstOrNull { it.original.isLocalCache() } ?: return null
        return selectDefault(cached.original)
    }

    override suspend fun removePreferencesUntilFirstCandidate() {
        if (preferredCandidatesMedia.first().isNotEmpty()) return
        alliance.removePreference()
        if (preferredCandidatesNotCached.first().isNotEmpty()) return
        resolution.removePreference()
        if (preferredCandidatesNotCached.first().isNotEmpty()) return
        subtitleLanguageId.removePreference()
        if (preferredCandidatesNotCached.first().isNotEmpty()) return
        mediaSourceId.removePreference()
    }

    interface MediaPreferenceItemImpl<T : Any> : MediaPreferenceItem<T> {
        fun preferWithoutBroadcast(value: T)
    }

    private inline fun <reified T : Any> mediaPreferenceItem(
        debugName: String,
        crossinline getFromMediaList: (list: List<Media>) -> List<T>,
        crossinline getFromPreference: (MediaPreference) -> T?,
    ) = object : MediaPreferenceItemImpl<T> {
        override val available: Flow<List<T>> = filteredCandidatesMedia.map { list ->
            getFromMediaList(list)
        }.flowOn(flowCoroutineContext).cached()

        // 当前用户覆盖的选择. 一旦用户有覆盖, 就不要用默认去修改它了
        private val overridePreference: MutableStateFlow<OptionalPreference<T>> =
            MutableStateFlow(OptionalPreference.noPreference())

        /**
         * must not cache, see [removePreferencesUntilFirstCandidate]
         */
        override val userSelected: Flow<OptionalPreference<T>> =
            combine(savedUserPreference, overridePreference) { preference, override ->
                override.flatMapNoPreference {
                    OptionalPreference.preferIfNotNull(getFromPreference(preference))
                }
            }.flowOn(flowCoroutineContext)

        override val defaultSelected: Flow<T?> = savedDefaultPreference.map { getFromPreference(it) }
            .flowOn(flowCoroutineContext).cached()

        /**
         * must not cache, see [removePreferencesUntilFirstCandidate]
         */
        override val finalSelected: Flow<T?> = combine(userSelected, defaultSelected) { user, default ->
            user.orElse { default }
        }.flowOn(flowCoroutineContext)

        override suspend fun removePreference() {
            overridePreference.value = OptionalPreference.preferNoValue()
            broadcastChangePreference(null)
        }

        override fun preferWithoutBroadcast(value: T) {
            overridePreference.value = OptionalPreference.prefer(value)
        }

        override suspend fun prefer(value: T) {
            preferWithoutBroadcast(value)
            broadcastChangePreference(null)
        }

        override fun toString(): String = "MediaPreferenceItem($debugName)"
    }
}
