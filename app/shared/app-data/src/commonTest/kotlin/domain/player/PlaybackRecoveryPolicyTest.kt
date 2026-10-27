/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.player

import me.him188.ani.app.domain.media.DroppedFileMedia
import me.him188.ani.app.domain.media.TestMediaList
import me.him188.ani.datasources.api.source.MediaSourceKind
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackRecoveryPolicyTest {
    @Test
    fun `automatic recovery honors user intent and distinguishes switching from network retry`() {
        val web = TestMediaList[0].copy(kind = MediaSourceKind.WEB)
        val torrent = web.copy(kind = MediaSourceKind.BitTorrent)
        val cache = web.copy(kind = MediaSourceKind.LocalCache)
        val dropped = web.copy(mediaSourceId = DroppedFileMedia.MEDIA_SOURCE_ID)

        // 播放失败可从 BT 转向 WEB，网络恢复只重载当前在线资源。
        for (media in listOf(web, torrent, cache, null)) {
            assertTrue(PlaybackRecoveryPolicy.canSwitchMedia(media, MediaSourceKind.WEB))
            assertFalse(PlaybackRecoveryPolicy.canSwitchMedia(media, MediaSourceKind.WEB, enabled = false))
            assertFalse(PlaybackRecoveryPolicy.canSwitchMedia(media, MediaSourceKind.BitTorrent))
            assertFalse(PlaybackRecoveryPolicy.canSwitchMedia(media, null))
        }
        assertTrue(PlaybackRecoveryPolicy.canRetryAfterNetworkChange(web, MediaSourceKind.WEB, enabled = true))
        for (media in listOf(torrent, cache, dropped, null)) {
            assertFalse(PlaybackRecoveryPolicy.canRetryAfterNetworkChange(media, MediaSourceKind.WEB, enabled = true))
        }
        assertFalse(PlaybackRecoveryPolicy.canSwitchMedia(dropped, MediaSourceKind.WEB))
        assertFalse(PlaybackRecoveryPolicy.canRetryAfterNetworkChange(web, MediaSourceKind.WEB, enabled = false))
        assertFalse(PlaybackRecoveryPolicy.canRetryAfterNetworkChange(web, MediaSourceKind.BitTorrent, enabled = true))
    }
}
