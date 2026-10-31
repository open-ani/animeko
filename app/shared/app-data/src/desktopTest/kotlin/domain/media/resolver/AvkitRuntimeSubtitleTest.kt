/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.resolver

import kotlinx.io.Buffer
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import kotlin.test.Test
import kotlin.test.assertEquals

/** 输入文件由 Apple FFmpeg runtime 将双语、重叠 SRT 规范化为 Matroska 后提供。 */
@EnabledIfEnvironmentVariable(named = "ANI_AVKIT_SUBTITLE_FIXTURE", matches = ".+")
class AvkitRuntimeSubtitleTest {
    @Test
    fun `runtime exported subtitle packets retain their text and absolute timestamps`() {
        val output = Buffer()
        SystemFileSystem.source(Path(checkNotNull(System.getenv("ANI_AVKIT_SUBTITLE_FIXTURE")))).buffered().use {
            exportMatroskaSubtitleAsSrt(it, output)
        }
        assertEquals(
            "1\n00:00:05,000 --> 00:00:08,500\n你好\nHello\n\n2\n00:00:07,000 --> 00:00:09,000\nOverlap\n\n",
            output.readString(),
        )
    }
}
