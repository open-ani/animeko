/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.selector

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.app.domain.media.selector.testFramework.FetchMediaSelectorTestSuite
import me.him188.ani.app.domain.media.selector.testFramework.MediaSelectorTestSuite.Companion.DEFAULT_PREFERENCE
import me.him188.ani.app.domain.media.selector.testFramework.MediaSelectorTestSuite.Companion.SOURCE_DMHY
import me.him188.ani.app.domain.media.selector.testFramework.RecordedMediaSelectorEvent.OnBeforeSelect
import me.him188.ani.app.domain.media.selector.testFramework.RecordedMediaSelectorEvent.OnChangePreference
import me.him188.ani.app.domain.media.selector.testFramework.RecordedMediaSelectorEvent.OnPreferWebSource
import me.him188.ani.app.domain.media.selector.testFramework.RecordedMediaSelectorEvent.OnSelect
import me.him188.ani.app.domain.media.selector.testFramework.SimpleMediaSelectorTestSuite
import me.him188.ani.app.domain.media.selector.testFramework.collectEvents
import me.him188.ani.app.domain.media.selector.testFramework.runFetchMediaSelectorTestSuite
import me.him188.ani.app.domain.media.selector.testFramework.runSimpleMediaSelectorTestSuite
import me.him188.ani.app.domain.media.selector.testFramework.tier
import me.him188.ani.app.domain.mediasource.GetMediaSelectorSourceTiersUseCase
import me.him188.ani.app.domain.mediasource.GetPreferredWebMediaSourceUseCase
import me.him188.ani.app.domain.settings.GetMediaSelectorSettingsFlowUseCase
import me.him188.ani.datasources.api.source.MediaSourceKind.WEB
import me.him188.ani.test.DisabledOnNative
import org.koin.core.Koin
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * INFRA-01: `enableCaching = true` (生产默认, 见 `DefaultMediaSelector` 构造器) 的
 * `shareIn(WhileSubscribed(5s), replay = 1)` 缓存路径.
 *
 * ## 本文件的组织方式: 双跑
 *
 * 方案 §1.1#12 要求的是"既有用例在 `enableCaching = true/false` 两种配置下各跑一遍",
 * 而不是"另写一批 caching-only 用例". 因此本文件把关键场景抽成 `check*` 函数,
 * 由两个 `@Test` 分别以 `cachingEnabled = false` / `true` 驱动**同一段断言**.
 * 双跑钉住的命题是: **caching 只改变计算时机, 不改变终值语义**.
 *
 * ## 时序断言禁令
 *
 * caching 夹具只保证终值语义等价, **不保证时序等价**, 因此双跑用例里不写任何时序断言.
 * 唯一一条与时序有关的用例是 [`INFRA-01 缓存路径下上游变更在调度器被推进前仍回放旧快照`],
 * 它单跑 `true` 并已在注释里说明为什么它不是"要保住的行为".
 * 夹具与生产的差异见 [SimpleMediaSelectorTestSuite] 的 `cachingEnabled` KDoc.
 *
 * @see DefaultMediaSelector
 */
@DisabledOnNative // TODO: ContextParameters crashes on Native
class MediaSelectorCachingEnabledTest {
    ///////////////////////////////////////////////////////////////////////////
    // TRY-01 双跑
    ///////////////////////////////////////////////////////////////////////////

    private suspend fun SimpleMediaSelectorTestSuite.checkTrySelectDefaultPicksPreferredResolution() {
        initSubject("孤独摇滚")
        mediaApi.addMedia(
            media(
                kind = WEB, subjectName = initApi.subjectName,
                alliance = "字幕组A", resolution = "1080P", subtitleLanguages = listOf("CHS"),
            ),
            media(
                kind = WEB, subjectName = initApi.subjectName,
                alliance = "字幕组B", resolution = "720P", subtitleLanguages = listOf("CHT"),
            ),
        )

        val selected = selector.trySelectDefault()
        assertNotNull(selected)
        assertEquals("1080P", selected.properties.resolution)
        assertEquals(selected, selector.selected.value)
        // TRY-01: selected 已非 null 时再次调用直接返回 null
        assertNull(selector.trySelectDefault())
    }

