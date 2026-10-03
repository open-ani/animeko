/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.pip

import me.him188.ani.app.data.models.preference.BackgroundBehavior
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PictureInPicturePolicyTest {
    @Test
    fun `ASPECT-01 常规 16 比 9 约分后返回`() {
        assertEquals(16 to 9, computePipAspectRatio(1920, 1080))
        assertEquals(16 to 9, computePipAspectRatio(3840, 2160))
    }

    @Test
    fun `ASPECT-02 宽高任一未知时返回 null 使用系统默认比例`() {
        assertNull(computePipAspectRatio(null, 1080))
        assertNull(computePipAspectRatio(1920, null))
        assertNull(computePipAspectRatio(null, null))
    }

    @Test
    fun `ASPECT-03 宽高非正数时返回 null`() {
        assertNull(computePipAspectRatio(0, 1080))
        assertNull(computePipAspectRatio(1920, 0))
        assertNull(computePipAspectRatio(-1, 1080))
    }

    @Test
    fun `ASPECT-04 过宽视频压缩到 239 比 100`() {
        assertEquals(239 to 100, computePipAspectRatio(3000, 100))
        assertEquals(239 to 100, computePipAspectRatio(2390, 1000))
    }

    @Test
    fun `ASPECT-05 过高视频压缩到 100 比 239`() {
        assertEquals(100 to 239, computePipAspectRatio(100, 3000))
        assertEquals(100 to 239, computePipAspectRatio(1000, 2390))
    }

    @Test
    fun `ASPECT-06 边界值 239 比 100 恰好合法`() {
        assertEquals(239 to 100, computePipAspectRatio(239, 100))
        assertEquals(100 to 239, computePipAspectRatio(100, 239))
    }

    @Test
    fun `AUTO-01 默认行为且播放中才自动进入`() {
        assertTrue(shouldAutoEnterPictureInPicture(BackgroundBehavior.AUTO_PICTURE_IN_PICTURE, playWhenReady = true))
        assertFalse(shouldAutoEnterPictureInPicture(BackgroundBehavior.AUTO_PICTURE_IN_PICTURE, playWhenReady = false))
    }

    @Test
    fun `AUTO-02 暂停行为与后台播放行为不自动进入`() {
        assertFalse(shouldAutoEnterPictureInPicture(BackgroundBehavior.PAUSE, playWhenReady = true))
        assertFalse(shouldAutoEnterPictureInPicture(BackgroundBehavior.BACKGROUND_PLAYBACK, playWhenReady = true))
    }
}
