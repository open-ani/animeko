/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.remote

import androidx.compose.runtime.State
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.danmaku.DanmakuRegexFilter
import me.him188.ani.app.data.repository.player.DanmakuRegexFilterListCodec
import me.him188.ani.app.domain.settings.remote.RemotePreferenceSettings
import me.him188.ani.app.domain.settings.remote.RemoteSettingsBackup
import me.him188.ani.app.domain.settings.remote.RemoteSettingsSession
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
    private val onPartialRestore: () -> Unit = {},
) {

    private val json = RemoteSettingsProtocol.json

    /**
     * 控件在点击时读取 [State.value] 构造新的配置对象并提交. 读取和提交都在点击的线程上同步完成,
     * 因此连续的编辑各自基于前一次编辑的值, 并按点击顺序写入电视.
     */
    private fun <T> state(settings: RemotePreferenceSettings<T>): SettingsState<T> {
        val initial = settings.current
        val observed = settings.flow.produceState(initial, scope)
        val latest =
            object : State<T> {
                override val value: T
                    get() {
                        observed.value // 订阅重组
                        return settings.current
                    }
            }
        return SettingsState(
            latest,
            onUpdate = { value -> settings.set(value) },
            initial,
            scope,
            initiallyLoaded = true,
            updateStart = CoroutineStart.UNDISPATCHED,
        )
    }

    internal val sources = RemoteMediaSourcesState(session, scope)

    suspend fun exportBackup(): String = json.encodeToString(session.exportBackup())

    suspend fun restoreBackup(text: String): Boolean {
        val backup = json.decodeFromString(RemoteSettingsBackup.serializer(), text)
        val plan = session.previewBackup(backup)
        val result = session.applyBackup(plan.planId)
        if (result.failed.isNotEmpty()) onPartialRestore()
        return result.failed.isEmpty()
    }

    val storage =
        state(session.preferences.mediaCacheSettings)

    val video =
        state(session.preferences.videoScaffoldConfig)
    val kernel =
        state(session.preferences.playerKernelConfig)
    val filter =
        state(session.preferences.danmakuFilterConfig)
    val watching =
        state(session.preferences.watchTogetherSettings)
    val selector =
        state(session.preferences.mediaSelectorSettings)
    val resolver =
        state(session.preferences.videoResolverSettings)

    val selection =
        MediaSelectionGroupState(
            state(session.preferences.defaultMediaPreference),
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
        scope.launch { session.editDanmakuFilters(transform) }
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
            onExport = { DanmakuRegexFilterListCodec.encode(filters.value) },
            onImport = { text ->
                val imported = DanmakuRegexFilterListCodec.decode(text)
                session.editDanmakuFilters { imported }
                true
            },
        )
}
