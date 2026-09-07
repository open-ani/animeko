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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.app.domain.media.fetch.MediaFetchSession
import me.him188.ani.app.domain.media.selector.testFramework.FetchMediaSelectorTestSuite
import me.him188.ani.app.domain.media.selector.testFramework.Handle
import me.him188.ani.app.domain.media.selector.testFramework.MediaSelectorTestSuite
import me.him188.ani.app.domain.media.selector.testFramework.collectEvents
import me.him188.ani.app.domain.media.selector.testFramework.runFetchMediaSelectorTestSuite
import me.him188.ani.app.domain.media.selector.testFramework.tier
import me.him188.ani.app.domain.mediasource.GetMediaSelectorSourceTiersUseCase
import me.him188.ani.app.domain.mediasource.GetPreferredWebMediaSourceUseCase
import me.him188.ani.app.domain.settings.GetMediaSelectorSettingsFlowUseCase
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.source.MediaSourceKind.BitTorrent
import me.him188.ani.datasources.api.source.MediaSourceKind.WEB
import me.him188.ani.test.DisabledOnNative
import org.koin.core.Koin
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@DisabledOnNative // TODO: ContextParameters crashes on Native
class MediaSelectorOrchestrationCharacterizationTest {
    private val preferredWebMediaSource = MutableStateFlow<String?>(null)

    @Test
    fun `MISC-03 仅启动编排即驱动全部源查询`() = runFetchMediaSelectorTestSuite {
        initSubject()
        preferenceApi.savedUserPreference.value = MediaPreference.Any
        preferenceApi.mediaSelectorSettings.value = autoSelectSettings(preferKind = null, fastSelectWebKind = false)

        val (_, session, sources) = configureFetchSession(startInBackground = false) {
            object {
                val web1 by web()
                val bt1 by bt()
            }
        }

        testScope().runCurrent()
        assertEquals(0, sources.web1.fetchCount)
        assertEquals(0, sources.bt1.fetchCount)

        val job = launchAutoSelect(session)
        testScope().runCurrent()

        // PINNED: MISC-03
        assertEquals(1, sources.web1.fetchCount)
        assertEquals(1, sources.bt1.fetchCount)
        assertFalse(job.isCompleted)

        sources.web1.complete(media(kind = WEB, subjectName = initApi.subjectName))
        sources.bt1.complete(media(kind = BitTorrent, subjectName = initApi.subjectName))
        testScope().runCurrent()

        job.assertCompleted()
        assertEquals(1, sources.web1.fetchCount)
        assertEquals(1, sources.bt1.fetchCount)
    }

    @Test
    fun `completed Web sources with mismatched preference wait for exact phase instead of cancelling selection`() = runFetchMediaSelectorTestSuite {
        initSubject()
        preferenceApi.savedUserPreference.value = MediaPreference.Any.copy(alliance = "组A")
        preferenceApi.mediaSelectorSettings.value = autoSelectSettings()
        val (_, session, sources) = configureFetchSession {
            object {
                val web1 by web { tier = 2 }
                val web2 by web { tier = 2 }
            }
        }
        val collected = selector.collectEvents {
            val job = launchAutoSelect(session)
            sources.web1.complete(media(kind = WEB, alliance = "组B", subjectName = initApi.subjectName))
            sources.web2.complete(media(kind = WEB, alliance = "组B", subjectName = initApi.subjectName))
            testScope().runCurrent()
            assertFalse(job.isCompleted)
            assertNull(selector.selected.value)
            assertEquals(2, selector.filteredCandidatesMedia.first().size)
            assertEquals(emptyList(), selector.preferredCandidatesMedia.first())
            testScope().advanceTimeBy(5.seconds)
            testScope().runCurrent()
            assertSelectedSource(sources.web1)
            job.assertCompleted()
        }
        assertEquals(1, collected.onSelect.size)
        assertEquals(0, collected.onChangePreference.size)
    }

