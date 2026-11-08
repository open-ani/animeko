/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.player

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackProgressEvidenceTest {
    @Test
    fun `success requires five seconds of real playback progress`() {
        val evidence = PlaybackProgressEvidence()
        assertFalse(evidence.update(0, 0, true))
        assertFalse(evidence.update(4_999, 4_999, true))
        assertTrue(evidence.update(5_000, 5_000, true))
    }

    @Test
    fun `pause buffering and stalled position do not count as played time`() {
        val evidence = PlaybackProgressEvidence()
        evidence.update(0, 0, true)
        assertFalse(evidence.update(2_000, 2_000, true))
        evidence.update(2_001, 2_000, false)
        evidence.update(20_000, 2_000, false)
        evidence.update(20_001, 2_000, true)
        assertFalse(evidence.update(40_000, 2_000, true))
        assertFalse(evidence.update(42_999, 4_999, true))
        assertTrue(evidence.update(43_000, 5_000, true))
    }

    @Test
    fun `seeking does not turn a position jump into playback success`() {
        val evidence = PlaybackProgressEvidence()
        evidence.update(0, 0, true)
        assertFalse(evidence.update(1_000, 100_000, true))
        evidence.onSeek()
        evidence.update(1_001, 100_000, true)
        assertFalse(evidence.update(6_000, 104_999, true))
        assertTrue(evidence.update(6_001, 105_000, true))
    }
}
