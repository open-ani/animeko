/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.tv.ui.settings

import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.models.danmaku.DanmakuRegexFilter
import me.him188.ani.app.data.models.preference.NsfwMode
import me.him188.ani.app.data.persistent.MemoryDataStore
import me.him188.ani.app.data.repository.media.MediaSourceSubscriptionRepository
import me.him188.ani.app.data.repository.media.MediaSourceSubscriptionsSaveData
import me.him188.ani.app.data.repository.player.DanmakuRegexFilterRepositoryImpl
import me.him188.ani.app.data.repository.user.PreferencesRepositoryImpl
import me.him188.ani.app.domain.media.fetch.MediaFetcher
import me.him188.ani.app.domain.media.fetch.MediaSourceManager
import me.him188.ani.app.domain.media.selector.MediaSelectorSourceTiers
import me.him188.ani.app.domain.mediasource.instance.MediaSourceInstance
import me.him188.ani.app.domain.mediasource.instance.MediaSourceSave
import me.him188.ani.app.domain.mediasource.codec.MediaSourceCodecManager
import me.him188.ani.app.domain.mediasource.codec.ExportedMediaSourceDataList
import me.him188.ani.app.domain.mediasource.subscription.MediaSourceSubscription
import me.him188.ani.app.domain.mediasource.subscription.MediaSourceSubscriptionUpdater
import me.him188.ani.app.domain.mediasource.subscription.SubscriptionUpdateData
import me.him188.ani.datasources.api.matcher.MediaSourceWebVideoMatcherLoader
import me.him188.ani.datasources.api.source.FactoryId
import me.him188.ani.datasources.api.source.MediaSourceConfig
import me.him188.ani.datasources.api.source.MediaSourceFactory
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(TestOnly::class)
class TvSettingsViewModelTest {
    @Test
    fun editsPersistAcrossRepositoryRecreationAndPreserveUnrelatedFields() = runTest {
        val store = MemoryDataStore(emptyPreferences())
        val repository = PreferencesRepositoryImpl(store)
        repository.videoScaffoldConfig.update { copy(playbackSpeed = 2.5f, fastForwardSpeed = 3f, autoMarkDone = false) }
        val vm = TvSettingsViewModel(
            repository, DanmakuRegexFilterRepositoryImpl(MemoryDataStore(emptyList())), TestSources(),
            MediaSourceSubscriptionRepository(MemoryDataStore(MediaSourceSubscriptionsSaveData.Default.copy(list = emptyList()))), { emptyList() }, StandardTestDispatcher(testScheduler),
        )
        try {
            vm.onIntent(TvSettingsIntent.Video { copy(autoPlayNext = false) })
            vm.onIntent(TvSettingsIntent.Video { withPlaybackSpeedRange(.5f..1.5f) })
            vm.onIntent(TvSettingsIntent.Appearance { copy(searchSettings = searchSettings.copy(nsfwMode = NsfwMode.HIDE)) })
            vm.onIntent(TvSettingsIntent.Appearance {
                copy(searchSettings = searchSettings.copy(ignoreDoneAndDroppedSubjects = true))
            })
            vm.onIntent(TvSettingsIntent.Theme { copy(seedColorValue = 123uL, useDynamicTheme = false) })
            runCurrent()
            val restored = PreferencesRepositoryImpl(store)
            val video = restored.videoScaffoldConfig.flow.first()
            assertFalse(video.autoPlayNext)
            assertFalse(video.autoMarkDone)
            assertEquals(1.5f, video.playbackSpeed)
            assertEquals(1.5f, video.fastForwardSpeed)
            assertEquals(.5f, video.minPlaybackSpeed)
            assertEquals(1.5f, video.maxPlaybackSpeed)
            assertEquals(NsfwMode.HIDE, restored.uiSettings.flow.first().searchSettings.nsfwMode)
            assertTrue(restored.uiSettings.flow.first().searchSettings.ignoreDoneAndDroppedSubjects)
            assertEquals(123uL, restored.themeSettings.flow.first().seedColorValue)
        } finally {
            vm.backgroundScope.cancel()
        }
    }

