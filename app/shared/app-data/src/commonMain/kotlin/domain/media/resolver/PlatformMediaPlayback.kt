/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.resolver

import org.openani.mediamp.source.MediaData

/** 平台播放准备及其临时文件，文件在播放器停止使用媒体后释放。 */
internal class PlatformMediaPlayback(
    val data: MediaData,
    private val cleanup: () -> Unit = {},
) : AutoCloseable {
    override fun close() = cleanup()
}

internal expect suspend fun preparePlatformMediaPlayback(data: MediaData): PlatformMediaPlayback
