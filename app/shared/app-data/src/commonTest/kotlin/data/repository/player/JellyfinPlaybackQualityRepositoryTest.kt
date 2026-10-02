/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.player

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import me.him188.ani.datasources.jellyfin.JellyfinPlaybackQuality
import kotlin.test.Test
import kotlin.test.assertEquals

class JellyfinPlaybackQualityRepositoryTest {
    @Test
    fun `defaults to auto and remembers each Jellyfin instance independently`() = runTest {
        val repository = JellyfinPlaybackQualityRepositoryImpl(InMemoryPreferencesDataStore())

        assertEquals(JellyfinPlaybackQuality.Auto, repository.get("living-room"))
        assertEquals(JellyfinPlaybackQuality.Auto, repository.get("remote-server"))

        repository.set("living-room", JellyfinPlaybackQuality.fixed(8_000_000))
        repository.set("remote-server", JellyfinPlaybackQuality.Original)

        assertEquals(JellyfinPlaybackQuality.fixed(8_000_000), repository.get("living-room"))
        assertEquals(JellyfinPlaybackQuality.Original, repository.get("remote-server"))
    }

    private class InMemoryPreferencesDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())

        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            return transform(state.value).also { state.value = it }
        }
    }
}
