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
class DefaultMediaSelectorSourceTierSortTest {
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
            next().assert(sourceId = "untiered")
            next().assert(sourceId = "t1")
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
            next().assert(sourceId = "bt1")
            next().assert(sourceId = "t2")
            next().assert(sourceId = "t2")
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
