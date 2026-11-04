/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.user

import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.models.preference.NsfwMode
import me.him188.ani.app.data.persistent.createTestPreferencesDataStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchFilterPreferencesTest {
    @Test
    fun `existing search preferences start with all filter groups collapsed`() = runTest {
        val store = createTestPreferencesDataStore(
            preferencesOf(
                stringPreferencesKey("uiSettings") to
                        """{"searchSettings":{"ignoreDoneAndDroppedSubjects":true,"nsfwMode":"HIDE"}}""",
            ),
        )

        val settings = PreferencesRepositoryImpl(store).uiSettings.flow.first().searchSettings

        assertEquals(emptySet(), settings.expandedFilterGroups)
        assertTrue(settings.ignoreDoneAndDroppedSubjects)
        assertEquals(NsfwMode.HIDE, settings.nsfwMode)
    }

    @Test
    fun `expanded filter groups survive preference reload and preserve other settings`() = runTest {
        val store = createTestPreferencesDataStore()
        val settings = PreferencesRepositoryImpl(store).uiSettings
        settings.update {
            copy(searchSettings = searchSettings.copy(expandedFilterGroups = setOf("genre", "setting")))
        }
        settings.update {
            copy(searchSettings = searchSettings.copy(nsfwMode = NsfwMode.HIDE, ignoreDoneAndDroppedSubjects = true))
        }
        settings.update {
            copy(
                searchSettings = searchSettings.copy(
                    expandedFilterGroups = searchSettings.expandedFilterGroups - "genre",
                ),
            )
        }

        val restoredStore = createTestPreferencesDataStore(store.data.first())
        val restored = PreferencesRepositoryImpl(restoredStore).uiSettings.flow.first().searchSettings

        assertEquals(setOf("setting"), restored.expandedFilterGroups)
        assertEquals(NsfwMode.HIDE, restored.nsfwMode)
        assertTrue(restored.ignoreDoneAndDroppedSubjects)
    }
}
