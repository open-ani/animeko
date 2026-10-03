/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.app.domain.settings.remote

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.getAndUpdate
import me.him188.ani.app.data.repository.user.Settings

class RemotePreferenceRepository(private val session: RemoteSettingsSession) {
    val videoScaffoldConfig =
        preference(RemotePreferencesSnapshot::videoScaffoldConfig, RemotePreference::VideoScaffold)
    val playerKernelConfig =
        preference(RemotePreferencesSnapshot::playerKernelConfig, RemotePreference::PlayerKernel)
    val danmakuFilterConfig =
        preference(RemotePreferencesSnapshot::danmakuFilterConfig, RemotePreference::DanmakuFilter)
    val mediaSelectorSettings =
        preference(
            RemotePreferencesSnapshot::mediaSelectorSettings,
            RemotePreference::MediaSelector,
        )
    val defaultMediaPreference =
        preference(
            RemotePreferencesSnapshot::defaultMediaPreference,
            RemotePreference::MediaSelection,
        )
    val videoResolverSettings =
        preference(
            RemotePreferencesSnapshot::videoResolverSettings,
            RemotePreference::VideoResolver,
        )
    val watchTogetherSettings =
        preference(
            RemotePreferencesSnapshot::watchTogetherSettings,
            RemotePreference::WatchTogether,
        )
    val mediaCacheSettings =
        preference(RemotePreferencesSnapshot::mediaCacheSettings, RemotePreference::MediaCache)

    private fun <T> preference(
        select: (RemotePreferencesSnapshot) -> VersionedValue<T>,
        wrap: (T) -> RemotePreference,
    ): Settings<T> =
        object : Settings<T> {
            /** The newest edit until the TV confirms or rejects it. Later edits are built on it. */
            private val submitted = MutableStateFlow<Edit<T>?>(null)

            override val flow =
                combine(session.snapshot, submitted) { snapshot, edit ->
                        if (edit != null) edit.value else select(snapshot.preferences).value
                    }
                    .distinctUntilChanged()

            override suspend fun set(value: T) {
                val edit = Edit(value)
                val previous = submitted.getAndUpdate { edit }
                val base =
                    if (previous != null) previous.value
                    else select(session.snapshot.value.preferences).value
                val write = session.submitPreference(wrap(value), wrap(base))
                write.invokeOnCompletion { submitted.compareAndSet(edit, null) }
                write.await()
            }
        }

    private class Edit<T>(val value: T)
}
