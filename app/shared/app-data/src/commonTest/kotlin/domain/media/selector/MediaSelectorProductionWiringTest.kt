/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.selector

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.app.data.models.subject.SubjectSeriesInfo
import me.him188.ani.app.data.models.subject.TestSubjectCollections
import me.him188.ani.app.data.persistent.createTestPreferencesDataStore
import me.him188.ani.app.data.persistent.database.dao.createMemoryPreferredWebMediaSourceDao
import me.him188.ani.app.data.repository.media.EpisodePreferencesRepository
import me.him188.ani.app.data.repository.media.EpisodePreferencesRepositoryImpl
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.data.repository.user.Settings
import me.him188.ani.app.domain.episode.CreateMediaFetchSelectBundleFlowUseCaseImpl
import me.him188.ani.app.domain.episode.SubjectEpisodeInfoBundle
import me.him188.ani.app.domain.media.fetch.MediaSourceManager
import me.him188.ani.app.domain.mediasource.codec.MediaSourceTier
import me.him188.ani.app.domain.media.selector.testFramework.runSimpleMediaSelectorTestSuite
import me.him188.ani.datasources.api.source.FactoryId
import me.him188.ani.datasources.api.source.MediaSourceConfig
import me.him188.ani.datasources.api.source.MediaSourceKind
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.test.advanceTimeBy

/**
 * P0#17 的生产接线点覆盖: 此前跨会话测试用 lambda/测试实现顶替了生产类, 导致
 * [MediaSelectorEventSavePreferenceUseCaseImpl] 与
 * [CreateMediaFetchSelectBundleFlowUseCaseImpl] 的 `savedUserPreference = mediaPreferenceFlow(subjectId)`
 * 接线零覆盖 —— 方向 B 改写这两处时不会有测试变红.
 *
 * 两个生产类都是 KoinComponent (全局 Koin), 因此本测试用 startKoin/stopKoin 注入真实
 * [EpisodePreferencesRepositoryImpl] (内存 DataStore).
 */
class MediaSelectorProductionWiringTest {
    private companion object {
        const val SUBJECT_ID = 42
    }

    private fun createRepository(
        globalDefault: MutableStateFlow<MediaPreference>,
    ): EpisodePreferencesRepositoryImpl = EpisodePreferencesRepositoryImpl(
        store = createTestPreferencesDataStore(),
        preferredWebMediaSourceDao = createMemoryPreferredWebMediaSourceDao(),
        defaultMediaPreference = globalDefault,
    )

    @Test
    fun `A11 生产保存链 MediaSelectorEventSavePreferenceUseCaseImpl 把手动选择落库到真实 repository`() =
        runSimpleMediaSelectorTestSuite(
            buildTest = {
                initSubject("孤独摇滚")
                preferenceApi.savedUserPreference.value = MediaPreference.Empty
                preferenceApi.savedDefaultPreference.value = MediaPreference.Empty
            },
        ) {
            val target = media(
                sourceId = "web1",
                alliance = "桜都字幕组",
                resolution = "720P",
                subtitleLanguages = listOf("CHT"),
                kind = MediaSourceKind.WEB,
            )
            mediaApi.addMedia(target)

            val repository = createRepository(MutableStateFlow(MediaPreference.Empty))
            startKoin {
                modules(module { single<EpisodePreferencesRepository> { repository } })
            }
            try {
                val job = testScope.launch {
                    MediaSelectorEventSavePreferenceUseCaseImpl(selector, SUBJECT_ID)
                }
                testScope.runCurrent()

                assertTrue(selector.select(target))
                testScope.advanceTimeBy(1001.milliseconds)
                testScope.runCurrent()

                assertEquals(
                    MediaPreference.Empty.copy(
                        alliance = "桜都字幕组",
                        resolution = "720P",
                        subtitleLanguageId = "CHT",
                        mediaSourceId = "web1",
                    ),
                    repository.mediaPreferenceFlow(SUBJECT_ID).first(),
                )
                job.cancel()
            } finally {
                stopKoin()
            }
        }

