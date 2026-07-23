/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:OptIn(UnsafeOriginalMediaAccess::class)

package me.him188.ani.app.domain.media.selector

import me.him188.ani.app.domain.media.selector.testFramework.MediaSelectorTestSuite
import me.him188.ani.app.domain.media.selector.testFramework.assertMedias
import me.him188.ani.app.domain.media.selector.testFramework.runSimpleMediaSelectorTestSuite
import me.him188.ani.app.domain.media.selector.testFramework.setChannelTiers
import me.him188.ani.app.domain.media.selector.testFramework.setSourceTiers
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.source.MediaSourceKind.BitTorrent
import me.him188.ani.datasources.api.source.MediaSourceKind.WEB
import me.him188.ani.datasources.api.source.MediaSourceLocation
import me.him188.ani.test.DisabledOnNative
import kotlin.test.Test

/**
 * @see DefaultMediaSelector.filteredCandidates
 * @see MediaSelectorSourceTiers
 * @see me.him188.ani.app.domain.mediasource.codec.MediaSourceTier
 */
@DisabledOnNative // TODO: ContextParameters crashes on Native
class MediaSelectorSourceTierSortTest {
    @Test
    fun `tier - basic sorting`() = runSimpleMediaSelectorTestSuite {
        initSubject()
        mediaApi.addMedia(
            media(sourceId = "2", kind = WEB),
            media(sourceId = "1", kind = WEB),
            media(sourceId = "3", kind = WEB),
        )
        setSourceTiers(
            "1" to 0u,
            "2" to 1u,
            "3" to 3u,
        )

        assertMedias {
            next().assert(sourceId = "1")
            next().assert(sourceId = "2")
            next().assert(sourceId = "3")
            assertNoMoreElements()
        }
    }

    @Test
    fun `tier - fallback`() = runSimpleMediaSelectorTestSuite {
        initSubject()
        mediaApi.addMedia(
            media(sourceId = "t0", kind = WEB),
            media(sourceId = "t1", kind = WEB),
            media(sourceId = "t2", kind = WEB),
            media(sourceId = "t3", kind = WEB),
            media(sourceId = "untiered", kind = WEB),
        )
        mediaApi.shuffle()
        setSourceTiers(
            "t0" to 0u,
            "t1" to 1u,
            "t2" to 2u,
            "t3" to 3u,
        )

        assertMedias {
            next().assert(sourceId = "t0")
            next().assert(sourceId = "t1")
            next().assert(sourceId = "untiered")
            next().assert(sourceId = "t2")
            next().assert(sourceId = "t3")
            assertNoMoreElements()
        }
    }

    private fun MediaSelectorTestSuite.initSubject() {
        initSubject("test")
    }

    @Test
    fun `tier - multiple same tier`() = runSimpleMediaSelectorTestSuite {
        initSubject()
        mediaApi.addMedia(
            media(sourceId = "t0", kind = WEB),
            media(sourceId = "t1", kind = WEB),
            media(sourceId = "t2", kind = WEB),
            media(sourceId = "t2", kind = WEB),
            media(sourceId = "t3", kind = WEB),
        )
        mediaApi.shuffle()
        setSourceTiers(
            "t0" to 0u,
            "t1" to 1u,
            "t2" to 2u,
            "t3" to 3u,
        )


        assertMedias {
            next().assert(sourceId = "t0")
            next().assert(sourceId = "t1")
            next().assert(sourceId = "t2")
            next().assert(sourceId = "t2")
            next().assert(sourceId = "t3")
            assertNoMoreElements()
        }
    }

