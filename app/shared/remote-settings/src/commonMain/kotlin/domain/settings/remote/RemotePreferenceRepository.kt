/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.app.domain.settings.remote

import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
            override val flow =
                session.snapshot.map { select(it.preferences).value }.distinctUntilChanged()

            override suspend fun set(value: T) {
                session.setPreference(wrap(value))
            }
        }
}
