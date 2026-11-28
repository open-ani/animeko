/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.media

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.Renderer
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(UnstableApi::class)
class SubtitleDelayRendererTest {
    @Test
    fun `render clock supports delaying advancing and seeking without changing elapsed time`() {
        val calls = mutableListOf<Pair<String, List<Any?>>>()
        val delegate = Proxy.newProxyInstance(Renderer::class.java.classLoader, arrayOf(Renderer::class.java)) { _, method, arguments ->
            calls += method.name to arguments.orEmpty().toList()
            when (method.returnType) {
                Long::class.javaPrimitiveType -> 20_000L
                Boolean::class.javaPrimitiveType -> true
                Int::class.javaPrimitiveType -> 0
                else -> null
            }
        } as Renderer
        var delay = 500L
        val renderer = SubtitleDelayRenderer(delegate) { delay }
        renderer.render(10_000_000, 50_000_000)
        assertEquals("render" to listOf(9_500_000L, 50_000_000L), calls.last())
        delay = -500
        renderer.render(10_000_000, 50_000_000)
        assertEquals("render" to listOf(10_500_000L, 50_000_000L), calls.last())
        renderer.resetPosition(10_000_000, false)
        assertEquals("resetPosition" to listOf(10_500_000L, false), calls.last())
        renderer.getDurationToProgressUs(10_000_000, 50_000_000)
        assertEquals("getDurationToProgressUs" to listOf(10_500_000L, 50_000_000L), calls.last())
    }
}