    @Test
    fun `tier - with BT but no preference`() = runSimpleMediaSelectorTestSuite {
        initSubject()
        mediaApi.addMedia(
            media(sourceId = "t0", kind = WEB),
            media(sourceId = "t1", kind = WEB),
            media(sourceId = "t2", kind = WEB),
            media(sourceId = "t2", kind = WEB),
            media(sourceId = "t3", kind = WEB),
            media(sourceId = "bt1", kind = BitTorrent), // becomes just fallback
        )
        mediaApi.shuffle()
        setSourceTiers(
            "t0" to 0u,
            "t1" to 1u,
            "t2" to 2u,
            "t3" to 3u,
        )
        preferenceApi.preferKind(null) // default pref is WEB, so we set to null

        assertMedias {
            next().assert(sourceId = "t0")
            next().assert(sourceId = "t1")
            next().assert(sourceId = "t2")
            next().assert(sourceId = "t2")
            next().assert(sourceId = "bt1")
            next().assert(sourceId = "t3")
            assertNoMoreElements()
        }
    }

    @Test
    fun `tier - prefer BT`() = runSimpleMediaSelectorTestSuite {
        initSubject()
        mediaApi.addMedia(
            media(sourceId = "t0", kind = WEB),
            media(sourceId = "t1", kind = WEB),
            media(sourceId = "t2", kind = WEB),
            media(sourceId = "t2", kind = WEB),
            media(sourceId = "t3", kind = WEB),
            media(sourceId = "bt1", kind = BitTorrent),
        )
        mediaApi.shuffle()
        setSourceTiers(
            "t0" to 0u,
            "t1" to 1u,
            "t2" to 2u,
            "t3" to 3u,
        )
        preferenceApi.preferKind(BitTorrent)

        assertMedias {
            next().assert(sourceId = "bt1")
            next().assert(sourceId = "t0")
            next().assert(sourceId = "t1")
            next().assert(sourceId = "t2")
            next().assert(sourceId = "t2")
            next().assert(sourceId = "t3")
            assertNoMoreElements()
        }
    }

    @Test
    fun `tier - prefer Web`() = runSimpleMediaSelectorTestSuite {
        initSubject()
        mediaApi.addMedia(
            media(sourceId = "t0", kind = WEB),
            media(sourceId = "t1", kind = WEB),
            media(sourceId = "t2", kind = WEB),
            media(sourceId = "t2", kind = WEB),
            media(sourceId = "t3", kind = WEB),
            media(sourceId = "bt1", kind = BitTorrent),
        )
        mediaApi.shuffle()
        setSourceTiers(
            "t0" to 0u,
            "t1" to 1u,
            "t2" to 2u,
            "t3" to 3u,
        )
        preferenceApi.preferKind(WEB)

        assertMedias {
            next().assert(sourceId = "t0")
            next().assert(sourceId = "t1")
            next().assert(sourceId = "t2")
            next().assert(sourceId = "t2")
            next().assert(sourceId = "t3")
            next().assert(sourceId = "bt1")
            assertNoMoreElements()
        }
    }

    @Test
    fun `tier - local cache`() = runSimpleMediaSelectorTestSuite {
        initSubject()
        mediaApi.addMedia(
            media(sourceId = "t0", kind = WEB),
            media(sourceId = "t1", kind = WEB),
            media(sourceId = "t2", kind = WEB),
            media(sourceId = "t2", kind = WEB),
            media(sourceId = "t3", kind = WEB),
            media(sourceId = "cache", kind = MediaSourceKind.LocalCache),
        )
        mediaApi.shuffle()
        setSourceTiers(
            "t0" to 0u,
            "t1" to 1u,
            "t2" to 2u,
            "t3" to 3u,
        )
        preferenceApi.preferKind(WEB)

        assertMedias {
            next().assert(sourceId = "cache")
            next().assert(sourceId = "t0")
            next().assert(sourceId = "t1")
            next().assert(sourceId = "t2")
            next().assert(sourceId = "t2")
            next().assert(sourceId = "t3")
            assertNoMoreElements()
        }
    }

