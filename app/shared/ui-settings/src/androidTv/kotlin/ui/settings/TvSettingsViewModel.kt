/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.tv.ui.settings

import androidx.compose.runtime.Stable
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.ui.compose.util.strippedLicenseContent
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
import me.him188.ani.app.ui.settings.SettingsViewModel
import me.him188.ani.app.ui.settings.tabs.about.mergeOpenSourceLibraries
import org.koin.core.Koin
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

@Stable
class TvSettingsViewModel(
    koin: Koin,
    private val loadLibraries: suspend () -> List<ByteArray>,
    backgroundCoroutineContext: CoroutineContext = EmptyCoroutineContext,
) : SettingsViewModel(koin, backgroundCoroutineContext) {
    private val eventsChannel = Channel<TvSettingsEvent>(Channel.BUFFERED)
    val events = eventsChannel.receiveAsFlow()
    private val reload = MutableStateFlow(0)
    private val libraries = MutableStateFlow<List<TvSettingsLibrary>>(emptyList())
    private val librariesLoading = MutableStateFlow(false)
    private val librariesFailed = MutableStateFlow(false)

    private val base = combine(
        appearanceSettingsFlow, themeSettingsFlow, videoSettingsFlow,
        kernelSettingsFlow, danmakuEnabledFlow,
    ) { appearance, theme, video, kernel, danmaku ->
        TvSettingsUiState(appearance = appearance, theme = theme, video = video, kernel = kernel, danmakuEnabled = danmaku)
    }
    private val preferences = combine(
        base, preferenceSettingsFlow, selectorSettingsFlow, resolverSettingsFlow,
    ) { state, preference, selector, resolver ->
        state.copy(preference = preference, selector = selector, resolver = resolver)
    }
    private val sources = mediaSourceInstancesFlow.map { instances ->
        instances.map {
            TvSettingsSource(
                it.instanceId, it.source.info.displayName, it.source.info.description.orEmpty(), it.source.info.websiteUrl.orEmpty(),
                it.isEnabled, it.factoryId.value, it.config.subscriptionId,
            )
        }
    }
    private val settingsFlow = combine(
        preferences, filterSettingsFlow, regexFiltersFlow, sources, mediaSubscriptionsFlow,
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
                    is TvSettingsIntent.Appearance -> updateAppearance(intent.update)
                    is TvSettingsIntent.Theme -> updateTheme(intent.update)
                    is TvSettingsIntent.Video -> updateVideo(intent.update)
                    is TvSettingsIntent.Kernel -> updateKernel(intent.update)
                    is TvSettingsIntent.Filter -> setRegexFilterEnabled(intent.enabled)
                    is TvSettingsIntent.Danmaku -> setDanmakuEnabled(intent.enabled)
                    is TvSettingsIntent.Preference -> updatePreference(intent.update)
                    is TvSettingsIntent.Selector -> updateSelector(intent.update)
                    is TvSettingsIntent.Resolver -> updateResolver(intent.update)
                    is TvSettingsIntent.SourceEnabled -> setSourceEnabled(intent.id, intent.enabled)
                    is TvSettingsIntent.SubscriptionEnabled -> setSubscriptionEnabled(intent.id, intent.enabled)
                    is TvSettingsIntent.SaveRegex -> saveRegexFilter(intent.filter, intent.isNew)
                    is TvSettingsIntent.RemoveRegex -> removeRegexFilter(intent.filter)
                    is TvSettingsIntent.ImportRegex -> eventsChannel.send(
                        if (importRegexFilters(intent.text)) TvSettingsEvent.ImportSucceeded else TvSettingsEvent.ImportFailed,
                    )
                    TvSettingsIntent.ExportRegex -> eventsChannel.send(TvSettingsEvent.Copy(exportRegexFilters()))
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
