/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.media

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.cash.turbine.test
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.data.persistent.createTestPreferencesDataStore
import me.him188.ani.app.data.persistent.database.dao.createMemoryPreferredWebMediaSourceDao
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EpisodePreferencesRepositoryImplTest {
    private val globalDefault = MediaPreference.Empty.copy(
        alliance = "global-alliance",
        resolution = "1080P",
        showWithoutSubtitle = true,
    )
    private val defaultMediaPreference = MutableStateFlow(globalDefault)

    private fun createRepository(store: DataStore<Preferences>): EpisodePreferencesRepositoryImpl {
        return EpisodePreferencesRepositoryImpl(
            store = store,
            preferredWebMediaSourceDao = createMemoryPreferredWebMediaSourceDao(),
            defaultMediaPreference = defaultMediaPreference,
        )
    }

    @Test
    fun `SAVE-06 set 后 flow 读回 equals`() = runTest {
        val store = createTestPreferencesDataStore()
        val repository = createRepository(store)
        val preference = MediaPreference.Empty.copy(
            alliance = "字幕组A",
            alliancePatterns = listOf("字幕组.*"),
            resolution = "720P",
            subtitleLanguageId = "CHS",
            mediaSourceId = "source-a",
        )

        repository.setMediaPreference(SUBJECT_ID, preference)

        assertEquals(preference, repository.mediaPreferenceFlow(SUBJECT_ID).first())
    }

    @Test
    fun `SAVE-06 无记录或空白串返回全局默认`() = runTest {
        val store = createTestPreferencesDataStore()
        val repository = createRepository(store)

        assertEquals(globalDefault, repository.mediaPreferenceFlow(SUBJECT_ID).first())

        store.edit { it[stringPreferencesKey(SUBJECT_ID.toString())] = " " }
        assertEquals(globalDefault, repository.mediaPreferenceFlow(SUBJECT_ID).first())

        val newDefault = globalDefault.copy(alliance = "new-global-alliance")
        defaultMediaPreference.value = newDefault
        assertEquals(newDefault, repository.mediaPreferenceFlow(SUBJECT_ID).first())
    }

    @Test
    fun `SAVE-06 全局默认变更不推给已有订阅者 只有 store 变更才触发重读`() = runTest {
        val store = createTestPreferencesDataStore()
        val repository = createRepository(store)

        repository.mediaPreferenceFlow(SUBJECT_ID).test {
            assertEquals(globalDefault, awaitItem())

            // PINNED: SAVE-06 的 emission 语义 —— 生产是 `store.data.map { ... defaultMediaPreference.first() }`,
            // 全局默认只在 store 变更时被重新读取. 已有订阅者不会因全局默认变更而收到新 emission.
            // 若改成 combine(store.data, defaultMediaPreference), 存量订阅者会多收一条, 进而触发 selector 全链路重算.
            val newDefault = globalDefault.copy(alliance = "new-global-alliance")
            defaultMediaPreference.value = newDefault
            expectNoEvents()

            // store 变更时才重读全局默认, 且只发射一条
            store.edit { it[stringPreferencesKey(SUBJECT_ID.toString())] = " " }
            assertEquals(newDefault, awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun `ROOM-04 preferredWebMediaSource set get remove 往返`() = runTest {
        val store = createTestPreferencesDataStore()
        val repository = createRepository(store)

        assertNull(repository.getPreferredWebMediaSource(SUBJECT_ID).first())

        repository.setPreferredWebMediaSource(SUBJECT_ID, "web-a")
        assertEquals("web-a", repository.getPreferredWebMediaSource(SUBJECT_ID).first())
        // 按 subjectId 隔离
        assertNull(repository.getPreferredWebMediaSource(SUBJECT_ID + 1).first())

        // 覆盖写
        repository.setPreferredWebMediaSource(SUBJECT_ID, "web-b")
        assertEquals("web-b", repository.getPreferredWebMediaSource(SUBJECT_ID).first())

        repository.removePreferredWebMediaSource(SUBJECT_ID)
        assertNull(repository.getPreferredWebMediaSource(SUBJECT_ID).first())
    }

    @Test
    fun `ROOM-04 preferredWebMediaSource 与 mediaPreference 是两条独立通道`() = runTest {
        val store = createTestPreferencesDataStore()
        val repository = createRepository(store)

        repository.setPreferredWebMediaSource(SUBJECT_ID, "web-a")
        // Room 通道的写入不会进入 DataStore 通道
        assertEquals(globalDefault, repository.mediaPreferenceFlow(SUBJECT_ID).first())

        repository.setMediaPreference(SUBJECT_ID, MediaPreference.Empty.copy(mediaSourceId = "web-b"))
        // DataStore 通道的写入也不会改变 Room 通道
        assertEquals("web-a", repository.getPreferredWebMediaSource(SUBJECT_ID).first())
    }

    @Test
    fun `SAVE-06 坏 JSON 回退全局默认且坏数据保留在 store`() = runTest {
        val store = createTestPreferencesDataStore()
        val repository = createRepository(store)
        val key = stringPreferencesKey(SUBJECT_ID.toString())
        store.edit { it[key] = "not json" }

        assertEquals(globalDefault, repository.mediaPreferenceFlow(SUBJECT_ID).first())
        // PINNED: SAVE-06 反序列化失败只回退全局默认, 不清理坏数据
        assertEquals("not json", store.data.first()[key])
    }

    @Test
    fun `SAVE-06 旧版缺字段 JSON 缺字段用序列化器默认而非全局默认`() = runTest {
        val store = createTestPreferencesDataStore()
        val repository = createRepository(store)
        store.edit { it[stringPreferencesKey(SUBJECT_ID.toString())] = """{"alliance":"Legacy"}""" }

        assertEquals(
            MediaPreference.PlatformDefault.copy(alliance = "Legacy"),
            repository.mediaPreferenceFlow(SUBJECT_ID).first(),
        )
    }

    private companion object {
        private const val SUBJECT_ID = 100
    }
}