    @Test
    fun `TRY-01 trySelectDefault 主分支选中偏好资源 - cachingEnabled=false`() =
        runSimpleMediaSelectorTestSuite(cachingEnabled = false) {
            checkTrySelectDefaultPicksPreferredResolution()
        }

    @Test
    fun `TRY-01 trySelectDefault 主分支选中偏好资源 - cachingEnabled=true`() =
        runSimpleMediaSelectorTestSuite(cachingEnabled = true) {
            checkTrySelectDefaultPicksPreferredResolution()
        }

    ///////////////////////////////////////////////////////////////////////////
    // PF-02 双跑
    ///////////////////////////////////////////////////////////////////////////

    private suspend fun SimpleMediaSelectorTestSuite.checkPreferredCandidatesFilteredByUserPreference() {
        initSubject("孤独摇滚")
        preferenceApi.savedUserPreference.value = DEFAULT_PREFERENCE.copy(alliance = "字幕组A")
        mediaApi.addMedia(
            media(kind = WEB, subjectName = initApi.subjectName, alliance = "字幕组A"),
            media(kind = WEB, subjectName = initApi.subjectName, alliance = "字幕组B"),
        )

        // filteredCandidates 不看用户偏好 (FILT-05), preferredCandidates 才按四项偏好 AND 筛 (PF-02)
        assertEquals(
            setOf("字幕组A", "字幕组B"),
            selector.filteredCandidatesMedia.first().map { it.properties.alliance }.toSet(),
        )
        assertEquals(
            listOf("字幕组A"),
            selector.preferredCandidatesMedia.first().map { it.properties.alliance },
        )
    }

    @Test
    fun `PF-02 preferredCandidates 按用户偏好过滤而 filteredCandidates 不过滤 - cachingEnabled=false`() =
        runSimpleMediaSelectorTestSuite(cachingEnabled = false) {
            checkPreferredCandidatesFilteredByUserPreference()
        }

    @Test
    fun `PF-02 preferredCandidates 按用户偏好过滤而 filteredCandidates 不过滤 - cachingEnabled=true`() =
        runSimpleMediaSelectorTestSuite(cachingEnabled = true) {
            checkPreferredCandidatesFilteredByUserPreference()
        }

    ///////////////////////////////////////////////////////////////////////////
    // INFRA-01 终值语义: select 之后候选流收敛到新偏好 (双跑)
    ///////////////////////////////////////////////////////////////////////////

    /**
     * INFRA-01 的"终值语义等价": 无论 caching 开关如何, `select` 写入会话偏好后,
     * 把调度器推进一拍, `preferredCandidatesMedia` 都必须收敛到只剩被选中的那一个.
     *
     * 这里刻意**不**断言"推进之前读到的是什么":
     * - `enableCaching = true` 时读到的是 `shareIn(replay = 1)` 的旧快照, 这属于方案 §2.5#2 登记的微观时序差异;
     * - 而"select 全程不让出调度器"这一点又依赖 `broadcastChangePreference` 在
     *   `onChangePreference.subscriptionCount == 0` 时的早返回 —— 那是一个纯性能优化, 删掉它完全合法.
     *
     * 对时机的观测放在 [`INFRA-01 缓存路径下上游变更在调度器被推进前仍回放旧快照`] 一条里, 且不与 select 耦合.
     */
    private suspend fun SimpleMediaSelectorTestSuite.checkSelectConvergesToSelectedMedia() {
        initSubject("孤独摇滚")
        mediaApi.addMedia(
            media(
                kind = WEB, subjectName = initApi.subjectName,
                alliance = "字幕组A", subtitleLanguages = listOf("CHS"),
            ),
            media(
                kind = WEB, subjectName = initApi.subjectName,
                alliance = "字幕组B", subtitleLanguages = listOf("CHT"),
            ),
        )
        val mediaB = mediaApi.mediaList.value[1]
        assertEquals(2, selector.preferredCandidatesMedia.first().size)

        assertTrue(selector.select(mediaB))

        // 必须用 runCurrent 而不是 advanceUntilIdle: cachingScope 是 TestScope.backgroundScope,
        // 它派发的任务是 background dispatch event, 而 advanceUntilIdle 的停止条件是
        // `events.none { it.isForeground }` —— 队列里只剩 background 任务时它一步都不跑 (虚拟时间也不前进).
        testScope.runCurrent()
        assertEquals(listOf(mediaB), selector.preferredCandidatesMedia.first())
    }

