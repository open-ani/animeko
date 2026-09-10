/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.download

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.domain.media.TestMediaList

class DownloadMediaPreferenceTest {
    @Test
    fun `manual selections persist preferences including subsequent episodes`() = runTest {
        val saved = mutableListOf<MediaPreference>()
        val media = TestMediaList.first()
        val first = testDownloadSelection(1).selector
        selectMediaAndSavePreference(first, media) { saved += it }
        selectMediaAndSavePreference(first, media) { saved += it }
        assertEquals(1, saved.size)
        selectMediaAndSavePreference(testDownloadSelection(2).selector, media) { saved += it }
        assertEquals(2, saved.size)
    }
}
