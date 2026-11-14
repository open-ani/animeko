/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.details.components

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 覆盖 [formatCollectionCount] 的千位缩写规则.
 *
 * 缩写只在统计一行放不下时才启用 (见 [SubjectDetailsDefaults.CollectionData]), 因此这里独立校验规则本身.
 */
class SubjectCollectionCountFormatTest {
    @Test
    fun `counts up to one thousand stay unchanged`() {
        assertEquals("0", formatCollectionCount(0, abbreviate = true))
        assertEquals("1", formatCollectionCount(1, abbreviate = true))
        assertEquals("999", formatCollectionCount(999, abbreviate = true))
        assertEquals("1000", formatCollectionCount(1000, abbreviate = true))
    }

    @Test
    fun `counts between one and ten thousand keep one decimal`() {
        assertEquals("1.5k", formatCollectionCount(1555, abbreviate = true))
        assertEquals("1.2k", formatCollectionCount(1234, abbreviate = true))
        assertEquals("9.9k", formatCollectionCount(9999, abbreviate = true))
    }

    /** 小数位为 0 时省略 `.0`, 例如 9000 显示 `9k` 而不是 `9.0k`. */
    @Test
    fun `trailing zero fraction is omitted`() {
        assertEquals("4k", formatCollectionCount(4000, abbreviate = true))
        assertEquals("9k", formatCollectionCount(9000, abbreviate = true))
        assertEquals("1k", formatCollectionCount(1001, abbreviate = true))
    }

    /** 万以上只取整数部分, 不四舍五入, 结果不比实际值大. */
    @Test
    fun `counts from ten thousand use integer thousands`() {
        assertEquals("10k", formatCollectionCount(10000, abbreviate = true))
        assertEquals("35k", formatCollectionCount(35555, abbreviate = true))
        assertEquals("19k", formatCollectionCount(19999, abbreviate = true))
        assertEquals("99k", formatCollectionCount(99999, abbreviate = true))
    }

    /** 未启用缩写时必须原样输出, 宽屏上不能丢精度. */
    @Test
    fun `without abbreviation the exact count is kept`() {
        assertEquals("1555", formatCollectionCount(1555, abbreviate = false))
        assertEquals("35555", formatCollectionCount(35555, abbreviate = false))
        assertEquals("10000", formatCollectionCount(10000, abbreviate = false))
    }
}
