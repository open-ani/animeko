/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.selector

import androidx.compose.ui.util.fastFilter
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
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.source.MediaSourceLocation
import me.him188.ani.datasources.api.topic.hasSeason
import me.him188.ani.datasources.api.topic.isSingleEpisode
import kotlin.coroutines.CoroutineContext

/**
 * 数据源选择器.
 *
 * 从 [mediaList] 中, 根据用户默认偏好和本次选择的即时偏好, 过滤出 [filteredCandidates].
 * 支持默认选择 [trySelectDefault], [trySelectCached].
 * 用户操作的 [select] 将覆盖默认的选项.
 *
 * 对于选项的更新, 将会触发事件 [events].
 *
 * @see MediaSelectorFactory
 */
interface MediaSelector {
    /**
     * 搜索到的全部的列表, 经过了设置 [MediaSelectorSettings] 筛选.
     */
    val mediaList: Flow<List<Media>>

    val alliance: MediaPreferenceItem<String>
    val resolution: MediaPreferenceItem<String>
    val subtitleLanguageId: MediaPreferenceItem<String>
    val mediaSourceId: MediaPreferenceItem<String>

    /**
     * 经过 [alliance], [resolution] 等[偏好][MediaPreference]筛选后的列表.
     */
    val filteredCandidates: Flow<List<Media>>

    /**
     * 目前选中的项目. 它不一定是 [filteredCandidates] 中的一个项目.
     */
    val selected: StateFlow<Media?>

    /**
     * 用于监听 [select] 等事件
     * @see eventHandling
     */
    val events: MediaSelectorEvents

    /**
     * 选择一个 [Media]. 该 [Media] 可以是位于 [filteredCandidates] 中的, 也可以不是.
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
     * 这会忽略一切用户偏好设置, 只根据数据源顺序选择. 将会选择在 list 中 index 越小的数据源.
     *
     * @param overrideUserSelection 是否覆盖用户选择.
     * 若为 `true`, 则会忽略用户目前的选择, 使用此函数的结果替换选择.
     * 若为 `false`, 如果用户已经选择了一个 media, 则此函数不会做任何事情.
     * @param blacklistMediaIds 黑名单, 这些 media 不会被选择. 如果遇到黑名单中的 media, 将会跳过.
     *
     * @return 成功选择且已经记录的 [Media]. 返回 `null` 时表示没有选择.
     */
    suspend fun trySelectFromMediaSources(
        mediaSourceOrder: List<String>,
        overrideUserSelection: Boolean = false,
        blacklistMediaIds: Set<String> = emptySet(),
    ): Media?

    /**
     * 尝试选择缓存 ([MediaSourceKind.LocalCache]) 作为默认选择, 如果没有缓存则不做任何事情
     * @return 成功选择且已经记录的缓存, 若没有缓存或用户已经手动选择了一个则返回 `null`
     * @see autoSelect
     */
    suspend fun trySelectCached(): Media?

    /**
     * 逐渐取消选择, 直到 [filteredCandidates] 有至少一个元素.
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
     * 用户的选择, 可能为空
     */
    val userSelected: Flow<OptionalPreference<T>>

    /**
     * 默认的选择, 为空表示没有默认的选择
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
) : MediaSelector {
    companion object {
        private fun isLocalCache(it: Media) = it.kind == MediaSourceKind.LocalCache

        fun filterCandidates(
            mediaList: List<Media>,
            mergedPreferences: MediaPreference,
        ): List<Media> {
            infix fun <Pref : Any> Pref?.matches(prop: Pref): Boolean = this == null || this == prop
            infix fun <Pref : Any> Pref?.matches(prop: List<Pref>): Boolean = this == null || this in prop

            /**
             * 当 [it] 满足当前筛选条件时返回 `true`.
             */
            fun filterCandidate(it: Media): Boolean {
                if (isLocalCache(it)) {
                    return true // always show local, so that [makeDefaultSelection] will select a local one
                }

                return mergedPreferences.alliance matches it.properties.alliance &&
                        mergedPreferences.resolution matches it.properties.resolution &&
                        mergedPreferences.subtitleLanguageId matches it.properties.subtitleLanguageIds &&
                        mergedPreferences.mediaSourceId matches it.mediaSourceId
            }

