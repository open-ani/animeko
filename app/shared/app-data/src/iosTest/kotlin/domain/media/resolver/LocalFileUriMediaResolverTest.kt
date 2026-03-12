/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.resolver

import me.him188.ani.datasources.api.topic.ResourceLocation
import kotlin.test.Test
import kotlin.test.assertEquals

class LocalFileUriMediaResolverTest {
    @Test
    fun keepsRealMkvExtensionForContainedFiles() {
        assertEquals(
            ".mkv",
            inferExpectedFileExtension(
                filePath = "/tmp/video-file.mkv",
                fileType = ResourceLocation.LocalFile.FileType.CONTAINED,
                originalUri = null,
            ),
        )
    }

    @Test
    fun infersContainedExtensionFromOriginalUriWhenCacheFileHasNoSuffix() {
        assertEquals(
            ".mkv",
            inferExpectedFileExtension(
                filePath = "/tmp/video-file",
                fileType = ResourceLocation.LocalFile.FileType.CONTAINED,
                originalUri = "https://example.com/video-file.mkv?token=1",
            ),
        )
    }

    @Test
    fun fallsBackToMp4ForSuffixlessContainedFiles() {
        assertEquals(
            ".mp4",
            inferExpectedFileExtension(
                filePath = "/tmp/video-file",
                fileType = ResourceLocation.LocalFile.FileType.CONTAINED,
                originalUri = null,
            ),
        )
    }
}
