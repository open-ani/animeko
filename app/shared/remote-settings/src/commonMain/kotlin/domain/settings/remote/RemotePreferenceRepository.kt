/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.app.domain.settings.remote

import kotlinx.coroutines.Deferred
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
    ) = RemotePreferenceSettings(session, select, wrap)
}

/**
 * One TV preference. A form reads [current] and calls [submit] on the same thread, so that every
 * edit is built on the one before it and the edits are written in that order.
 */
class RemotePreferenceSettings<T>
internal constructor(
    private val session: RemoteSettingsSession,
    private val select: (RemotePreferencesSnapshot) -> VersionedValue<T>,
    private val wrap: (T) -> RemotePreference,
) : Settings<T> {
    private class Edit<T>(val value: T)

    /** The newest edit until the TV confirms or rejects it. */
    private val submitted = MutableStateFlow<Edit<T>?>(null)

    private fun confirmed(snapshot: SettingsSnapshot) = select(snapshot.preferences).value

    /** The newest submitted value while its write is in progress, otherwise the TV value. */
    val current: T
        get() {
            val edit = submitted.value
            return if (edit != null) edit.value else confirmed(session.snapshot.value)
        }

    override val flow =
        combine(session.snapshot, submitted) { snapshot, edit ->
                if (edit != null) edit.value else confirmed(snapshot)
            }
            .distinctUntilChanged()

    /** Queues the write of [value], which the caller built from [current]. */
    fun submit(value: T): Deferred<Unit> {
        val edit = Edit(value)
        val previous = submitted.getAndUpdate { edit }
        val base = if (previous != null) previous.value else confirmed(session.snapshot.value)
        return session.submitPreference(wrap(value), wrap(base)).apply {
            invokeOnCompletion { submitted.compareAndSet(edit, null) }
        }
    }

    override suspend fun set(value: T) = submit(value).await()

    override suspend fun update(update: T.() -> T) = set(current.update())
}
