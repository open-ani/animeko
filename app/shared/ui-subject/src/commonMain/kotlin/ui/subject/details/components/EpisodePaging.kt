/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.details.components

/**
 * 桌面 (双栏/三栏) 选集网格的分页计算 (纯逻辑, 可单测).
 *
 * 设计基准 (见 `docs/subject-details-rewrite/02-rewrite-plan.md` §5):
 * - 每页最多 [rows] 行 (默认 2), 每行列数 [columnsPerRow] 随列宽动态传入;
 * - 每页容量 = rows × columnsPerRow;
 * - 初始页 = 包含当前观看进度集的页;
 * - 不足一页 (总集数 ≤ 容量) 时不分页.
 *
 * 手机端为 LazyRow 横滑不分页, 不使用本类型.
 */
data class EpisodePaging(
    val totalCount: Int,
    val capacity: Int,
) {
    init {
        require(capacity >= 1) { "capacity must be >= 1, but was $capacity" }
    }

    /** 总页数; 空列表为 0. */
    val pageCount: Int = if (totalCount <= 0) 0 else (totalCount + capacity - 1) / capacity

    /** 是否需要显示分页控件 (总集数超过单页容量). */
    val isPaged: Boolean get() = totalCount > capacity

    /** 给定 item 索引所在的页码 (0-based); 越界会被夹取到有效范围. */
    fun pageOf(itemIndex: Int): Int {
        if (pageCount == 0) return 0
        return (itemIndex.coerceAtLeast(0) / capacity).coerceIn(0, pageCount - 1)
    }

    /**
     * 初始页: 包含 [currentIndex] (当前进度集) 的页; [currentIndex] 无效 (<0) 时回退第 0 页.
     */
    fun initialPage(currentIndex: Int): Int = if (currentIndex < 0) 0 else pageOf(currentIndex)

    /** 某页对应的 item 索引区间 `[start, endExclusive)`; 末页会按实际数量截断. */
    fun itemRange(page: Int): IntRange {
        if (pageCount == 0) return IntRange.EMPTY
        val clamped = page.coerceIn(0, pageCount - 1)
        val start = clamped * capacity
        val end = (start + capacity).coerceAtMost(totalCount)
        return start until end
    }
}