    @Test
    fun `INFRA-01 select 后候选流收敛到被选中的 media - cachingEnabled=false`() =
        runSimpleMediaSelectorTestSuite(cachingEnabled = false) {
            checkSelectConvergesToSelectedMedia()
        }

    @Test
    fun `INFRA-01 select 后候选流收敛到被选中的 media - cachingEnabled=true`() =
        runSimpleMediaSelectorTestSuite(cachingEnabled = true) {
            checkSelectConvergesToSelectedMedia()
        }

    ///////////////////////////////////////////////////////////////////////////
    // INFRA-01 缓存路径独有: replay=1 的旧快照
    ///////////////////////////////////////////////////////////////////////////

    /**
     * INFRA-01 缓存路径的可观测后果: `preferredCandidates` 是 `shareIn(WhileSubscribed(5s), replay = 1)`,
     * 上游变更之后, 在调度器被推进之前, `first()` 会同步回放**旧快照**.
     *
     * 这条**不是**要保住的行为, 是方案 §2.5#2「微观时序差异」第 2 项登记的现状:
     * 6 处独立 `cached()` 收敛为单点 shareIn 之后, 本现象预期消失, 届时删掉这条用例即可.
     * 因此不标 PINNED.
     *
     * 触发方式刻意选了"直接改上游 [MutableStateFlow]"而不是 `select`:
     * 后者的同步性依赖 `broadcastChangePreference` 在无订阅者时的早返回(一个纯性能优化),
     * 会让这条用例对一次完全合法的重构误报.
     */
    @Test
    fun `INFRA-01 缓存路径下上游变更在调度器被推进前仍回放旧快照`() = runSimpleMediaSelectorTestSuite(
        cachingEnabled = true,
        buildTest = {
            initSubject("孤独摇滚")
            preferenceApi.savedDefaultPreference.value = DEFAULT_PREFERENCE.copy(showWithoutSubtitle = true)
            mediaApi.addMedia(
                media(
                    kind = WEB, subjectName = initApi.subjectName,
                    alliance = "字幕组A", subtitleLanguages = listOf("CHS"),
                ),
                media(
                    kind = WEB, subjectName = initApi.subjectName,
                    alliance = "字幕组B", subtitleLanguages = emptyList(),
                ),
            )
        },
    ) {
        // 建立第一次订阅, 让 shareIn 的 replay 缓存里有值
        assertEquals(2, selector.preferredCandidatesMedia.first().size)

        // FILT-04: 收紧全局默认后, 无字幕的那条应当被排除. 只动上游 MutableStateFlow, 不经过任何 selector 方法.
        preferenceApi.savedDefaultPreference.value = DEFAULT_PREFERENCE.copy(showWithoutSubtitle = false)
        assertEquals(2, selector.preferredCandidatesMedia.first().size)

        testScope.runCurrent()
        assertEquals(1, selector.preferredCandidatesMedia.first().size)
    }

    ///////////////////////////////////////////////////////////////////////////
    // SEL-02 / SEL-05 双跑
    ///////////////////////////////////////////////////////////////////////////