    @Test
    fun `channel tier - channels within one source sorted by channel tier`() = runSimpleMediaSelectorTestSuite {
        initSubject()
        mediaApi.addMedia(
            media(sourceId = "s", kind = WEB, alliance = "channel-b", mediaId = "s.b"),
            media(sourceId = "s", kind = WEB, alliance = "channel-a", mediaId = "s.a"),
            media(sourceId = "s", kind = WEB, alliance = "channel-c", mediaId = "s.c"),
        )
        mediaApi.shuffle()
        setSourceTiers("s" to 1u)
        setChannelTiers(
            "s",
            "channel-a" to 0u,
            "channel-b" to 2u,
            // channel-c falls back to source tier 1
        )

        assertMedias {
            next().assert(mediaId = "s.a")
            next().assert(mediaId = "s.c")
            next().assert(mediaId = "s.b")
            assertNoMoreElements()
        }
    }

    @Test
    fun `channel tier - channel tier participates in cross-source sorting`() = runSimpleMediaSelectorTestSuite {
        // 用户场景: A 源的 channel A/B 是 tier 0, B 源的 channel C 是 tier 1
        initSubject()
        mediaApi.addMedia(
            media(sourceId = "B", kind = WEB, alliance = "channel-c", mediaId = "B.c"),
            media(sourceId = "A", kind = WEB, alliance = "channel-a", mediaId = "A.a"),
            media(sourceId = "A", kind = WEB, alliance = "channel-b", mediaId = "A.b"),
        )
        setSourceTiers(
            "A" to 3u,
            "B" to 3u,
        )
        setChannelTiers("A", "channel-a" to 0u, "channel-b" to 0u)
        setChannelTiers("B", "channel-c" to 1u)

        assertMedias {
            // A 源的两个 tier 0 channel 在前 (稳定排序保持加入顺序), B 源的 tier 1 channel 其次
            next().assert(mediaId = "A.a")
            next().assert(mediaId = "A.b")
            next().assert(mediaId = "B.c")
            assertNoMoreElements()
        }
    }

    @Test
    fun `channel tier - demoted channel ranks after other source`() = runSimpleMediaSelectorTestSuite {
        // 数据源整体 tier 0, 但单个 channel 被降级到 2, 应排在 tier 1 的其他源之后
        initSubject()
        mediaApi.addMedia(
            media(sourceId = "A", kind = WEB, alliance = "bad-channel", mediaId = "A.bad"),
            media(sourceId = "B", kind = WEB, alliance = "channel", mediaId = "B.ch"),
        )
        setSourceTiers(
            "A" to 0u,
            "B" to 1u,
        )
        setChannelTiers("A", "bad-channel" to 2u)

        assertMedias {
            next().assert(mediaId = "B.ch")
            next().assert(mediaId = "A.bad")
            assertNoMoreElements()
        }
    }

    @Test
    fun `tier - LAN`() = runSimpleMediaSelectorTestSuite {
        initSubject()
        mediaApi.addMedia(
            media(sourceId = "t0", kind = WEB),
            media(sourceId = "t1", kind = WEB),
            media(sourceId = "t2", kind = WEB),
            media(sourceId = "t2", kind = WEB),
            media(sourceId = "t3", kind = WEB),
            media(sourceId = "local", kind = WEB, location = MediaSourceLocation.Local),
            media(sourceId = "lan", kind = WEB, location = MediaSourceLocation.Lan),
        )
        mediaApi.shuffle()
        setSourceTiers(
            "t0" to 0u,
            "t1" to 1u,
            "t2" to 2u,
            "t3" to 3u,
        )
        preferenceApi.preferKind(WEB)

        assertMedias {
            next().assert(sourceId = "local")
            next().assert(sourceId = "lan")
            next().assert(sourceId = "t0")
            next().assert(sourceId = "t1")
            next().assert(sourceId = "t2")
            next().assert(sourceId = "t2")
            next().assert(sourceId = "t3")
            assertNoMoreElements()
        }
    }
}
