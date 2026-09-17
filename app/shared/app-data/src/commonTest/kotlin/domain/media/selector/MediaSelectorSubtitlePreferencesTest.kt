/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.selector

import me.him188.ani.app.domain.media.selector.testFramework.assertMedias
import me.him188.ani.app.domain.media.selector.testFramework.runSimpleMediaSelectorTestSuite
import me.him188.ani.datasources.api.SubtitleKind
import me.him188.ani.utils.platform.Arch
import me.him188.ani.utils.platform.Platform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MediaSelectorSubtitlePreferencesTest {
    @Test
    fun `desktop players support supplied subtitles on all architectures`() {
        val platforms = listOf(
            Platform.MacOS(Arch.AARCH64),
            Platform.MacOS(Arch.X86_64),
            Platform.Windows(Arch.AARCH64),
            Platform.Windows(Arch.X86_64),
            Platform.Linux(Arch.X86_64),
        )
        for (platform in platforms) {
            val preferences = MediaSelectorSubtitlePreferences.forPlatform(platform)
            for (kind in SubtitleKind.entries) {
                assertEquals(
                    if (kind == SubtitleKind.EXTERNAL_DISCOVER) SubtitleKindPreference.HIDE
                    else SubtitleKindPreference.NORMAL,
                    preferences[kind],
                    "$platform: $kind",
                )
            }
        }
    }

    @Test
    fun `macOS includes and automatically selects internal subtitles`() =
        checkMacOSSelection(SubtitleKind.CLOSED)

    @Test
    fun `macOS includes and automatically selects potentially internal subtitles`() =
        checkMacOSSelection(SubtitleKind.CLOSED_OR_EXTERNAL_DISCOVER)

    @Test
    fun `macOS excludes external subtitles without files`() =
        checkMacOSSelection(SubtitleKind.EXTERNAL_DISCOVER, included = false)

    private fun checkMacOSSelection(kind: SubtitleKind, included: Boolean = true) = runSimpleMediaSelectorTestSuite(
        buildTest = {
            initSubject("Subtitle test")
            preferenceApi.mediaSelectorContext.value = preferenceApi.mediaSelectorContext.value.copy(
                subjectFinished = false,
            )
            preferenceApi.setSubtitlePreferences(
                MediaSelectorSubtitlePreferences.forPlatform(Platform.MacOS(Arch.AARCH64)),
            )
            mediaApi.addMedia(
                media(
                    subjectName = "Subtitle test",
                    mediaId = "subtitle-test",
                    originalTitle = "[Test] Subtitle test - 01 [1080p HEVC-10bit AAC].mkv",
                    subtitleKind = kind,
                ),
            )
        },
    ) {
        assertMedias {
            single().assert(
                included = included,
                exclusionReason = if (included) null else MediaExclusionReason.UnsupportedByPlatformPlayer,
            )
        }
        if (included) {
            assertEquals("subtitle-test", selector.trySelectDefault()?.mediaId)
        } else {
            assertNull(selector.trySelectDefault())
        }
    }
}