    private suspend fun SimpleMediaSelectorTestSuite.checkSelectBroadcastsEventsAndPayload() {
        initSubject("孤独摇滚")
        mediaApi.addMedia(
            media(
                kind = WEB, subjectName = initApi.subjectName,
                alliance = "字幕组A", resolution = "1080P", subtitleLanguages = listOf("CHS"),
            ),
            media(
                kind = WEB, subjectName = initApi.subjectName,
                alliance = "字幕组B", resolution = "720P", subtitleLanguages = listOf("CHT"),
            ),
        )
        val mediaA = mediaApi.mediaList.value[0]

        val collected = selector.collectEvents {
            assertTrue(selector.select(mediaA))
        }

        // SEL-02: 四步副作用顺序不因 caching 改变
        collected.assertOrder(
            OnBeforeSelect::class,
            OnChangePreference::class,
            OnPreferWebSource::class,
            OnSelect::class,
        )
        val expectedEvent = SelectEvent(media = mediaA, subtitleLanguageId = null, previousMedia = null)
        assertEquals(expectedEvent, collected.onBeforeSelect.single().event)
        assertEquals(expectedEvent, collected.onSelect.single().event)

        // SEL-05: 载荷 = savedUserPreference.copy(四个核心字段取自 newPreferences), 其余字段保留数据库原值
        assertEquals(
            DEFAULT_PREFERENCE.copy(
                alliance = "字幕组A",
                resolution = "1080P",
                subtitleLanguageId = "CHS",
                mediaSourceId = SOURCE_DMHY,
            ),
            collected.onChangePreference.single().preference,
        )
    }

    @Test
    fun `SEL-05 select 广播的偏好载荷四字段来自 media - cachingEnabled=false`() =
        runSimpleMediaSelectorTestSuite(cachingEnabled = false) {
            checkSelectBroadcastsEventsAndPayload()
        }

    @Test
    fun `SEL-05 select 广播的偏好载荷四字段来自 media - cachingEnabled=true`() =
        runSimpleMediaSelectorTestSuite(cachingEnabled = true) {
            checkSelectBroadcastsEventsAndPayload()
        }

    ///////////////////////////////////////////////////////////////////////////
    // FAST-02 双跑 (走 MediaFetchSession 的编排路径)
    ///////////////////////////////////////////////////////////////////////////

    private val preferredWebMediaSource = MutableStateFlow<String?>(null)

    context(scope: TestScope)
    private suspend fun FetchMediaSelectorTestSuite.checkTier0SourceIsInstantlySelected() {
        initSubject("test")
        preferenceApi.savedUserPreference.value = MediaPreference.Any
        preferenceApi.mediaSelectorSettings.value = MediaSelectorSettings.AllVisible.copy(
            autoEnableLastSelected = false,
            fastSelectWebKind = true,
            preferKind = WEB,
            fastSelectWebLowTierToleranceDuration = 5.seconds,
            hideSingleEpisodeForCompleted = false,
            preferSeasons = false,
        )

        val (_, session, sources) = configureFetchSession {
            object {
                val web1 by web { tier = 0 }
            }
        }

        val useCase = MediaSelectorAutoSelectUseCaseImpl(createKoin())
        val job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            useCase(session, selector)
        }
        sources.web1.complete(media(kind = WEB, subjectName = initApi.subjectName))
        scope.runCurrent()

        assertEquals("web1", selector.selected.value?.mediaSourceId)
        assertTrue(job.isCompleted)
        assertFalse(job.isCancelled)
    }

    @Test
    fun `FAST-02 tier0 源被秒选并结束编排 - cachingEnabled=false`() =
        runFetchMediaSelectorTestSuite(cachingEnabled = false) {
            checkTier0SourceIsInstantlySelected()
        }

    @Test
    fun `FAST-02 tier0 源被秒选并结束编排 - cachingEnabled=true`() =
        runFetchMediaSelectorTestSuite(cachingEnabled = true) {
            checkTier0SourceIsInstantlySelected()
        }

    private fun FetchMediaSelectorTestSuite.createKoin(): Koin {
        return Koin().apply {
            loadModules(
                listOf(
                    module {
                        single<GetMediaSelectorSettingsFlowUseCase> {
                            GetMediaSelectorSettingsFlowUseCase { preferenceApi.mediaSelectorSettings }
                        }
                        single<GetMediaSelectorSourceTiersUseCase> {
                            GetMediaSelectorSourceTiersUseCase {
                                preferenceApi.mediaSelectorContext.map {
                                    it.mediaSourceTiers ?: MediaSelectorSourceTiers.Empty
                                }
                            }
                        }
                        single<GetPreferredWebMediaSourceUseCase> {
                            GetPreferredWebMediaSourceUseCase { preferredWebMediaSource }
                        }
                    },
                ),
            )
        }
    }
}
