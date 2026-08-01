/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.danmaku.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LruCacheTest {
    @Test
    fun `hit returns the same instance and does not create again`() {
        val cache = LruCache<String, Any>(maxEntries = 4)
        var creations = 0
        val first = cache.getOrPut("a") { creations++; Any() }
        val second = cache.getOrPut("a") { creations++; Any() }

        assertSame(first, second)
        assertEquals(1, creations)
        assertEquals(1, cache.size)
    }

    @Test
    fun `evicts least recently used when over capacity`() {
        val cache = LruCache<String, String>(maxEntries = 3)
        cache.getOrPut("a") { "a" }
        cache.getOrPut("b") { "b" }
        cache.getOrPut("c") { "c" }
        cache.getOrPut("d") { "d" }

        assertEquals(3, cache.size)
        assertFalse(cache.containsKey("a"))
        assertEquals(listOf("b", "c", "d"), cache.keysInEvictionOrder())
    }

    @Test
    fun `access refreshes recency`() {
        val cache = LruCache<String, String>(maxEntries = 3)
        cache.getOrPut("a") { "a" }
        cache.getOrPut("b") { "b" }
        cache.getOrPut("c") { "c" }
        // 重新访问 a, 使 b 变成最久未使用
        cache.getOrPut("a") { error("should be a hit") }
        cache.getOrPut("d") { "d" }

        assertTrue(cache.containsKey("a"))
        assertFalse(cache.containsKey("b"))
        assertEquals(listOf("c", "a", "d"), cache.keysInEvictionOrder())
    }

    @Test
    fun `repeated spam of one key never grows the cache`() {
        val cache = LruCache<String, String>(maxEntries = 256)
        repeat(1000) { cache.getOrPut("233333333") { "x" } }
        assertEquals(1, cache.size)
    }

    @Test
    fun `clear empties the cache`() {
        val cache = LruCache<String, String>(maxEntries = 4)
        cache.getOrPut("a") { "a" }
        cache.clear()
        assertEquals(0, cache.size)
    }
}

class DanmakuRenderKeyTest {
    private fun key(
        text: String = "233",
        textStyle: TextStyle = TextStyle(fontSize = 18.sp, color = Color.White, fontWeight = FontWeight.W600),
        strokeColor: Color = Color.Black,
        strokeWidth: Float = 4f,
        shadow: Shadow? = null,
    ) = DanmakuRenderKey(text, textStyle, strokeColor, strokeWidth, shadow)

    @Test
    fun `identical fields are equal and share a hash code`() {
        assertEquals(key(), key())
        assertEquals(key().hashCode(), key().hashCode())
    }

    @Test
    fun `text is part of the key`() {
        assertNotEquals(key(text = "233"), key(text = "666"))
    }

    @Test
    fun `resolved text style carries color, size, weight and underline`() {
        val base = TextStyle(fontSize = 18.sp, color = Color.White, fontWeight = FontWeight.W600)
        assertNotEquals(key(textStyle = base), key(textStyle = base.copy(color = Color.Red)))
        assertNotEquals(key(textStyle = base), key(textStyle = base.copy(fontSize = 24.sp)))
        assertNotEquals(key(textStyle = base), key(textStyle = base.copy(fontWeight = FontWeight.W300)))
        assertNotEquals(
            key(textStyle = base),
            key(textStyle = base.copy(textDecoration = TextDecoration.Underline)),
        )
    }

    @Test
    fun `stroke and shadow are part of the key`() {
        assertNotEquals(key(strokeWidth = 4f), key(strokeWidth = 2f))
        assertNotEquals(key(strokeColor = Color.Black), key(strokeColor = Color.DarkGray))
        assertNotEquals(key(shadow = null), key(shadow = Shadow(Color.Black, Offset(2f, 2f), 2f)))
    }

    @Test
    fun `cache reuses one entry for identical spam danmaku`() {
        val cache = LruCache<DanmakuRenderKey, String>(maxEntries = 64)
        repeat(50) { cache.getOrPut(key()) { "entry" } }
        assertEquals(1, cache.size)
    }
}