    @Test
    fun invalidRegexIsRejectedAndImportExportUsesTheSharedFormat() = runTest {
        val regex = DanmakuRegexFilterRepositoryImpl(MemoryDataStore(emptyList()))
        val vm = TvSettingsViewModel(
            PreferencesRepositoryImpl(MemoryDataStore(emptyPreferences())), regex, TestSources(),
            MediaSourceSubscriptionRepository(MemoryDataStore(MediaSourceSubscriptionsSaveData.Default.copy(list = emptyList()))), { emptyList() }, StandardTestDispatcher(testScheduler),
        )
        try {
            vm.onIntent(TvSettingsIntent.SaveRegex(DanmakuRegexFilter("bad", regex = "["), true))
            runCurrent()
            assertTrue(regex.flow.first().isEmpty())
            val rule = DanmakuRegexFilter("good", "Spoilers", "spoiler.*")
            vm.onIntent(TvSettingsIntent.SaveRegex(rule, true))
            vm.onIntent(TvSettingsIntent.ExportRegex)
            runCurrent()
            val exported = assertIs<TvSettingsEvent.Copy>(vm.events.first()).text
            vm.onIntent(TvSettingsIntent.ImportRegex("invalid"))
            runCurrent()
            assertEquals(TvSettingsEvent.ImportFailed, vm.events.first())
            assertEquals(listOf(rule), regex.flow.first())
            vm.onIntent(TvSettingsIntent.RemoveRegex(rule))
            vm.onIntent(TvSettingsIntent.ImportRegex(exported))
            runCurrent()
            assertEquals(TvSettingsEvent.ImportSucceeded, vm.events.first())
            assertEquals(listOf(rule), regex.flow.first())
        } finally {
            vm.backgroundScope.cancel()
        }
    }

