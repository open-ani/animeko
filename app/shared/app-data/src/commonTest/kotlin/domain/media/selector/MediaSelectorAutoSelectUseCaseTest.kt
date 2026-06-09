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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.app.domain.media.fetch.MediaFetchSession
import me.him188.ani.app.domain.media.selector.testFramework.FetchMediaSelectorTestSuite
import me.him188.ani.app.domain.media.selector.testFramework.Handle
import me.him188.ani.app.domain.media.selector.testFramework.MediaSelectorTestSuite
import me.him188.ani.app.domain.media.selector.testFramework.runFetchMediaSelectorTestSuite
import me.him188.ani.app.domain.media.selector.testFramework.tier
import me.him188.ani.app.domain.mediasource.GetMediaSelectorSourceTiersUseCase
import me.him188.ani.app.domain.mediasource.GetPreferredWebMediaSourceUseCase
import me.him188.ani.app.domain.settings.GetMediaSelectorSettingsFlowUseCase
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.source.MediaSourceKind.BitTorrent
import me.him188.ani.datasources.api.source.MediaSourceKind.LocalCache
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
class MediaSelectorAutoSelectUseCaseTest {
    private val preferredWebMediaSource = MutableStateFlow<String?>(null)

    @Test
    fun `preferred web source blocks fast select until preferred source completes`() = runFetchMediaSelectorTestSuite {
        initSubject()
        preferenceApi.savedUserPreference.value = MediaPreference.Any
        preferenceApi.mediaSelectorSettings.value = autoSelectSettings()

        val (_, session, sources) = configureFetchSession {
            object {
                val web1 by web { tier = 0 }
                val web2 by web { tier = 2 }
            }
        }
        preferredWebMediaSource.value = "web2"

        val job = launchAutoSelect(session)
        sources.web1.complete(media(kind = WEB, subjectName = initApi.subjectName))
        testScope().runCurrent()

        assertNull(selector.selected.value)
        assertFalse(job.isCompleted)

        sources.web2.complete(media(kind = WEB, subjectName = initApi.subjectName))
        testScope().runCurrent()

        assertSelectedSource(sources.web2)
        job.assertCompleted()
    }

    @Test
    fun `fast select starts after preferred source completes with no media`() = runFetchMediaSelectorTestSuite {
        initSubject()
        preferenceApi.savedUserPreference.value = MediaPreference.Any
        preferenceApi.mediaSelectorSettings.value = autoSelectSettings()

        val (_, session, sources) = configureFetchSession {
            object {
                val web1 by web { tier = 0 }
                val web2 by web { tier = 2 }
            }
        }
        preferredWebMediaSource.value = "web2"

        val job = launchAutoSelect(session)
        sources.web1.complete(media(kind = WEB, subjectName = initApi.subjectName))
        testScope().runCurrent()

        assertNull(selector.selected.value)
        assertFalse(job.isCompleted)

        sources.web2.complete(emptyList<Media>())
        testScope().runCurrent()

        assertSelectedSource(sources.web1)
        job.assertCompleted()
    }

    @Test
    fun `fast select starts immediately when preferred source is unset`() = runFetchMediaSelectorTestSuite {
        initSubject()
        preferenceApi.savedUserPreference.value = MediaPreference.Any
        preferenceApi.mediaSelectorSettings.value = autoSelectSettings()

        val (_, session, sources) = configureFetchSession {
            object {
                val web1 by web { tier = 0 }
            }
        }

        val job = launchAutoSelect(session)
        sources.web1.complete(media(kind = WEB, subjectName = initApi.subjectName))
        testScope().runCurrent()

        assertSelectedSource(sources.web1)
        job.assertCompleted()
    }

    @Test
    fun `fast select starts immediately when preferred source is not in session`() = runFetchMediaSelectorTestSuite {
        initSubject()
        preferenceApi.savedUserPreference.value = MediaPreference.Any
        preferenceApi.mediaSelectorSettings.value = autoSelectSettings()

        val (_, session, sources) = configureFetchSession {
            object {
                val web1 by web { tier = 0 }
            }
        }
        preferredWebMediaSource.value = "missing"

        val job = launchAutoSelect(session)
        sources.web1.complete(media(kind = WEB, subjectName = initApi.subjectName))
        testScope().runCurrent()

        assertSelectedSource(sources.web1)
        job.assertCompleted()
    }

    @Test
    fun `preferred source obeys current source preference before fast select falls back to non preferred`() =
        runFetchMediaSelectorTestSuite {
            initSubject()
            preferenceApi.savedUserPreference.value = MediaPreference.Any.copy(mediaSourceId = "web1")
            preferenceApi.mediaSelectorSettings.value = autoSelectSettings()

            val (_, session, sources) = configureFetchSession {
                object {
                    val web1 by web { tier = 0 }
                    val web2 by web { tier = 2 }
                }
            }
            preferredWebMediaSource.value = "web2"

            val job = launchAutoSelect(session)
            sources.web1.complete(media(kind = WEB, subjectName = initApi.subjectName))
            sources.web2.complete(media(kind = WEB, subjectName = initApi.subjectName))
            testScope().runCurrent()

            assertSelectedSource(sources.web1)
            job.assertCompleted()
        }

