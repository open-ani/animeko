/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.mediaselect.manual

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import me.him188.ani.app.data.repository.media.ManualBrowseMemory
import me.him188.ani.app.domain.mediasource.instance.MediaSourceInstance
import me.him188.ani.app.domain.mediasource.web.BlockReason
import me.him188.ani.app.domain.mediasource.web.BlockedException
import me.him188.ani.app.domain.mediasource.web.captcha.SolveOutcome
import me.him188.ani.app.domain.mediasource.web.captcha.WebSessionManager
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.source.BrowseChannel
import me.him188.ani.datasources.api.source.BrowseEpisode
import me.him188.ani.datasources.api.source.BrowseSubject
import me.him188.ani.datasources.api.source.MediaSourceInfo
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn

/**
 * 当前条目与剧集 (VM 从 SubjectEpisodeInfoBundle 派生); null 表示会话未就绪, 页面禁用播放按钮.
 */
@Immutable
data class ManualBrowseTarget(
    val subjectId: Int,
    /**
     * 默认搜索关键字 = subjectInfo.nameCnOrName.
     */
    val subjectName: String,
    /**
     * 传给 createMedia 的集号 = episodeInfo.sort; 也是记忆的 playedAsSort.
     */
    val episodeSort: EpisodeSort,
    /**
     * "25", 供「作为第 25 话播放」.
     */
    val episodeSortText: String,
)

@Immutable
data class ManualBrowseSource(
    val instanceId: String,
    val mediaSourceId: String,
    val info: MediaSourceInfo,
)

sealed interface ManualLoadState<out T> {
    data object Idle : ManualLoadState<Nothing>
    data object Loading : ManualLoadState<Nothing>
    data class Success<T>(val value: T) : ManualLoadState<T>

    /**
     * 限流、网络错误、验证码交互解决一次仍失败都归此; 页面显示错误文本 + 重试.
     * [captchaUnsupported] = 被验证码挡且 WebSessionManager.isInteractiveSupported == false (iOS):
     * 文案 media_selector_manual_captcha_unsupported, 无重试按钮.
     */
    data class Failed(val error: Throwable, val captchaUnsupported: Boolean = false) : ManualLoadState<Nothing>
}

@Immutable
data class ManualBrowsePresentation(
    /**
     * isEnabled && supportsBrowsing 的实例, allInstances 顺序.
     */
    val sources: List<ManualBrowseSource>,
    /**
     * 生效的选中源 instanceId: 用户显式选择 (仍在 sources 中) → 记忆源 / 偏好 Web 源 → 第一个; 无源时 null.
     */
    val selectedSourceId: String?,
    /**
     * 生效的关键字: 用户输入 → target.subjectName → "".
     */
    val keyword: String,
    /**
     * 当前源的搜索结果. 结果按源缓存, 换源显示各自的结果; 没搜过的源为 Idle.
     */
    val results: ManualLoadState<List<BrowseSubject>>,
    /**
     * 非 null = 第二页 (线路与剧集).
     */
    val openedSubject: BrowseSubject?,
    val channels: ManualLoadState<List<BrowseChannel>>,
    /**
     * 线路单选用下标 (BrowseChannel.name 可重复可 null).
     */
    val selectedChannelIndex: Int,
    /**
     * 用户显式选择 → 否则线路里第一个 episodeSort == target.episodeSort 的下标 → 否则 null.
     */
    val selectedEpisodeIndex: Int?,
    /**
     * 确认对话框对应的剧集下标; null = 对话框关闭. 只由用户点击网格 ([ManualBrowseState.selectEpisode]) 置为该下标, 自动预选不打开对话框;
     * 非 null 时恒等于 [selectedEpisodeIndex]. 关闭对话框 ([ManualBrowseState.dismissConfirm]) 与播放成功都清空它.
     */
    val confirmingEpisodeIndex: Int?,
    val target: ManualBrowseTarget?,
    /**
     * createMedia → onPlay 进行中, 按钮禁用.
     */
    val isPlaying: Boolean,
    /**
     * 组合流尚未发射时的占位值 ([Empty]); 页面据此不把空的 [sources] 当成「没有支持浏览的数据源」.
     */
    val isPlaceholder: Boolean = false,
) {
    val selectedSource: ManualBrowseSource? get() = sources.firstOrNull { it.instanceId == selectedSourceId }
    val selectedChannel: BrowseChannel?
        get() = (channels as? ManualLoadState.Success)?.value?.getOrNull(selectedChannelIndex)
    val selectedEpisode: BrowseEpisode? get() = selectedEpisodeIndex?.let { selectedChannel?.episodes?.getOrNull(it) }

    /**
     * 「下一话播放 X」的 X; 没有下一项为 null.
     */
    val nextEpisodeName: String?
        get() = selectedEpisodeIndex?.let { selectedChannel?.episodes?.getOrNull(it + 1)?.name }

    companion object {
        val Empty: ManualBrowsePresentation = ManualBrowsePresentation(
            sources = emptyList(),
            selectedSourceId = null,
            keyword = "",
            results = ManualLoadState.Idle,
            openedSubject = null,
            channels = ManualLoadState.Idle,
            selectedChannelIndex = 0,
            selectedEpisodeIndex = null,
            confirmingEpisodeIndex = null,
            target = null,
            isPlaying = false,
            isPlaceholder = true,
        )
    }
}