    @Test
    fun `pending Web source does not prevent exact fallback after the first deadline`() = runFetchMediaSelectorTestSuite {
        initSubject()
        preferenceApi.savedUserPreference.value = MediaPreference.Any.copy(alliance = "组A")
        preferenceApi.mediaSelectorSettings.value = autoSelectSettings()
        val (_, session, sources) = configureFetchSession {
            object {
                val web1 by web { tier = 2 }
                val pendingWeb by web { tier = 2 }
            }
        }
        val job = launchAutoSelect(session)
        sources.web1.complete(media(kind = WEB, alliance = "组B", subjectName = initApi.subjectName))
        testScope().runCurrent()
        assertNull(selector.selected.value)
        assertFalse(job.isCompleted)
        testScope().advanceTimeBy(5.seconds)
        testScope().runCurrent()
        assertSelectedSource(sources.web1)
        job.assertCompleted()
    }

    @Test
    fun `EMG-01 BT 偏好字幕组消失时自动选择彻底不发生`() = runFetchMediaSelectorTestSuite {
        initSubject()
        preferenceApi.savedUserPreference.value = MediaPreference.Any.copy(alliance = "组A")
        preferenceApi.mediaSelectorSettings.value = autoSelectSettings(preferKind = BitTorrent)

        val (_, session, sources) = configureFetchSession {
            object {
                val bt1 by bt()
            }
        }

        val job = launchAutoSelect(session)
        sources.bt1.complete(
            media(kind = BitTorrent, alliance = "组B", subjectName = initApi.subjectName),
            media(kind = BitTorrent, alliance = "组B", subjectName = initApi.subjectName),
        )
        testScope().advanceUntilIdle()

        // PINNED: EMG-01
        assertNull(selector.selected.value)
        job.assertCompleted()
    }

    @Test
    fun `EMG-01 对照组 BT 偏好字幕组存在时正常自动选择`() = runFetchMediaSelectorTestSuite {
        initSubject()
        preferenceApi.savedUserPreference.value = MediaPreference.Any.copy(alliance = "组A")
        preferenceApi.mediaSelectorSettings.value = autoSelectSettings(preferKind = BitTorrent)

        val (_, session, sources) = configureFetchSession {
            object {
                val bt1 by bt()
            }
        }

        val job = launchAutoSelect(session)
        sources.bt1.complete(
            media(kind = BitTorrent, alliance = "组B", subjectName = initApi.subjectName),
            media(kind = BitTorrent, alliance = "组A", subjectName = initApi.subjectName),
        )
        testScope().advanceUntilIdle()

        assertEquals("组A", selector.selected.value?.properties?.alliance)
        assertSelectedSource(sources.bt1)
        job.assertCompleted()
    }

    context(scope: TestScope)
    private fun FetchMediaSelectorTestSuite.launchAutoSelect(session: MediaFetchSession): Job {
        val useCase = MediaSelectorAutoSelectUseCaseImpl(createKoin())
        return scope.launch(start = CoroutineStart.UNDISPATCHED) {
            useCase(session, selector)
        }
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

    private fun FetchMediaSelectorTestSuite.assertSelectedSource(source: Handle) {
        assertEquals(source.instance.mediaSourceId, selector.selected.value?.mediaSourceId)
    }

    /**
     * 编排 **正常** 结束. [Job.isCompleted] 对被取消的 job 同样为 `true`, 所以必须同时排除取消/异常终止,
     * 否则 "编排被 CancellationException 中断" 会被误读成 "编排正常以 null 结束".
     */
    private fun Job.assertCompleted() {
        assertTrue(isCompleted, "Auto select job should have completed")
        assertFalse(isCancelled, "Auto select job should have completed normally, but it was cancelled")
    }

    private fun autoSelectSettings(
        preferKind: MediaSourceKind? = WEB,
        fastSelectWebKind: Boolean = true,
        autoEnableLastSelected: Boolean = false,
        lowTierToleranceDuration: Duration = 5.seconds,
    ): MediaSelectorSettings = MediaSelectorSettings.AllVisible.copy(
        autoEnableLastSelected = autoEnableLastSelected,
        fastSelectWebKind = fastSelectWebKind,
        preferKind = preferKind,
        fastSelectWebLowTierToleranceDuration = lowTierToleranceDuration,
        hideSingleEpisodeForCompleted = false,
        preferSeasons = false,
    )

    private fun MediaSelectorTestSuite.initSubject() {
        initSubject("test")
    }

    context(scope: TestScope)
    private fun testScope(): TestScope = implicit()
}