    @Test
    fun `fast select timeout starts only after preferred source fails`() = runFetchMediaSelectorTestSuite {
        initSubject()
        preferenceApi.savedUserPreference.value = MediaPreference.Any
        preferenceApi.mediaSelectorSettings.value = autoSelectSettings(lowTierToleranceDuration = 1.seconds)

        val (_, session, sources) = configureFetchSession {
            object {
                val web1 by web { tier = 2 }
                val web2 by web { tier = 0 }
                val web3 by web { tier = 2 }
            }
        }
        preferredWebMediaSource.value = "web2"

        val job = launchAutoSelect(session)
        sources.web1.complete(media(kind = WEB, subjectName = initApi.subjectName))
        testScope().advanceTimeBy(1.seconds)
        testScope().runCurrent()

        assertNull(selector.selected.value)
        assertFalse(job.isCompleted)

        sources.web2.complete(emptyList<Media>())
        testScope().runCurrent()

        assertNull(selector.selected.value)
        assertFalse(job.isCompleted)

        testScope().advanceTimeBy(1.seconds)
        testScope().runCurrent()

        assertSelectedSource(sources.web1)
        job.assertCompleted()
    }

    @Test
    fun `local cache can win while preferred web source is still pending`() = runFetchMediaSelectorTestSuite {
        initSubject()
        preferenceApi.savedUserPreference.value = MediaPreference.Any
        preferenceApi.mediaSelectorSettings.value = autoSelectSettings()

        val (_, session, sources) = configureFetchSession {
            object {
                val cached by localCache()
                val web1 by web { tier = 0 }
            }
        }
        preferredWebMediaSource.value = "web1"

        val job = launchAutoSelect(session)
        sources.cached.complete(media(kind = LocalCache, subjectName = initApi.subjectName))
        testScope().runCurrent()

        assertSelectedSource(sources.cached)
        job.assertCompleted()
    }

    @Test
    fun `fallback default waits for preferred kind when fast select is disabled`() = runFetchMediaSelectorTestSuite {
        initSubject()
        preferenceApi.savedUserPreference.value = MediaPreference.Any
        preferenceApi.mediaSelectorSettings.value = autoSelectSettings(fastSelectWebKind = false)

        val (_, session, sources) = configureFetchSession {
            object {
                val web1 by web { tier = 0 }
                val web2 by web { tier = 2 }
            }
        }

        val job = launchAutoSelect(session)
        sources.web1.complete(media(kind = WEB, subjectName = initApi.subjectName))
        testScope().runCurrent()

        assertNull(selector.selected.value)
        assertFalse(job.isCompleted)

        sources.web2.complete(media(kind = WEB, subjectName = initApi.subjectName))
        testScope().runCurrent()

        assertSelectedSource(sources.web1)
        job.assertCompleted()
    }

    @Test
    fun `fast select is skipped when preferred kind is not web`() = runFetchMediaSelectorTestSuite {
        initSubject()
        preferenceApi.savedUserPreference.value = MediaPreference.Any
        preferenceApi.mediaSelectorSettings.value = autoSelectSettings(preferKind = BitTorrent)

        val (_, session, sources) = configureFetchSession {
            object {
                val bt1 by bt()
                val web1 by web { tier = 0 }
            }
        }

        val job = launchAutoSelect(session)
        sources.web1.complete(media(kind = WEB, subjectName = initApi.subjectName))
        testScope().runCurrent()

        assertNull(selector.selected.value)
        assertFalse(job.isCompleted)

        sources.bt1.complete(media(kind = BitTorrent, subjectName = initApi.subjectName))
        testScope().runCurrent()

        assertSelectedSource(sources.bt1)
        job.assertCompleted()
    }

    @Test
    fun `auto enables last selected disabled source`() = runFetchMediaSelectorTestSuite {
        initSubject()
        preferenceApi.savedUserPreference.value = MediaPreference.Any.copy(mediaSourceId = "web1")
        preferenceApi.mediaSelectorSettings.value = autoSelectSettings(
            autoEnableLastSelected = true,
            fastSelectWebKind = false,
        )

        val (_, session, sources) = configureFetchSession {
            object {
                val web1 by web(enabled = false)
                val web2 by web()
            }
        }

        val job = launchAutoSelect(session)
        testScope().runCurrent()

        sources.web1.complete(media(kind = WEB, subjectName = initApi.subjectName))
        testScope().runCurrent()

        assertNull(selector.selected.value)
        assertFalse(job.isCompleted)

        sources.web2.complete(media(kind = WEB, subjectName = initApi.subjectName))
        testScope().runCurrent()

        assertSelectedSource(sources.web1)
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

    private fun Job.assertCompleted() {
        assertTrue(isCompleted, "Auto select job should have completed")
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
