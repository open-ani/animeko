/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.tv.ui.settings

import androidx.compose.runtime.Stable
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.ui.compose.util.strippedLicenseContent
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.him188.ani.app.data.repository.player.DanmakuRegexFilterRepository
import me.him188.ani.app.data.repository.media.MediaSourceSubscriptionRepository
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.media.fetch.MediaSourceManager
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.app.ui.settings.tabs.about.mergeOpenSourceLibraries

@Stable
class TvSettingsViewModel(
    private val settings: SettingsRepository,
    private val regexRepository: DanmakuRegexFilterRepository,
    private val sourceManager: MediaSourceManager,
    private val subscriptions: MediaSourceSubscriptionRepository,
    private val loadLibraries: suspend () -> List<ByteArray>,
    backgroundCoroutineContext: CoroutineContext = EmptyCoroutineContext,
) : AbstractViewModel(backgroundCoroutineContext) {
    private val eventsChannel = Channel<TvSettingsEvent>(Channel.BUFFERED)
    val events = eventsChannel.receiveAsFlow()
    private val reload = MutableStateFlow(0)
    private val libraries = MutableStateFlow<List<TvSettingsLibrary>>(emptyList())
    private val librariesLoading = MutableStateFlow(false)
    private val librariesFailed = MutableStateFlow(false)

    private val base = combine(
        settings.uiSettings.flow, settings.themeSettings.flow, settings.videoScaffoldConfig.flow,
        settings.playerKernelConfig.flow, settings.danmakuEnabled.flow,
    ) { appearance, theme, video, kernel, danmaku ->
        TvSettingsUiState(appearance = appearance, theme = theme, video = video, kernel = kernel, danmakuEnabled = danmaku)
    }
    private val preferences = combine(
        base, settings.defaultMediaPreference.flow, settings.mediaSelectorSettings.flow, settings.videoResolverSettings.flow,
    ) { state, preference, selector, resolver ->
        state.copy(preference = preference, selector = selector, resolver = resolver)
    }
    private val sources = sourceManager.allInstances.map { instances ->
        instances.filterNot { sourceManager.isLocal(it.factoryId) }.map {
            TvSettingsSource(
                it.instanceId, it.source.info.displayName, it.source.info.description.orEmpty(), it.source.info.websiteUrl.orEmpty(),
                it.isEnabled, it.factoryId.value, it.config.subscriptionId,
            )
        }
    }
    private val settingsFlow = combine(
        preferences, settings.danmakuFilterConfig.flow, regexRepository.flow, sources, subscriptions.flow,
    ) { state, filter, regexFilters, sources, subscriptions ->
        state.copy(
            loaded = true, filter = filter, regexFilters = regexFilters, sources = sources,
            subscriptions = subscriptions.map { TvSettingsSubscription(it.subscriptionId, it.url, it.enabled) },
        )
    }
    val uiState = combine(
        reload.flatMapLatest { settingsFlow.catch { emit(TvSettingsUiState(loadFailed = true)) } },
        libraries, librariesLoading, librariesFailed,
    ) { state, libraries, loading, failed ->
        state.copy(libraries = libraries, librariesLoading = loading, librariesFailed = failed)
    }.stateIn(backgroundScope, SharingStarted.WhileSubscribed(5_000), TvSettingsUiState())

    fun onIntent(intent: TvSettingsIntent) {
        if (intent == TvSettingsIntent.Retry) {
            reload.update { it + 1 }
            return
        }
        backgroundScope.launch {
            try {
                when (intent) {
                    is TvSettingsIntent.Appearance -> settings.uiSettings.update(intent.update)
                    is TvSettingsIntent.Theme -> settings.themeSettings.update(intent.update)
                    is TvSettingsIntent.Video -> settings.videoScaffoldConfig.update(intent.update)
                    is TvSettingsIntent.Kernel -> settings.playerKernelConfig.update(intent.update)
                    is TvSettingsIntent.Filter -> settings.danmakuFilterConfig.update { copy(enableRegexFilter = intent.enabled) }
                    is TvSettingsIntent.Danmaku -> settings.danmakuEnabled.set(intent.enabled)
                    is TvSettingsIntent.Preference -> settings.defaultMediaPreference.update(intent.update)
                    is TvSettingsIntent.Selector -> settings.mediaSelectorSettings.update(intent.update)
                    is TvSettingsIntent.Resolver -> settings.videoResolverSettings.update(intent.update)
                    is TvSettingsIntent.SourceEnabled -> sourceManager.setEnabled(intent.id, intent.enabled)
                    is TvSettingsIntent.SubscriptionEnabled -> subscriptions.update(intent.id) { current ->
                        sourceManager.setEnabled(
                            sourceManager.getListBySubscriptionId(intent.id).map { it.instanceId }, intent.enabled,
                        )
                        current.copy(enabled = intent.enabled)
                    }
                    is TvSettingsIntent.SaveRegex -> {
                        if (intent.filter.regex.isBlank() || !isTvSettingsRegexValid(intent.filter.regex)) return@launch
                        if (intent.isNew) regexRepository.add(intent.filter)
                        else regexRepository.update(intent.filter.id, intent.filter)
                    }
                    is TvSettingsIntent.RemoveRegex -> regexRepository.remove(intent.filter)
                    is TvSettingsIntent.ImportRegex -> eventsChannel.send(
                        if (regexRepository.import(intent.text)) TvSettingsEvent.ImportSucceeded else TvSettingsEvent.ImportFailed,
                    )
                    TvSettingsIntent.ExportRegex -> eventsChannel.send(TvSettingsEvent.Copy(regexRepository.export()))
                    TvSettingsIntent.LoadLibraries -> readLibraries()
                    TvSettingsIntent.Retry -> Unit
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                eventsChannel.send(TvSettingsEvent.SaveFailed)
            }
        }
    }

    private suspend fun readLibraries() {
        if (librariesLoading.value || libraries.value.isNotEmpty()) return
        librariesLoading.value = true
        librariesFailed.value = false
        try {
            val parsed = mergeOpenSourceLibraries(loadLibraries().map { Libs.Builder().withJson(it.decodeToString()).build() })
            libraries.value = parsed.libraries.map { library ->
                TvSettingsLibrary(
                    library.uniqueId, library.name, library.artifactVersion.orEmpty(),
                    library.website?.takeIf(String::isNotBlank) ?: library.scm?.url?.takeIf(String::isNotBlank),
                    library.licenses.joinToString { it.name },
                    library.strippedLicenseContent.ifBlank { library.licenses.mapNotNull { it.url }.joinToString("\n") },
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            librariesFailed.value = true
        } finally {
            librariesLoading.value = false
        }
    }
}

internal fun isTvSettingsRegexValid(value: String): Boolean = runCatching { Regex(value) }.isSuccess