    @Suppress("DEPRECATION")
    @Test
    fun `A11 生产接线 CreateMediaFetchSelectBundleFlowUseCaseImpl 的 selector 从真实 repository 按 subjectId 读偏好且跟随更新`() =
        runTest {
            val globalDefault = MutableStateFlow(MediaPreference.Empty)
            val repository = createRepository(globalDefault)
            repository.setMediaPreference(
                SUBJECT_ID,
                MediaPreference.Empty.copy(mediaSourceId = "web1", alliance = "桜都字幕组"),
            )

            val builder = me.him188.ani.app.domain.media.selector.legacy.MediaSelectorTestBuilder(this)
            builder.delayedMediaSource("web1").complete(emptyList())

            val fakeManager = object : MediaSourceManager {
                override val allInstances = MutableStateFlow(builder.mediaSources.toList())
                override val mediaFetcher = flowOf(builder.createMediaFetcher())
                override fun mediaSourceTiersFlow(): Flow<MediaSelectorSourceTiers> =
                    flowOf(MediaSelectorSourceTiers(emptyMap(), emptyMap()) { MediaSourceTier.Fallback })

                override val allFactories get() = error("not used")
                override val allFactoryIds get() = error("not used")
                override val webVideoMatcherLoader get() = error("not used")
                override fun instanceConfigFlow(instanceId: String) = error("not used")
                override suspend fun addInstance(
                    instanceId: String, mediaSourceId: String, factoryId: FactoryId, config: MediaSourceConfig,
                ) = error("not used")

                override suspend fun getListBySubscriptionId(subscriptionId: String) = error("not used")
                override suspend fun partiallyReorderInstances(instanceIds: List<String>) = error("not used")
                override suspend fun updateConfig(instanceId: String, config: MediaSourceConfig) = error("not used")
                override suspend fun setEnabled(instanceId: String, enabled: Boolean) = error("not used")
                override suspend fun removeInstance(instanceId: String) = error("not used")
            }
            val fakeSettings = object : SettingsRepository {
                override val mediaSelectorSettings = staticSettings(MediaSelectorSettings.Default)
                override val defaultMediaPreference = staticSettings(MediaPreference.Empty)

                override val danmakuEnabled get() = error("not used")
                override val danmakuConfig get() = error("not used")
                override val danmakuFilterConfig get() = error("not used")
                override val profileSettings get() = error("not used")
                override val proxySettings get() = error("not used")
                override val mediaCacheSettings get() = error("not used")
                override val danmakuSettings get() = error("not used")
                override val uiSettings get() = error("not used")
                override val themeSettings get() = error("not used")
                override val updateSettings get() = error("not used")
                override val videoScaffoldConfig get() = error("not used")
                override val playerKernelConfig get() = error("not used")
                override val videoResolverSettings get() = error("not used")
                override val anitorrentConfig get() = error("not used")
                override val pikpakConfig get() = error("not used")
                override val torrentPeerConfig get() = error("not used")
                override val oneshotActionConfig get() = error("not used")
                override val analyticsSettings get() = error("not used")
                override val debugSettings get() = error("not used")
                override val watchTogetherSettings get() = error("not used")
            }

            startKoin {
                modules(
                    module {
                        single<MediaSourceManager> { fakeManager }
                        single<EpisodePreferencesRepository> { repository }
                        single<SettingsRepository> { fakeSettings }
                    },
                )
            }
            try {
                val useCase = CreateMediaFetchSelectBundleFlowUseCaseImpl(
                    flowContext = coroutineContext[ContinuationInterceptor] as CoroutineContext,
                )

                val bundle = useCase(flowOf(createInfoBundle(SUBJECT_ID))).filterNotNull().first()
                assertEquals("web1", bundle.mediaSelector.mediaSourceId.finalSelected.first())
                assertEquals("桜都字幕组", bundle.mediaSelector.alliance.finalSelected.first())

                // 必须是 flow 接线而非一次性快照: 落库更新要实时跟随
                repository.setMediaPreference(
                    SUBJECT_ID,
                    MediaPreference.Empty.copy(mediaSourceId = "web2"),
                )
                assertEquals("web2", bundle.mediaSelector.mediaSourceId.finalSelected.first())

                // 必须按 bundle.subjectId 读: 其他条目读不到本条目的偏好
                val other = useCase(flowOf(createInfoBundle(SUBJECT_ID + 1))).filterNotNull().first()
                assertNull(other.mediaSelector.mediaSourceId.finalSelected.first())
            } finally {
                stopKoin()
            }
        }

    private fun createInfoBundle(subjectId: Int): SubjectEpisodeInfoBundle {
        val collection = TestSubjectCollections[0].run {
            copy(subjectInfo = subjectInfo.copy(subjectId = subjectId))
        }
        val episode = collection.episodes[0]
        return SubjectEpisodeInfoBundle(
            subjectId,
            episode.episodeInfo.episodeId,
            collection,
            episode,
            seriesInfo = SubjectSeriesInfo.Fallback,
            subjectCompleted = false,
        )
    }

    private fun <T> staticSettings(value: T): Settings<T> = object : Settings<T> {
        override val flow: Flow<T> = MutableStateFlow(value)
        override suspend fun set(value: T) = error("not used")
    }
}