            return mediaList.filter {
                filterCandidate(it)
            }
        }
    }

    private fun <T> Flow<T>.cached(): Flow<T> {
        if (!enableCaching) return this
        return this.shareIn(CoroutineScope(flowCoroutineContext), SharingStarted.WhileSubscribed(), replay = 1)
    }

    private val mediaSelectorSettings = mediaSelectorSettings.cached()
    private val mediaSelectorContext = mediaSelectorContextNotCached.cached()

    override val mediaList: Flow<List<Media>> = combine(
        mediaListNotCached.cached(), // cache 是必要的, 当 newPreferences 变更的时候不能重新加载 media list (网络)
        savedDefaultPreference, // 只需要使用 default, 因为目前不能覆盖生肉设置
        // 如果依赖 merged pref, 会产生循环依赖 (mediaList -> mediaPreferenceItem -> newPreferences -> mediaList)
        this.mediaSelectorSettings,
        this.mediaSelectorContext,
    ) { list, pref, settings, context ->
        filterMediaList(pref, settings, context, list)
            .sortedWith(
                compareByDescending<Media> { media ->
                    val subtitleKind = media.properties.subtitleKind
                    if (context.subtitlePreferences != null && subtitleKind != null) {
                        if (context.subtitlePreferences[subtitleKind] == SubtitleKindPreference.LOW_PRIORITY) {
                            return@compareByDescending 0
                        }
                    }
                    1
                }.then(compareBy { it.costForDownload })
                    .thenByDescending { it.publishedTime },
            )
    }.flowOn(flowCoroutineContext)

    /**
     * 过滤掉 [MediaSelectorSettings] 指定的内容. 例如过滤生肉, 对于完结番过滤掉单集
     */
    private fun filterMediaList(
        preference: MediaPreference,
        settings: MediaSelectorSettings,
        context: MediaSelectorContext,
        list: List<Media>
    ): List<Media> {
        return list.fastFilter filter@{ media ->
            if (isLocalCache(media)) return@filter true // 本地缓存总是要显示

            if (settings.hideSingleEpisodeForCompleted
                && context.subjectFinished == true // 还未加载到剧集信息时, 先显示
                && media.kind == MediaSourceKind.BitTorrent
            ) {
                // 完结番隐藏单集资源
                val range = media.episodeRange ?: return@filter false
                if (range.isSingleEpisode()) return@filter false
            }

            if (!preference.showWithoutSubtitle &&
                (media.properties.subtitleLanguageIds.isEmpty() && media.extraFiles.subtitles.isEmpty())
            ) {
                // 不显示无字幕的
                return@filter false
            }

            val subtitleKind = media.properties.subtitleKind
            if (context.subtitlePreferences != null && subtitleKind != null) {
                if (context.subtitlePreferences[subtitleKind] == SubtitleKindPreference.HIDE) {
                    return@filter false
                }
            }

            true
        }

    }

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
    private val filteredCandidatesNotCached = combine(this.mediaList, newPreferences) { mediaList, mergedPreferences ->
        filterCandidates(mediaList, mergedPreferences)
    }

    override val filteredCandidates: Flow<List<Media>> = filteredCandidatesNotCached.cached()

    override val selected: MutableStateFlow<Media?> = MutableStateFlow(null)
    override val events = MutableMediaSelectorEvents()

    override suspend fun select(candidate: Media): Boolean {
        return selectImpl(candidate, updatePreference = true)
    }

    private suspend fun selectImpl(candidate: Media, updatePreference: Boolean): Boolean {
        events.onBeforeSelect.emit(SelectEvent(candidate, null))
        if (selected.value == candidate) return false
        selected.value = candidate

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


    override suspend fun trySelectDefault(): Media? {
        if (selected.value != null) return null

        val mergedPreference = newPreferences.first()
        val selectedSubtitleLanguageId = mergedPreference.subtitleLanguageId
        val selectedResolution = mergedPreference.resolution
        val selectedAlliance = mergedPreference.alliance
        val selectedMediaSource = mergedPreference.mediaSourceId
        val allianceRegexes = mergedPreference.alliancePatterns.orEmpty().map { it.toRegex() }
        val availableAlliances = alliance.available.first()

        val candidates = filteredCandidates.first()
        if (candidates.isEmpty()) return null

        val mediaSelectorContext = mediaSelectorContext.filter {
            it.allFieldsLoaded()
        }.first()
        val mediaSelectorSettings = mediaSelectorSettings.first()


        val shouldPreferSeasons = mediaSelectorContext.subjectFinished == true
                && mediaSelectorSettings.preferSeasons

        val preferKind = mediaSelectorSettings.preferKind

        val anyFilter = "*"

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
                yield("*")
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
                return selectDefault(
                    list.fastFirstOrNull { it.episodeRange?.hasSeason() == null }
                        ?: list.first(),
                )
            }
            return selectDefault(list.first())
        }

        // TODO: too complex, should refactor

        fun selectImpl(candidates: List<Media>): Media? {
            for (resolution in resolutions) { // DFS 尽可能匹配第一个分辨率
                val filteredByResolution =
                    if (resolution == anyFilter) candidates
                    else candidates.filter { resolution == it.properties.resolution }
                if (filteredByResolution.isEmpty()) continue

                for (languageId in languageIds) {
                    val filteredByLanguage =
                        if (languageId == anyFilter) filteredByResolution
                        else filteredByResolution.filter { languageId in it.properties.subtitleLanguageIds }
                    if (filteredByLanguage.isEmpty()) continue

                    for (alliance in alliances) { // 能匹配第一个最好
                        // 这里是消耗最大的地方, 因为有正则匹配
                        val filteredByAlliance =
                            if (alliance == anyFilter) filteredByLanguage
                            else filteredByLanguage.filter { alliance == it.properties.alliance }
                        if (filteredByAlliance.isEmpty()) continue

                        for (mediaSource in mediaSources) {
                            val filteredByMediaSource =
                                if (mediaSource == anyFilter) filteredByAlliance
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
                            if (mediaSource == anyFilter) filteredByLanguage
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

        if (preferKind != null) {
            val web = candidates.filter { it.kind == preferKind }
            selectImpl(web)?.let {
                return it
            }
        }

        if (shouldPreferSeasons) {
            val seasons = candidates.filter { it.episodeRange?.hasSeason() == true }
            selectImpl(seasons)?.let {
                return it
            }
        }
        selectImpl(candidates)?.let { return it }

        return selectAny(candidates)
    }

    override suspend fun trySelectFromMediaSources(
        mediaSourceOrder: List<String>,
        overrideUserSelection: Boolean,
        blacklistMediaIds: Set<String>
    ): Media? {
        if (mediaSourceOrder.isEmpty()) return null

        val candidates = mediaList.first()
        if (candidates.isEmpty()) return null

        val selected = mediaSourceOrder.firstNotNullOfOrNull { mediaSourceId ->
            candidates.fastFirstOrNull { it.mediaSourceId == mediaSourceId && it.mediaId !in blacklistMediaIds }
        }
        return selected?.let {
            if (overrideUserSelection) {
                selectImpl(it, updatePreference = false)
                it
            } else {
                selectDefault(it)
            }
        }
    }

    override suspend fun trySelectCached(): Media? {
        if (selected.value != null) return null
        val candidates = filteredCandidates.first()
        val cached = candidates.fastFirstOrNull { isLocalCache(it) } ?: return null
        return selectDefault(cached)
    }

    override suspend fun removePreferencesUntilFirstCandidate() {
        if (filteredCandidates.first().isNotEmpty()) return
        alliance.removePreference()
        if (filteredCandidatesNotCached.first().isNotEmpty()) return
        resolution.removePreference()
        if (filteredCandidatesNotCached.first().isNotEmpty()) return
        subtitleLanguageId.removePreference()
        if (filteredCandidatesNotCached.first().isNotEmpty()) return
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
        override val available: Flow<List<T>> = mediaList.map { list ->
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

private val Media.costForDownload
    get() = when (location) {
        MediaSourceLocation.Local -> 0
        MediaSourceLocation.Lan -> 1
        else -> 2
    }
