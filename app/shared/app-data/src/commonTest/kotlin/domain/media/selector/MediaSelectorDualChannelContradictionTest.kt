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
import kotlinx.coroutines.test.currentTime
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

/**
 * 双通道矛盾特征 (P0#18): per-subject MediaPreference JSON 通道与 Room
 * preferred_web_media_source 通道同时有值且互相矛盾时的自动选择运行时行为.
 * 作为通道统一 backfill 裁决 (C9) 的对照基线.
 */
@DisabledOnNative // TODO: ContextParameters crashes on Native
class MediaSelectorDualChannelContradictionTest {
    private val preferredWebMediaSource = MutableStateFlow<String?>(null)

    /**
     * 正向对照: 三条 MIG-DUAL 矛盾用例都以 "Room 通道落空" 收场, 单看它们无法区分 "Room 通道被 JSON 偏好否决"
     * 与 "Room 通道整条链根本没接上" (把 [GetPreferredWebMediaSourceUseCase] 恒返回 null 三条都仍然绿).
     *
     * 本用例去掉 JSON 侧的 `mediaSourceId` 约束, 使 Room 偏好源的候选能进入 `preferredCandidates`,
     * 此时 clause① 是唯一能在 T0 选中的路径:
     * - web1 (tier=0, 快速选择的 instant 路径唯一能秒选的源) 一直没有结果;
     * - web2 是 tier=2, 被 `getBestTier() > InstantSelectTierThreshold` 挡在 instant 路径之外;
     * - 容忍窗 fallback 要到 5s 才触发, 而这里虚拟时间仍停在 0.
     *
     * 所以选中 web2 只能是 Room 通道 (`trySelectPreferredWebSource`) 干的.
     */
    @Test
    fun `MIG-DUAL-00 正向对照 JSON无源约束时Room偏好源在T0经clause1直接选中`() = runFetchMediaSelectorTestSuite {
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
        sources.web2.complete(media(kind = WEB, subjectName = initApi.subjectName))
        testScope().runCurrent()

        assertEquals(0L, testScope().currentTime, "容忍窗 fallback 不应参与, 选择必须发生在 T0")
        assertSelectedSource(sources.web2)
        job.assertCompleted()
    }

    @Test
    fun `MIG-DUAL-01 JSON偏好web1时Room偏好web2完成有结果clause1仍落空最终选JSON侧`() = runFetchMediaSelectorTestSuite {
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
        sources.web2.complete(media(kind = WEB, subjectName = initApi.subjectName))
        testScope().runCurrent()

        // PINNED: MIG-DUAL-01 Room 偏好源已完成且有结果, 但 JSON mediaSourceId 偏好把它滤出 preferredCandidates,
        // clause① (allowNonPreferred=false) 落空转为挂起
        assertNull(selector.selected.value)
        assertFalse(job.isCompleted)

        sources.web1.complete(media(kind = WEB, subjectName = initApi.subjectName))
        testScope().runCurrent()

        assertSelectedSource(sources.web1)
        job.assertCompleted()
    }

    @Test
    fun `MIG-DUAL-02 JSON偏好web1无结果时仍可在精确匹配阶段选择web2`() = runFetchMediaSelectorTestSuite {
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
        sources.web2.complete(media(kind = WEB, subjectName = initApi.subjectName))
        testScope().runCurrent()

        assertNull(selector.selected.value)
        assertFalse(job.isCompleted)

        sources.web1.complete(emptyList<Media>())
        testScope().runCurrent()

        // Remembered-source selection respects the existing JSON preference. Once it fails,
        // exact fallback may relax that preference without a separate completion clause cancelling it.
        assertNull(selector.selected.value)
        assertFalse(job.isCompleted)
        testScope().advanceTimeBy(5.seconds)
        testScope().runCurrent()
        assertSelectedSource(sources.web2)
        job.assertCompleted()
    }

    @Test
    fun `MIG-DUAL-03 JSON字幕组偏好与Room偏好源候选不相容时clause1落空经容忍超时兜底选中`() = runFetchMediaSelectorTestSuite {
        initSubject()
        preferenceApi.savedUserPreference.value = MediaPreference.Any.copy(alliance = "桜都字幕组")
        preferenceApi.mediaSelectorSettings.value = autoSelectSettings(lowTierToleranceDuration = 1.seconds)

        val (_, session, sources) = configureFetchSession {
            object {
                val web1 by web { tier = 0 }
                val web2 by web { tier = 2 }
            }
        }
        preferredWebMediaSource.value = "web2"

        val job = launchAutoSelect(session)
        sources.web2.complete(
            media(kind = WEB, alliance = "字幕组", subjectName = initApi.subjectName),
        )
        testScope().runCurrent()

        // PINNED: MIG-DUAL-03 alliance 偏好把 Room 偏好源的候选滤出 preferredCandidates, clause① 落空
        assertNull(selector.selected.value)
        assertFalse(job.isCompleted)

        testScope().advanceTimeBy(1.seconds)
        testScope().runCurrent()

        // 容忍超时后 fast select 兜底以 allowNonPreferred=true 无视 alliance 偏好选中该源
        assertSelectedSource(sources.web2)
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
     * 否则 "编排被 CancellationException 中断" 会被误读成 "编排正常结束".
     */
    private fun Job.assertCompleted() {
        assertTrue(isCompleted, "Auto select job should have completed")
        assertFalse(isCancelled, "Auto select job should have completed normally, but it was cancelled")
    }

    private fun autoSelectSettings(
        preferKind: MediaSourceKind? = WEB,
        fastSelectWebKind: Boolean = true,
        lowTierToleranceDuration: Duration = 5.seconds,
    ): MediaSelectorSettings = MediaSelectorSettings.AllVisible.copy(
        autoEnableLastSelected = false,
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
