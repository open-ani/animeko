/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.remote

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import me.him188.ani.app.data.models.danmaku.DanmakuRegexFilter
import me.him188.ani.app.data.repository.user.Settings
import me.him188.ani.app.domain.settings.remote.RemotePreferencesSnapshot
import me.him188.ani.app.domain.settings.remote.RemoteSettingsSession
import me.him188.ani.app.domain.settings.remote.ReplaceDanmakuFilters
import me.him188.ani.app.domain.settings.remote.VersionedValue
import me.him188.ani.app.ui.foundation.produceState
import me.him188.ani.app.ui.settings.danmaku.DanmakuRegexFilterState
import me.him188.ani.app.ui.settings.framework.SettingsState
import me.him188.ani.app.ui.settings.tabs.media.MediaSelectionGroupState
import me.him188.ani.remote.settings.RemoteSettingsProtocol

/**
 * Settings widgets observe the TV snapshot; their submissions run in the session's own write scope.
 */
class RemoteSettingsFormState(
    private val session: RemoteSettingsSession,
    private val scope: CoroutineScope,
) {

    private val json = RemoteSettingsProtocol.json
    private val filtersSerializer = ListSerializer(DanmakuRegexFilter.serializer())

    private fun <T> state(
        select: (RemotePreferencesSnapshot) -> VersionedValue<T>,
        settings: Settings<T>,
    ): SettingsState<T> {
        val initial = select(session.snapshot.value.preferences).value
        return SettingsState(
            settings.flow.produceState(initial, scope),
            onUpdate = { value -> settings.set(value) },
            initial,
            scope,
            initiallyLoaded = true,
        )
    }

    internal val sources = RemoteMediaSourcesState(session, scope)

    val storage =
        state(
            RemotePreferencesSnapshot::mediaCacheSettings,
            session.preferences.mediaCacheSettings,
        )

    val video =
        state(
            RemotePreferencesSnapshot::videoScaffoldConfig,
            session.preferences.videoScaffoldConfig,
        )
    val kernel =
        state(
            RemotePreferencesSnapshot::playerKernelConfig,
            session.preferences.playerKernelConfig,
        )
    val filter =
        state(
            RemotePreferencesSnapshot::danmakuFilterConfig,
            session.preferences.danmakuFilterConfig,
        )
    val watching =
        state(
            RemotePreferencesSnapshot::watchTogetherSettings,
            session.preferences.watchTogetherSettings,
        )
    val selector =
        state(
            RemotePreferencesSnapshot::mediaSelectorSettings,
            session.preferences.mediaSelectorSettings,
        )
    val resolver =
        state(
            RemotePreferencesSnapshot::videoResolverSettings,
            session.preferences.videoResolverSettings,
        )

    val selection =
        MediaSelectionGroupState(
            state(
                RemotePreferencesSnapshot::defaultMediaPreference,
                session.preferences.defaultMediaPreference,
            ),
            selector,
            resolver,
        )
    private val filters =
        session.snapshot
            .map { it.danmakuFilters.value }
            .produceState(
                session.snapshot.value.danmakuFilters.value,
                scope,
            )

    private fun editFilters(transform: (List<DanmakuRegexFilter>) -> List<DanmakuRegexFilter>) {
        scope.launch { session.danmakuFilters(ReplaceDanmakuFilters(transform(filters.value))) }
    }

    val regexFilters =
        DanmakuRegexFilterState(
            filters,
            add = { item -> editFilters { it + item } },
            edit = { id, item ->
                editFilters { filters -> filters.map { if (it.id == id) item else it } }
            },
            remove = { item -> editFilters { filters -> filters.filterNot { it.id == item.id } } },
            switch = { item ->
                editFilters { filters ->
                    filters.map { if (it.id == item.id) it.copy(enabled = !it.enabled) else it }
                }
            },
            onExport = { json.encodeToString(filtersSerializer, filters.value) },
            onImport = { text ->
                session.danmakuFilters(
                    ReplaceDanmakuFilters(json.decodeFromString(filtersSerializer, text))
                )
                true
            },
        )
}