/**
 * 手动查找状态. 独立于 MediaSelectorState, 在 EpisodeViewModel 构造一次并持有, 搜索结果与选择在会话内保持.
 *
 * 内部约定 (实现者必须遵守, 测试依赖它):
 * - 组合流 [presentationSource] (combine(browsableSources, target, preferredSourceId, 内部 MutableStateFlow…), 未 stateIn) 是唯一真值;
 *   [presentationFlow] = `presentationSource.stateIn(backgroundScope, WhileSubscribed(5_000), Empty)` 只供 UI 订阅.
 * - 所有动作 (selectSource / searchIfNeeded / search / openSubject / retry / selectChannel / selectEpisode / play) 读取生效源、生效关键字、预选剧集一律 `presentationSource.first()`;
 *   禁止读 `presentationFlow.value` —— `StateFlow.value` / `first()` 不会拉起 `WhileSubscribed` 的上游, 无 UI 订阅时 (单测、页面首帧) 恒为 Empty, search() 会因 selectedSourceId == null 无操作.
 *   `first()` 会拉起 `browsableSources` (`allInstances` 是 shareIn(Lazily, replay 1), 立即有值) 与 `preferredSourceId` (DataStore / Room 读一次), 延迟可接受.
 *
 * @param browsableSources 已过滤的实例列表: `allInstances.map { it.filter { i -> i.isEnabled && i.source.supportsBrowsing } }`, 保持 allInstances 顺序.
 *        每次请求前 `first()` 取当前实例, 不缓存 MediaSource.
 * @param webSessionManager 搜索 / 浏览抛 `BlockedException(reason is BlockReason.Captcha)` 时:
 *        `isInteractiveSupported == false` → Failed(captchaUnsupported = true); 否则 `solve(e.request, interactive = true)` 为 Solved 则重试一次同一请求, 否则 Failed.
 * @param target 当前集; VM 从 `episodeSessionFlow.flatMapLatest { it.infoBundleFlow }` 派生.
 * @param preferredSourceId 默认源的 mediaSourceId: `combine(memoryRepo.flow(subjectId), getPreferredWebMediaSource(subjectId)) { m, p -> m?.mediaSourceId ?: p }`.
 * @param onPlay 把 media 交给当前会话的 MediaSelector: memory != null → `select` 并写记忆; null → `selectTemporarily`. 在 [backgroundScope] 内执行, 不随 UI 作用域取消.
 */
