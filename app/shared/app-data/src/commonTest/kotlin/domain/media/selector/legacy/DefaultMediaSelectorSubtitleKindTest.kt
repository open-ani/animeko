/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.selector.legacy

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.domain.media.selector.MediaExclusionReason
import me.him188.ani.app.domain.media.selector.MediaSelectorSubtitlePreferences
import me.him188.ani.app.domain.media.selector.SubtitleKindPreference
import me.him188.ani.datasources.api.DefaultMedia
import me.him188.ani.datasources.api.SubtitleKind
import me.him188.ani.utils.platform.Arch
import me.him188.ani.utils.platform.Platform
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * @suppress 已弃用, 新的 test 使用 [me.him188.ani.app.domain.media.selector.testFramework.TestMediaFetchSessionBuilder].
 * @see me.him188.ani.app.domain.media.selector.MediaSelectorAutoSelect
 */
class DefaultMediaSelectorSubtitleKindTest : AbstractDefaultMediaSelectorTest() {
    @Test
    fun `AllNormal does not hide`() = runTest {
        setSubtitlePreferences(MediaSelectorSubtitlePreferences.Companion.AllNormal)
        val target: DefaultMedia
        addMedia(
            media(alliance = "字幕组1").also { target = it },
            media(alliance = "字幕组2"),
            media(alliance = "字幕组3"),
            media(alliance = "字幕组4"),
            media(alliance = "字幕组5"),
        )
        savedDefaultPreference.value = DEFAULT_PREFERENCE
        assertEquals(target, selector.trySelectDefault())
    }

    @Test
    fun `does not select hidden`() = runTest {
        setSubtitlePreference(SubtitleKind.CLOSED, SubtitleKindPreference.HIDE)
        val target: DefaultMedia
        addMedia(
            media(alliance = "字幕组1", subtitleKind = SubtitleKind.CLOSED),
            media(alliance = "字幕组2").also { target = it },
            media(alliance = "字幕组3"),
            media(alliance = "字幕组4"),
            media(alliance = "字幕组5"),
        )
        savedDefaultPreference.value = DEFAULT_PREFERENCE
        assertEquals(target, selector.trySelectDefault())
    }

    @Test
    fun `low priority ones are selected last`() = runTest {
        setSubtitlePreference(SubtitleKind.CLOSED, SubtitleKindPreference.LOW_PRIORITY)
        val target: DefaultMedia
        addMedia(
            media(alliance = "字幕组1", subtitleKind = SubtitleKind.CLOSED),
            media(alliance = "字幕组2").also { target = it },
            media(alliance = "字幕组3"),
            media(alliance = "字幕组4"),
            media(alliance = "字幕组5"),
        )
        savedDefaultPreference.value = DEFAULT_PREFERENCE
        assertEquals(target, selector.trySelectDefault())
    }

    @Test
    fun `hidden items are not in mediaList`() = runTest {
        setSubtitlePreference(SubtitleKind.CLOSED, SubtitleKindPreference.HIDE)
        addMedia(
            media(alliance = "字幕组1", subtitleKind = SubtitleKind.CLOSED),
            media(alliance = "字幕组2"),
            media(alliance = "字幕组3"),
            media(alliance = "字幕组4"),
            media(alliance = "字幕组5"),
        )
        selector.filteredCandidates.first().run {
            assertEquals(5, size)
            assertEquals(MediaExclusionReason.UnsupportedByPlatformPlayer, get(4).exclusionReason)
        }
        assertEquals(4, selector.filteredCandidatesMedia.first().size)
    }

    @Test
    fun `hidden items are not in filteredCandidates`() = runTest {
        setSubtitlePreference(SubtitleKind.CLOSED, SubtitleKindPreference.HIDE)
        addMedia(
            media(alliance = "字幕组1", subtitleKind = SubtitleKind.CLOSED),
            media(alliance = "字幕组2"),
            media(alliance = "字幕组3"),
            media(alliance = "字幕组4"),
            media(alliance = "字幕组5"),
        )
        assertEquals(4, selector.preferredCandidatesMedia.first().size)
    }
}

/**
 * 测试各个平台的默认设置
 *
 * @see SubtitleKind
 */
sealed class DefaultMediaSelectorSubtitleKindPlatformTest(
    platform: Platform
) : AbstractDefaultMediaSelectorTest() {
    class MacOS : DefaultMediaSelectorSubtitleKindPlatformTest(Platform.MacOS(Arch.AARCH64)) {
        @Test
        fun `does not select CLOSED_OR_EXTERNAL_DISCOVER`() = runTest {
            val target: DefaultMedia
            addMedia(
                media(alliance = "字幕组1", subtitleKind = SubtitleKind.CLOSED_OR_EXTERNAL_DISCOVER),
                media(alliance = "字幕组2").also { target = it },
                media(alliance = "字幕组3"),
                media(alliance = "字幕组4"),
                media(alliance = "字幕组5"),
            )
            savedDefaultPreference.value = DEFAULT_PREFERENCE
            assertEquals(target, selector.trySelectDefault())
        }

        @Test
        fun `does not select CLOSED`() = runTest {
            val target: DefaultMedia
            addMedia(
                media(alliance = "字幕组1", subtitleKind = SubtitleKind.CLOSED),
                media(alliance = "字幕组2").also { target = it },
                media(alliance = "字幕组3"),
                media(alliance = "字幕组4"),
                media(alliance = "字幕组5"),
            )
            savedDefaultPreference.value = DEFAULT_PREFERENCE
            assertEquals(target, selector.trySelectDefault())
        }
    }

    class Windows : DefaultMediaSelectorSubtitleKindPlatformTest(Platform.Windows(Arch.X86_64))
    class Android : DefaultMediaSelectorSubtitleKindPlatformTest(Platform.Android(Arch.ARMV8A)) {
        @Test
        fun `CLOSED is low priority`() = runTest {
            val target: DefaultMedia
            addMedia(
                media(alliance = "字幕组1", subtitleKind = SubtitleKind.CLOSED),
                media(alliance = "字幕组2").also { target = it },
            )
            savedDefaultPreference.value = DEFAULT_PREFERENCE
            assertEquals(target, selector.trySelectDefault())
        }
    }

    // TODO: Test DefaultMediaSelectorSubtitleKindPlatformTest for iOS

    init {
        setSubtitlePreferences(MediaSelectorSubtitlePreferences.Companion.forPlatform(platform))
    }

    @Test
    fun `does not select EXTERNAL_DISCOVER`() = runTest {
        val target: DefaultMedia
        addMedia(
            media(alliance = "字幕组1", subtitleKind = SubtitleKind.EXTERNAL_DISCOVER),
            media(alliance = "字幕组2").also { target = it },
            media(alliance = "字幕组3"),
            media(alliance = "字幕组4"),
            media(alliance = "字幕组5"),
        )
        savedDefaultPreference.value = DEFAULT_PREFERENCE
        assertEquals(target, selector.trySelectDefault())
    }
}
