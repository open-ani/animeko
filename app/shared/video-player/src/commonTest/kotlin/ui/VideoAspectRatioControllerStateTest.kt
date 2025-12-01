/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.ui.framework.takeSnapshot
import org.openani.mediamp.InternalForInheritanceMediampApi
import org.openani.mediamp.features.AspectRatioMode
import org.openani.mediamp.features.VideoAspectRatio
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(InternalForInheritanceMediampApi::class)
private class TestVideoAspectRatio(initial: AspectRatioMode) : VideoAspectRatio {
    private val _flow = MutableStateFlow(initial)
    override val mode = _flow.asStateFlow()
    val setCalls = mutableListOf<AspectRatioMode>()

    override fun setMode(mode: AspectRatioMode) {
        setCalls += mode
        _flow.value = mode
    }
}

class VideoAspectRatioControllerStateTest {
    @Test
    fun `init - reading currentMode from videoAspectRatio`() = runTest {
        val videoAspectRatio = TestVideoAspectRatio(AspectRatioMode.STRETCH)
        val state = VideoAspectRatioControllerState(
            videoAspectRatio = videoAspectRatio,
            scope = backgroundScope,
        )
        takeSnapshot()

        assertEquals(AspectRatioMode.STRETCH, state.currentMode)
        assertEquals(
            VideoAspectRatioControllerState.Entries.indexOf(AspectRatioMode.STRETCH),
            state.currentIndex,
        )
    }

    @Test
    fun `collect - external changes reflect in currentMode`() = runTest {
        val videoAspectRatio = TestVideoAspectRatio(AspectRatioMode.FIT)
        val state = VideoAspectRatioControllerState(
            videoAspectRatio = videoAspectRatio,
            scope = backgroundScope,
        )
        takeSnapshot()
        assertEquals(AspectRatioMode.FIT, state.currentMode)

        val job = launch {
            videoAspectRatio.setMode(AspectRatioMode.CROP)
        }
        job.join()
        takeSnapshot()

        assertEquals(AspectRatioMode.CROP, state.currentMode)
        assertEquals(
            VideoAspectRatioControllerState.Entries.indexOf(AspectRatioMode.CROP),
            state.currentIndex,
        )
    }

    @Test
    fun `setMode - delegates to videoAspectRatio`() = runTest {
        val videoAspectRatio = TestVideoAspectRatio(AspectRatioMode.FIT)
        val state = VideoAspectRatioControllerState(
            videoAspectRatio = videoAspectRatio,
            scope = backgroundScope,
        )
        takeSnapshot()

        state.setMode(AspectRatioMode.STRETCH)
        takeSnapshot()

        assertEquals(listOf(AspectRatioMode.STRETCH), videoAspectRatio.setCalls)
        assertEquals(AspectRatioMode.STRETCH, state.currentMode)
        assertEquals(
            VideoAspectRatioControllerState.Entries.indexOf(AspectRatioMode.STRETCH),
            state.currentIndex,
        )
    }
}
