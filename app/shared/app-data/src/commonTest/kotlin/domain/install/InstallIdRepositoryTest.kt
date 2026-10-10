/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.install

import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.persistent.MemoryDataStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

class InstallIdRepositoryTest {
    @Test
    fun `id is not available before load`() {
        val repository = InstallIdRepository(MemoryDataStore(InstallInfo.Initial))

        assertNull(repository.currentId)
    }

    @Test
    fun `load generates a canonical uuid once and keeps it`() = runTest {
        val store = MemoryDataStore(InstallInfo.Initial)
        val repository = InstallIdRepository(store)

        val id = repository.load()

        assertEquals(36, id.length)
        assertEquals(id, Uuid.parse(id).toString())
        assertEquals(id, repository.currentId)
        assertEquals(id, store.data.value.installId)
        assertEquals(id, repository.load())
        // 重启后读到同一个 ID
        assertEquals(id, InstallIdRepository(store).load())
    }

    @Test
    fun `load keeps the saved id and other install info`() = runTest {
        val saved = InstallInfo(installId = "3f2b0c1e-8d4a-4b6f-9a7e-0c5d2e1f3a4b", firstLaunchAtMillis = 123)
        val store = MemoryDataStore(saved)

        assertEquals("3f2b0c1e-8d4a-4b6f-9a7e-0c5d2e1f3a4b", InstallIdRepository(store).load())
        assertEquals(saved, store.data.value)
    }
}
