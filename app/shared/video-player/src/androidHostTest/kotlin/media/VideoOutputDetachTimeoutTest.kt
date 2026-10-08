/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.media

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoTimeoutException
import org.openani.mediamp.PlaybackErrorCode
import org.openani.mediamp.PlaybackException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(UnstableApi::class)
class VideoOutputDetachTimeoutTest {
    @Test
    fun detectsDetachTimeoutWrappedByMediamp() {
        // MediaMP -> ExoPlaybackException -> ExoTimeoutException, as reported in user logs
        val error = PlaybackException(
            PlaybackErrorCode.INTERNAL,
            "ExoPlayer playback failed: ERROR_CODE_TIMEOUT (1003): Unexpected runtime error",
            RuntimeException(
                "Unexpected runtime error",
                ExoTimeoutException(ExoTimeoutException.TIMEOUT_OPERATION_DETACH_SURFACE),
            ),
        )
        assertTrue(error.isVideoOutputDetachTimeout())
    }

    @Test
    fun ignoresOtherTimeoutsAndErrors() {
        assertFalse(
            RuntimeException(ExoTimeoutException(ExoTimeoutException.TIMEOUT_OPERATION_RELEASE))
                .isVideoOutputDetachTimeout(),
        )
        assertFalse(PlaybackException(PlaybackErrorCode.IO, "Source error").isVideoOutputDetachTimeout())
    }
}