    @Test
    fun failedLoadingCanBeRetriedWithoutReconstructingThePage() = runTest {
        var fail = true
        val sources = TestSources(flow {
            if (fail) error("source storage unavailable")
            emit(emptyList())
        })
        val vm = TvSettingsViewModel(
            PreferencesRepositoryImpl(MemoryDataStore(emptyPreferences())),
            DanmakuRegexFilterRepositoryImpl(MemoryDataStore(emptyList())), sources,
            MediaSourceSubscriptionRepository(MemoryDataStore(MediaSourceSubscriptionsSaveData.Default.copy(list = emptyList()))), { emptyList() }, StandardTestDispatcher(testScheduler),
        )
        val collection = backgroundScope.launch(StandardTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        try {
            runCurrent()
            assertTrue(vm.uiState.value.loadFailed)
            fail = false
            vm.onIntent(TvSettingsIntent.Retry)
            runCurrent()
            assertTrue(vm.uiState.value.loaded)
            assertFalse(vm.uiState.value.loadFailed)
        } finally {
            collection.cancel()
            vm.backgroundScope.cancel()
        }
    }

    @Test
    fun subscriptionSwitchPersistsAndUpdatesOnlyItsSourcesWhileIndividualSwitchesStayIndependent() = runTest {
        val subscriptionStore = MemoryDataStore(MediaSourceSubscriptionsSaveData.Default.copy(list = listOf(
            MediaSourceSubscription("group", "https://example.com/sources.json"),
        )))
        val subscriptions = MediaSourceSubscriptionRepository(subscriptionStore)
        val sources = TestSources(saves = listOf(source("first", "group"), source("second", "group", false), source("other", null)))
        val vm = TvSettingsViewModel(
            PreferencesRepositoryImpl(MemoryDataStore(emptyPreferences())),
            DanmakuRegexFilterRepositoryImpl(MemoryDataStore(emptyList())), sources, subscriptions,
            { emptyList() }, StandardTestDispatcher(testScheduler),
        )
        try {
            vm.onIntent(TvSettingsIntent.SubscriptionEnabled("group", false))
            runCurrent()
            assertEquals(listOf(false, false, true), sources.saves.map { it.isEnabled })
            assertFalse(MediaSourceSubscriptionRepository(subscriptionStore).flow.first().single().enabled)
            vm.onIntent(TvSettingsIntent.SourceEnabled("first", true))
            runCurrent()
            assertEquals(listOf(true, false, true), sources.saves.map { it.isEnabled })
            assertFalse(subscriptions.flow.first().single().enabled)
            vm.onIntent(TvSettingsIntent.SubscriptionEnabled("group", true))
            runCurrent()
            assertTrue(sources.saves.all { it.isEnabled })
            assertTrue(subscriptions.flow.first().single().enabled)
            vm.onIntent(TvSettingsIntent.SourceEnabled("second", false))
            runCurrent()
            assertEquals(listOf(true, false, true), sources.saves.map { it.isEnabled })
            assertTrue(subscriptions.flow.first().single().enabled)
        } finally {
            vm.backgroundScope.cancel()
        }
    }

    @Test
    fun disablingASubscriptionDuringDownloadPreventsReconciliationAndFurtherRefreshes() = runTest {
        val subscriptions = MediaSourceSubscriptionRepository(MemoryDataStore(MediaSourceSubscriptionsSaveData.Default.copy(
            list = listOf(MediaSourceSubscription("group", "https://example.com/sources.json")),
        )))
        val sources = TestSources(saves = listOf(source("first", "group")))
        val downloaded = CompletableDeferred<Unit>()
        var requests = 0
        val updater = MediaSourceSubscriptionUpdater(subscriptions, sources, MediaSourceCodecManager()) {
            requests++
            downloaded.await()
            SubscriptionUpdateData(ExportedMediaSourceDataList(emptyList()))
        }
        val vm = TvSettingsViewModel(
            PreferencesRepositoryImpl(MemoryDataStore(emptyPreferences())),
            DanmakuRegexFilterRepositoryImpl(MemoryDataStore(emptyList())), sources, subscriptions,
            { emptyList() }, StandardTestDispatcher(testScheduler),
        )
        try {
            val update = launch { updater.updateAllOutdated(force = true) }
            runCurrent()
            assertEquals(1, requests)
            vm.onIntent(TvSettingsIntent.SubscriptionEnabled("group", false))
            runCurrent()
            downloaded.complete(Unit)
            runCurrent()
            update.join()
            assertFalse(sources.saves.single().isEnabled)
            assertNull(subscriptions.flow.first().single().lastUpdated)
            updater.updateAllOutdated(force = true)
            assertEquals(1, requests)
        } finally {
            vm.backgroundScope.cancel()
        }
    }

    @Test
    fun failedSubscriptionSwitchReportsFailureWithoutChangingItsPersistedState() = runTest {
        val subscriptions = MediaSourceSubscriptionRepository(MemoryDataStore(MediaSourceSubscriptionsSaveData.Default.copy(
            list = listOf(MediaSourceSubscription("group", "https://example.com/sources.json")),
        )))
        val sources = TestSources(saves = listOf(source("first", "group")), failWrites = true)
        val vm = TvSettingsViewModel(
            PreferencesRepositoryImpl(MemoryDataStore(emptyPreferences())),
            DanmakuRegexFilterRepositoryImpl(MemoryDataStore(emptyList())), sources, subscriptions,
            { emptyList() }, StandardTestDispatcher(testScheduler),
        )
        try {
            vm.onIntent(TvSettingsIntent.SubscriptionEnabled("group", false))
            runCurrent()
            assertEquals(TvSettingsEvent.SaveFailed, vm.events.first())
            assertTrue(subscriptions.flow.first().single().enabled)
            assertTrue(sources.saves.single().isEnabled)
        } finally {
            vm.backgroundScope.cancel()
        }
    }

    private fun source(id: String, subscription: String?, enabled: Boolean = true) = MediaSourceSave(
        id, id, FactoryId("web-selector"), enabled, MediaSourceConfig(subscriptionId = subscription),
    )

    private class TestSources(
        override val allInstances: Flow<List<MediaSourceInstance>> = flowOf(emptyList()),
        var saves: List<MediaSourceSave> = emptyList(),
        val failWrites: Boolean = false,
    ) : MediaSourceManager {
        override val allFactories: List<MediaSourceFactory> = emptyList()
        override val allFactoryIds: List<FactoryId> = emptyList()
        override val mediaFetcher: Flow<MediaFetcher> = flowOf()
        override val webVideoMatcherLoader = MediaSourceWebVideoMatcherLoader(flowOf(emptyList()))
        override fun instanceConfigFlow(instanceId: String): Flow<MediaSourceConfig?> = flowOf(null)
        override fun mediaSourceTiersFlow() = flowOf(MediaSelectorSourceTiers.Empty)
        override suspend fun addInstance(instanceId: String, mediaSourceId: String, factoryId: FactoryId, config: MediaSourceConfig) =
            error("Read-only sources")
        override suspend fun getListBySubscriptionId(subscriptionId: String) = saves.filter { it.config.subscriptionId == subscriptionId }
        override suspend fun partiallyReorderInstances(instanceIds: List<String>) = error("Read-only sources")
        override suspend fun updateConfig(instanceId: String, config: MediaSourceConfig) = error("Read-only sources")
        override suspend fun setEnabled(instanceId: String, enabled: Boolean) = setEnabled(listOf(instanceId), enabled)
        override suspend fun setEnabled(instanceIds: Collection<String>, enabled: Boolean) {
            check(!failWrites) { "Source storage unavailable" }
            saves = saves.map { if (it.instanceId in instanceIds) it.copy(isEnabled = enabled) else it }
        }
        override suspend fun removeInstance(instanceId: String) = error("Read-only sources")
    }
}
