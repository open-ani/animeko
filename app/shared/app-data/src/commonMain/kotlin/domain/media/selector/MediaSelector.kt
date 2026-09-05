/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.selector

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.data.models.preference.MediaPreference.Companion.ANY_FILTER
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.app.domain.media.selector.engine.AutoSelectSnapshot
import me.him188.ani.app.domain.media.selector.engine.SourceSnapshot
import me.him188.ani.app.domain.media.selector.engine.findMediaByPreference
import me.him188.ani.app.domain.media.selector.filter.MediaSelectorFilterSortAlgorithm
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.isLocalCache
import me.him188.ani.datasources.api.source.MediaSourceKind
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
 * 数据源还可以为各个 channel (对应 [me.him188.ani.datasources.api.MediaProperties.alliance]) 单独指定 tier
 * ([MediaSelectorSourceTiers.channelTiers]). 排序时以资源的有效 tier 为准: channel tier 优先, 没有 channel tier 时使用数据源 tier.
 * 因此同一个数据源的不同 channel 的资源可以有不同的排序优先级.
 *
 * ### 快速选择
 *
 * 快速选择是由 [MediaSelectorAutoSelect] 实现的[拓展功能][MediaSelectorAutoSelect.fastSelectWebSources], 仅对 [WEB][MediaSourceKind.WEB] 源有效.
 * 决策本身是纯函数 [me.him188.ani.app.domain.media.selector.engine.decideWebAutoSelect], 输入是 [autoSelectSnapshots] 组装的一致快照,
 * 由 [me.him188.ani.app.domain.media.selector.engine.runWebAutoSelect] 这一个执行循环驱动.
 * 如果快速选择数据源功能为启用状态 ([MediaSelectorSettings.fastSelectWebKind] 为 `true` 且偏好 [WEB][MediaSourceKind.WEB]),
 * 快速选择会随着时间推进经过三个阶段, 每个阶段只允许选择满足该阶段条件的资源, 数据源每次查询完成都会按当前阶段重新评估一次:
 *
 * 1. **立即选择** (从开始到 [fastSelectWebLowTierToleranceDuration][MediaSelectorSettings.fastSelectWebLowTierToleranceDuration]):
 *    只选择有效 tier (channel tier 优先) 不超过 [MediaSelectorAutoSelect.InstantSelectTierThreshold] 且条目名称精确匹配的资源.
 *    数据源一旦查询成功就立即选择, 不等待其他数据源.
 * 2. **仅精确匹配** (直到 [MediaSelectorAutoSelect.DefaultFuzzyFallbackDuration]):
 *    放开 tier 限制, 但仍只选择条目名称精确匹配的资源, 按有效 tier 升序逐档尝试.
 * 3. **允许模糊匹配** (超过第二段时长, 或所有 WEB 源都已结束查询):
 *    先按 tier 升序选精确匹配, 都没有再按 tier 升序选模糊匹配.
 *
 * "精确匹配" 指 [MatchMetadata.subjectMatchKind] 为 [EXACT][MatchMetadata.SubjectMatchKind.EXACT].
 * 同一 tier 内的取舍由纯函数 [me.him188.ani.app.domain.media.selector.engine.findMediaByPreference] 的偏好逻辑 (分辨率、字幕语言、字幕组、数据源顺序) 决定:
 * 先在用户偏好候选内选, 再放开全部偏好选.
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
     * 根据提供的 [candidateSources], 尝试选择一个 media.
     * 
     * 注意, 调用此方法时将从 [preferredCandidates] 和 [filteredCandidates] 当前的 snapshot 中选择,
     * 如果在选的过程中这些 flow 有更新, 则不会影响此次选择. 所以这个函数会很快返回结果.
     * WEB 自动选择不走此方法, 而是在 [autoSelectSnapshots] 的每次 emission 上重新决策 (参见 `engine/WebAutoSelectDriver.kt`).
     *
     * @param candidateSources 候选数据源, 只会从这些里选.
     * @param overrideUserSelection 是否覆盖用户选择.
     * 若为 `true`, 则会忽略用户目前的选择, 使用此函数的结果替换选择.
     * 若为 `false`, 如果用户已经选择了一个 media, 则此函数不会做任何事情.
     * @param blacklistMediaIds 黑名单, 这些 media 不会被选择. 如果遇到黑名单中的 media, 将会跳过.
     * @param allowNonPreferred 是否允许选择不满足用户偏好设置的项目. 如果为 `false`, 将只会从 [preferredCandidatesMedia] 中选择.
     * 如果为 `true`, 则放弃用户偏好, 只根据数据源顺序选择.
     * @param candidateMediaFilter 额外的 media 级过滤. 只有返回 `true` 的 media 才会被选择.
     * `null` 表示不额外过滤. 过滤器能看到 [MaybeExcludedMedia.Included.metadata], 用于 channel 级 tier、
     * 条目名称匹配程度等需要比数据源更细粒度控制的场景.
     *
     * @return 成功选择且已经记录的 [Media]. 返回 `null` 时表示没有选择.
     */
    suspend fun trySelectFromMediaSources(
        candidateSources: List<String>,
        overrideUserSelection: Boolean = false,
        blacklistMediaIds: Set<String> = emptySet(),
        allowNonPreferred: Boolean = false,
        candidateMediaFilter: ((MaybeExcludedMedia.Included) -> Boolean)? = null,
    ): Media?

    /**
     * 尝试选择缓存 ([MediaSourceKind.LocalCache]) 作为默认选择, 如果没有缓存则不做任何事情
     * @return 成功选择且已经记录的缓存, 若没有缓存或用户已经手动选择了一个则返回 `null`
     * @see autoSelect
     */
    suspend fun trySelectCached(): Media?

    /**
     * 为自动选择决策核组装一致快照: 把 [sources] 的每次 emission 与当前偏好、设置、context 同步合并,
     * 在同一次 emission 内算出过滤、排序与偏好筛选结果. 不经过 [filteredCandidates] 等带缓存的派生流, 因此没有传播延迟.
     *
     * 实现细节, 供 [MediaSelectorAutoSelect] 使用.
     */
    fun autoSelectSnapshots(sources: Flow<List<SourceSnapshot>>): Flow<AutoSelectSnapshot>

    /**
     * 自动选择的提交: 仅当当前 [selected] 仍为 [expectedSelection] 时以 CAS 切换到 [candidate]. 不更新用户偏好.
     * [candidate] 与 [expectedSelection] 相同时视为保留当前选择, 直接返回它, 不发事件.
     *
     * @return 提交后的 [Media]; 选择已被别人改变 (CAS 失败) 时返回 `null`.
     */
    suspend fun selectAutomatically(candidate: Media, expectedSelection: Media?): Media?

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
    /**
     * [cached] 使用的 scope. 为 `null` 时保持原行为: 每个 [cached] 调用创建一个无人管理的 [CoroutineScope].
     */
    private val cachingScope: CoroutineScope? = null,
) : MediaSelector {
    private fun <T> Flow<T>.cached(): Flow<T> {
        if (!enableCaching) return this
        // TODO: 2025/1/5 We need to correctly handle lifecycle. Let DefaultMediaSelector's caller control it.
        return this.shareIn(
            cachingScope ?: CoroutineScope(flowCoroutineContext),
            SharingStarted.WhileSubscribed(5_000),
            replay = 1,
        )
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
     * 内部媒体选择实现函数。
     *
     * 设置当前选中的 [Media]，并在必要时更新用户偏好设置及广播事件。
     *
     * 此函数会发出 [onBeforeSelect] 与 [onSelect] 两个事件，分别用于监听前置逻辑和最终结果。
     * 若传入的 [candidate] 与当前选中的媒体相同，且 [force] 为 false，则不会进行任何操作。
     *
     * @param candidate 待选中的媒体项。
     * @param updatePreference 是否根据此媒体更新用户偏好（如分辨率、联盟、字幕语言等）。
     * @param force 是否强制切换媒体。若为 true，则即使媒体未变也会触发切换。
     * @return 若成功完成切换则返回 true，否则为 false。
     */
    private suspend fun selectImpl(
        candidate: Media,
        updatePreference: Boolean,
        force: Boolean = false
    ): Boolean {
        val previous = selected.value

        // 若未启用强制切换，且目标与当前项相同，则跳过
        if (!force && previous == candidate) return false

        // 发出切换前事件
        events.onBeforeSelect.emit(
            SelectEvent(
                media = candidate,
                subtitleLanguageId = null,
                previousMedia = previous,
            ),
        )

        selected.value = candidate // MSF, will not trigger new emit

        // 更新用户偏好
        if (updatePreference) {
            alliance.preferWithoutBroadcast(candidate.properties.alliance)
            resolution.preferWithoutBroadcast(candidate.properties.resolution)
            mediaSourceId.preferWithoutBroadcast(candidate.mediaSourceId)
            candidate.properties.subtitleLanguageIds.singleOrNull()?.let {
                subtitleLanguageId.preferWithoutBroadcast(it)
            }

            broadcastChangePreference()
            if (candidate.kind == MediaSourceKind.WEB) {
                broadcastWebSourcePreference(candidate.mediaSourceId)
            }
        }

        // 发出选择完成事件
        events.onSelect.emit(
            SelectEvent(
                media = candidate,
                subtitleLanguageId = null,
                previousMedia = previous,
            ),
        )

        return true
    }

    override fun unselect() {
        selected.value = null
    }

    private suspend fun selectDefault(candidate: Media): Media? = selectAutomatically(candidate, expectedSelection = null)

    override suspend fun selectAutomatically(candidate: Media, expectedSelection: Media?): Media? {
        if (selected.value != expectedSelection) return null
        if (candidate == expectedSelection) return candidate // 保留当前选择

        val event = SelectEvent(
            media = candidate,
            subtitleLanguageId = null,
            previousMedia = expectedSelection,
        )
        events.onBeforeSelect.emit(event)

        if (!selected.compareAndSet(expectedSelection, candidate)) return null
        events.onSelect.emit(event)

        // 自动选择时不更新 preference
        return candidate
    }

    override fun autoSelectSnapshots(sources: Flow<List<SourceSnapshot>>): Flow<AutoSelectSnapshot> {
        return combine(
            sources,
            savedDefaultPreference, // 与 filteredCandidates 一致: 过滤只用全局默认偏好 (见 filteredCandidates 处注释)
            newPreferences,
            mediaSelectorSettings,
            mediaSelectorContext,
        ) { sourceSnapshots, defaultPreference, mergedPreference, settings, context ->
            val mediaList = sourceSnapshots.flatMap { it.results }.distinctBy { it.mediaId }
            val candidates = algorithm.filterMediaList(mediaList, defaultPreference, settings, context)
                .let { algorithm.sortMediaList(it, settings, context) }
            AutoSelectSnapshot(
                sources = sourceSnapshots,
                candidates = candidates,
                preferred = algorithm.filterByPreference(candidates, mergedPreference)
                    .filterIsInstance<MaybeExcludedMedia.Included>(),
                mergedPreference = mergedPreference,
                settings = settings,
                context = context,
            )
        }.flowOn(flowCoroutineContext)
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

    private suspend fun broadcastWebSourcePreference(mediaSourceId: String) {
        events.onPreferWebSource.emit(
            PreferWebSourceEvent(
                mediaSelectorContext.first().subjectInfo?.subjectId ?: return,
                mediaSourceId,
            ),
        )
    }

    /**
     * 参照用户偏好和各种限制设置, 从 [candidates] 中选择出最合适的 media.
     * 不会调用 [selectImpl] nor [selectDefault], 也就是说不会更新 [selected].
     *
     * 决策本身是纯函数 [findMediaByPreference]; 这里只负责等待 context 加载完成并取字幕组列表.
     */
    private suspend fun findUsingPreferenceFromCandidates(
        candidates: List<MaybeExcludedMedia.Included>,
        mergedPreference: MediaPreference,
    ): Media? {
        val availableAlliances = alliance.available.first()
        val context = mediaSelectorContext.filter { it.allFieldsLoaded() }.first()
        val settings = mediaSelectorSettings.first()
        return findMediaByPreference(candidates, mergedPreference, availableAlliances, context, settings)
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
        candidateSources: List<String>,
        overrideUserSelection: Boolean,
        blacklistMediaIds: Set<String>,
        allowNonPreferred: Boolean,
        candidateMediaFilter: ((MaybeExcludedMedia.Included) -> Boolean)?
    ): Media? {
        if (candidateSources.isEmpty()) return null

        fun bake(candidates: List<MaybeExcludedMedia.Included>): List<MaybeExcludedMedia.Included> {
            return candidates.filter {
                it.result.mediaSourceId in candidateSources && it.result.mediaId !in blacklistMediaIds
                        && (candidateMediaFilter == null || candidateMediaFilter(it))
            }
                .sortedBy { candidateSources.indexOf(it.result.mediaSourceId) }
        }

        val selected = run {
            val mergedPreference = newPreferences.first()
            // 只取一次 filteredCandidates 快照, 偏好列表从同一份快照现算.
            // 若改为读 preferredCandidates (另一个 shareIn), 它可能比 filteredCandidates 落后一次更新,
            // 导致偏好轮看到旧列表落空, 而非偏好轮在新列表里选出不符合用户偏好的资源.
            val filtered = filteredCandidates.first()
            val preferred = algorithm.filterByPreference(filtered, mergedPreference)

            findUsingPreferenceFromCandidates(
                bake(preferred.filterIsInstance<MaybeExcludedMedia.Included>()),
                mergedPreference.copy(alliance = ANY_FILTER),
            )?.let { return@run it } // 先考虑用户偏好

            if (allowNonPreferred) {
                // 如果用户偏好里面没有, 并且允许选择非偏好的, 才考虑全部列表
                findUsingPreferenceFromCandidates(
                    bake(filtered.filterIsInstance<MaybeExcludedMedia.Included>()),
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
            withContext(flowCoroutineContext) {
                overridePreference.value = OptionalPreference.preferNoValue()
                broadcastChangePreference(null)
            }
        }

        override fun preferWithoutBroadcast(value: T) {
            overridePreference.value = OptionalPreference.prefer(value)
        }

        override suspend fun prefer(value: T) {
            withContext(flowCoroutineContext) {
                preferWithoutBroadcast(value)
                broadcastChangePreference(null)
            }
        }

        override fun toString(): String = "MediaPreferenceItem($debugName)"
    }
}
