/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.danmaku.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle

/**
 * 一条弹幕的渲染结果. 排版结果在创建时就有, 位图在第一次绘制时才光栅化.
 *
 * 同一个 [DanmakuRenderCache] 里内容和样式相同的弹幕共用同一个 entry, 因此刷屏弹幕只会测量和光栅化一次.
 */
internal class DanmakuRenderEntry(
    val textLayout: TextLayoutResult,
) {
    /**
     * 只在绘制线程 (UI 线程) 上读写.
     */
    var imageBitmap: ImageBitmapWithOffset? = null
}

/**
 * [DanmakuRenderEntry] 的缓存 key. 包含所有会影响渲染结果的字段.
 *
 * [textStyle] 是 `baseStyle` 与 [DanmakuStyle.styleForText] 合并后的结果, 已经包含了字号, 字重,
 * 解析后的颜色 (即 `enableColor` 的效果) 和 `isSelf` 的下划线.
 */
@Immutable
internal data class DanmakuRenderKey(
    val text: String,
    val textStyle: TextStyle,
    val strokeColor: Color,
    val strokeWidth: Float,
    val shadow: Shadow?,
)

/**
 * 每个 [DanmakuHostState] 一个的 LRU 缓存. 不是全局的, 随 host state 一起回收.
 *
 * 注意: 非线程安全, 只能在 UI 线程访问 (与 [androidx.compose.ui.text.TextMeasurer] 的约束一致).
 *
 * 本身是不透明的: 类型是 public 只是因为它出现在 public 的 [StyledDanmaku] 构造器签名里.
 */
@Stable
class DanmakuRenderCache internal constructor(
    maxEntries: Int = DEFAULT_MAX_ENTRIES,
) {
    private val lru = LruCache<DanmakuRenderKey, DanmakuRenderEntry>(maxEntries)

    internal val size: Int get() = lru.size

    internal fun getOrPut(key: DanmakuRenderKey, measure: () -> TextLayoutResult): DanmakuRenderEntry =
        lru.getOrPut(key) { DanmakuRenderEntry(measure()) }

    internal fun clear() = lru.clear()

    internal companion object {
        const val DEFAULT_MAX_ENTRIES = 256
    }
}

/**
 * 最简 LRU. 用 [LinkedHashMap] 的插入顺序做访问顺序: 命中时 remove + put 把 entry 移到队尾.
 */
internal class LruCache<K : Any, V : Any>(
    private val maxEntries: Int,
) {
    init {
        require(maxEntries > 0) { "maxEntries must be positive, but was $maxEntries" }
    }

    private val entries = LinkedHashMap<K, V>()

    val size: Int get() = entries.size

    fun getOrPut(key: K, create: () -> V): V {
        val hit = entries.remove(key)
        if (hit != null) {
            entries[key] = hit
            return hit
        }

        val created = create()
        entries[key] = created
        while (entries.size > maxEntries) {
            val eldest = entries.keys.firstOrNull() ?: break
            entries.remove(eldest)
        }
        return created
    }

    fun containsKey(key: K): Boolean = entries.containsKey(key)

    fun keysInEvictionOrder(): List<K> = entries.keys.toList()

    fun clear() {
        entries.clear()
    }
}