@Stable
class ManualBrowseState(
    browsableSources: Flow<List<MediaSourceInstance>>,
    private val webSessionManager: WebSessionManager,
    target: Flow<ManualBrowseTarget?>,
    preferredSourceId: Flow<String?>,
    private val onPlay: suspend (media: Media, memory: ManualBrowseMemory?) -> Unit,
    private val backgroundScope: CoroutineScope,
) {
    private val browsableSources: Flow<List<MediaSourceInstance>> = browsableSources
    private val target: Flow<ManualBrowseTarget?> = target
    private val preferredSourceId: Flow<String?> = preferredSourceId

    /**
     * 一个源最近一次搜索. [keyword] 是发起时的生效关键字 (已 trim); [generation] 取自 [Session.searchGeneration].
     */
    private data class SourceSearch(
        val keyword: String,
        val results: ManualLoadState<List<BrowseSubject>>,
        val generation: Int,
    )

    /**
     * 会话内由用户动作产生的状态, 整体原子更新.
     *
     * [searchGeneration] 每次发起搜索时递增, 用来给请求编号; 搜索协程只在 [searches] 里该源的记录仍是自己的代数时写回结果,
     * 所以同一个源上被新搜索取代的旧请求即使拿到结果也不会覆盖. 旧请求不取消, 只丢弃结果.
     * [browseGeneration] 在每次发起、取消或重置浏览时递增, 规则相同, 但浏览只有一个进行中的请求, 旧的会被取消.
     */
    private data class Session(
        val explicitSourceId: String? = null,
        /**
         * null = 用户未输入, 生效关键字取 target.subjectName.
         */
        val keywordInput: String? = null,
        /**
         * instanceId → 该源最近一次搜索. 换源不清空, 切回来直接显示.
         */
        val searches: Map<String, SourceSearch> = emptyMap(),
        val openedSubject: BrowseSubject? = null,
        val channels: ManualLoadState<List<BrowseChannel>> = ManualLoadState.Idle,
        val channelIndex: Int = 0,
        val userEpisodeIndex: Int? = null,
        /**
         * 见 [ManualBrowsePresentation.confirmingEpisodeIndex]; 与 [userEpisodeIndex] 一起被清空.
         */
        val confirmingEpisodeIndex: Int? = null,
        val isPlaying: Boolean = false,
        val searchGeneration: Int = 0,
        val browseGeneration: Int = 0,
    )

    private val session = MutableStateFlow(Session())

    private var browseJob: Job? = null
    private var playJob: Deferred<Unit>? = null

    /**
     * 从 [play] 开始直到 [playJob] 完成都持有, 保证同一时刻只有一次 onPlay.
     */
    private val playGate = Mutex()

    private val presentationSource: Flow<ManualBrowsePresentation> = combine(
        this.browsableSources,
        this.target,
        this.preferredSourceId,
        session,
    ) { instances, target, preferredId, session ->
        val sources = instances.map { ManualBrowseSource(it.instanceId, it.mediaSourceId, it.source.info) }
        val selectedSourceId = session.explicitSourceId?.takeIf { id -> sources.any { it.instanceId == id } }
            ?: sources.firstOrNull { it.mediaSourceId == preferredId }?.instanceId
            ?: sources.firstOrNull()?.instanceId
        val channel = (session.channels as? ManualLoadState.Success)?.value?.getOrNull(session.channelIndex)
        val selectedEpisodeIndex = session.userEpisodeIndex
            ?.takeIf { channel != null && it in channel.episodes.indices }
            ?: channel?.let { c ->
                target?.let { t ->
                    c.episodes.indexOfFirst { it.episodeSort != null && it.episodeSort == t.episodeSort }.takeIf { it >= 0 }
                }
            }
        val confirmingEpisodeIndex = session.confirmingEpisodeIndex
            ?.takeIf { channel != null && it in channel.episodes.indices }
        ManualBrowsePresentation(
            sources = sources,
            selectedSourceId = selectedSourceId,
            keyword = session.keywordInput ?: target?.subjectName ?: "",
            results = selectedSourceId?.let { session.searches[it]?.results } ?: ManualLoadState.Idle,
            openedSubject = session.openedSubject,
            channels = session.channels,
            selectedChannelIndex = session.channelIndex,
            selectedEpisodeIndex = selectedEpisodeIndex,
            confirmingEpisodeIndex = confirmingEpisodeIndex,
            target = target,
            isPlaying = session.isPlaying,
        )
    }

    val presentationFlow: StateFlow<ManualBrowsePresentation> = presentationSource.stateIn(
        backgroundScope,
        SharingStarted.WhileSubscribed(5_000),
        ManualBrowsePresentation.Empty,
    )

    /**
     * 换源. 打开的条目属于原来的源, 一并关闭; 新源随后 [searchIfNeeded].
     * 原来的源正在进行的搜索不取消: 结果照常写进它的缓存, 切回来直接可用.
     */
    fun selectSource(instanceId: String) {
        browseJob?.cancel()
        session.update {
            it.copy(
                explicitSourceId = instanceId,
                openedSubject = null,
                channels = ManualLoadState.Idle,
                channelIndex = 0,
                userEpisodeIndex = null,
                confirmingEpisodeIndex = null,
                browseGeneration = it.browseGeneration + 1,
            )
        }
        searchIfNeeded()
    }

    fun setKeyword(keyword: String) {
        session.update { it.copy(keywordInput = keyword) }
    }

    /**
     * 当前源还没有用当前关键字搜过 (进行中、成功、失败都算搜过) 时搜索一次, 否则什么都不做. 页面打开、生效源变化与 [selectSource] 时调用.
     * 失败也算搜过: 被验证码挡住的源若每次切回来都重搜, 会反复弹验证; 由用户点重试.
     */
    fun searchIfNeeded() {
        launchSearch(force = false)
    }

    /**
     * 用户提交: 总是重新搜索当前源, 同一关键字也搜 (当作刷新). 空关键字不搜; results = Loading → Success / Failed; 不做匹配过滤.
     */
    fun search() {
        launchSearch(force = true)
    }

    private fun launchSearch(force: Boolean) {
        val generation = session.updateAndGet { it.copy(searchGeneration = it.searchGeneration + 1) }.searchGeneration
        backgroundScope.launch {
            val presentation = presentationSource.first()
            val keyword = presentation.keyword.trim()
            val sourceId = presentation.selectedSourceId
            if (keyword.isEmpty() || sourceId == null) return@launch
            val instance = findInstance(sourceId) ?: return@launch
            // 判断与占位在同一次原子更新里完成: 页面打开与换源会同时触发 searchIfNeeded, 只能有一次真正发出请求.
            var started = false
            session.update { s ->
                val existing = s.searches[sourceId]
                started = when {
                    existing != null && existing.generation > generation -> false // 这个源上已有更新的搜索
                    !force && existing != null && existing.keyword == keyword -> false
                    else -> true
                }
                if (started) {
                    s.copy(searches = s.searches + (sourceId to SourceSearch(keyword, ManualLoadState.Loading, generation)))
                } else {
                    s
                }
            }
            if (!started) return@launch
            val result = runBrowse { instance.source.searchSubjects(keyword) }
            session.update { s ->
                if (s.searches[sourceId]?.generation == generation) {
                    s.copy(searches = s.searches + (sourceId to SourceSearch(keyword, result, generation)))
                } else {
                    s
                }
            }
        }
    }

    /**
     * 取消上一次浏览; openedSubject = subject; channels = Loading → browseSubject; 成功后 selectedChannelIndex = 0, 用户剧集选择清空.
     */
    fun openSubject(subject: BrowseSubject) {
        browseJob?.cancel()
        val generation = session.updateAndGet {
            it.copy(
                openedSubject = subject,
                channels = ManualLoadState.Loading,
                channelIndex = 0,
                userEpisodeIndex = null,
                confirmingEpisodeIndex = null,
                browseGeneration = it.browseGeneration + 1,
            )
        }.browseGeneration
        browseJob = backgroundScope.launch {
            val presentation = presentationSource.first()
            val instance = presentation.selectedSourceId?.let { findInstance(it) }
            val result = if (instance == null) {
                ManualLoadState.Failed(IllegalStateException("No browsable source selected"))
            } else {
                runBrowse { instance.source.browseSubject(subject) }
            }
            session.update { s ->
                if (s.browseGeneration == generation) {
                    s.copy(channels = result, channelIndex = 0, userEpisodeIndex = null, confirmingEpisodeIndex = null)
                } else {
                    s
                }
            }
        }
    }

    /**
     * 回第一页, 保留 results.
     */
    fun closeSubject() {
        browseJob?.cancel()
        session.update {
            it.copy(
                openedSubject = null,
                channels = ManualLoadState.Idle,
                channelIndex = 0,
                userEpisodeIndex = null,
                confirmingEpisodeIndex = null,
                browseGeneration = it.browseGeneration + 1,
            )
        }
    }

    /**
     * openedSubject != null 且 channels 失败 → 重新 openSubject; 否则重新 search.
     */
    fun retry() {
        val current = session.value
        val opened = current.openedSubject
        if (opened != null && current.channels is ManualLoadState.Failed) {
            openSubject(opened)
        } else {
            search()
        }
    }

    /**
     * 清空用户剧集选择, 关闭确认对话框.
     */
    fun selectChannel(index: Int) {
        session.update { it.copy(channelIndex = index, userEpisodeIndex = null, confirmingEpisodeIndex = null) }
    }

    /**
     * 用户点击网格里的一项: 选中它并打开确认对话框.
     */
    fun selectEpisode(index: Int) {
        session.update { it.copy(userEpisodeIndex = index, confirmingEpisodeIndex = index) }
    }

    /**
     * 关闭确认对话框 (点 scrim / 返回键), 保留剧集选中.
     */
    fun dismissConfirm() {
        session.update { it.copy(confirmingEpisodeIndex = null) }
    }

    /**
     * 播放并记住 / 仅临时播放. 要求 selectedSource、openedSubject、selectedChannel、selectedEpisode、target 均非 null, 否则返回 false.
     * media = source.createMedia(subject, channel.name, episode, target.episodeSort) (两种情况都传当前集 sort); source 从 browsableSources.first() 按 instanceId 取.
     * remember = true 时 memory = ManualBrowseMemory(source.mediaSourceId, subject, selectedChannelIndex, channel.name, selectedEpisodeIndex,
     *   episode.episodeSort?.takeUnless { it is EpisodeSort.Unknown }, playedAsSort = target.episodeSort); 否则 null.
     * 并发: 已有进行中的播放 (`playJob?.isActive == true` 或拿不到 [playGate]) 时立即返回 null, 本次调用被忽略, 不是失败
     *   (页面作用域被取消后再次点击不能并发两次 select; isPlaying 经 stateIn 到达按钮前的快速双击也落在这里, 页面对 null 不提示).
     *   拿到 gate 后立即 isPlaying = true, 缩短按钮仍可点的窗口; `playJob = backgroundScope.async { onPlay(media, memory) }`,
     *   isPlaying 由 playJob 的 invokeOnCompletion 置 false (成功时同一次更新清空 confirmingEpisodeIndex), 未 launch 就返回的路径由 finally 置 false.
     *   `playJob.await()` 成功返回 true 并关闭确认对话框 (页面随后调用 onPlayed 关闭容器), 任何异常返回 false 并保持在当前页, 对话框保持打开.
     */
    suspend fun play(remember: Boolean): Boolean? {
        if (playJob?.isActive == true || !playGate.tryLock()) return null
        var launched = false
        session.update { it.copy(isPlaying = true) }
        try {
            val presentation = presentationSource.first()
            val source = presentation.selectedSource ?: return false
            val subject = presentation.openedSubject ?: return false
            val channel = presentation.selectedChannel ?: return false
            val episodeIndex = presentation.selectedEpisodeIndex ?: return false
            val episode = presentation.selectedEpisode ?: return false
            val target = presentation.target ?: return false
            val instance = findInstance(source.instanceId) ?: return false
            val media = try {
                instance.source.createMedia(subject, channel.name, episode, target.episodeSort)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.warn(e) { "ManualBrowseState: createMedia failed for ${episode.url}" }
                return false
            }
            val memory = if (remember) {
                ManualBrowseMemory(
                    mediaSourceId = instance.mediaSourceId,
                    subject = subject,
                    channelIndex = presentation.selectedChannelIndex,
                    channelName = channel.name,
                    episodeIndex = episodeIndex,
                    episodeSort = episode.episodeSort?.takeUnless { it is EpisodeSort.Unknown },
                    playedAsSort = target.episodeSort,
                )
            } else {
                null
            }
            val job = backgroundScope.async { onPlay(media, memory) }
            playJob = job
            launched = true
            job.invokeOnCompletion { cause ->
                // 成功时在同一次更新里关闭确认对话框, UI 不会看到「已完成但对话框仍开」的中间帧
                session.update {
                    it.copy(
                        isPlaying = false,
                        confirmingEpisodeIndex = if (cause == null) null else it.confirmingEpisodeIndex,
                    )
                }
                playGate.unlock()
            }
            return try {
                job.await()
                true
            } catch (e: CancellationException) {
                // 调用方自己被取消则继续传播; 只有 onPlay 所在的后台任务被取消才算失败
                currentCoroutineContext().ensureActive()
                false
            } catch (e: Exception) {
                logger.warn(e) { "ManualBrowseState: onPlay failed" }
                false
            }
        } finally {
            if (!launched) {
                session.update { it.copy(isPlaying = false) }
                playGate.unlock()
            }
        }
    }

    private suspend fun findInstance(instanceId: String): MediaSourceInstance? =
        browsableSources.first().firstOrNull { it.instanceId == instanceId }

    /**
     * 统一的请求异常处理: 验证码按 [webSessionManager] 的交互能力决定是提示不支持还是交互解决后重试一次; 其余异常直接失败.
     */
    private suspend fun <T> runBrowse(request: suspend () -> T): ManualLoadState<T> {
        val blocked = try {
            return ManualLoadState.Success(request())
        } catch (e: CancellationException) {
            throw e
        } catch (e: BlockedException) {
            e
        } catch (e: Exception) {
            logger.warn(e) { "ManualBrowseState: request failed" }
            return ManualLoadState.Failed(e)
        }
        if (blocked.reason !is BlockReason.Captcha) return ManualLoadState.Failed(blocked)
        if (!webSessionManager.isInteractiveSupported) return ManualLoadState.Failed(blocked, captchaUnsupported = true)
        if (webSessionManager.solve(blocked.request, interactive = true) != SolveOutcome.Solved) {
            return ManualLoadState.Failed(blocked)
        }
        return try {
            ManualLoadState.Success(request())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn(e) { "ManualBrowseState: request failed after solving captcha" }
            ManualLoadState.Failed(e)
        }
    }

    private companion object {
        private val logger = logger<ManualBrowseState>()
    }
}
