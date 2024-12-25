/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource

import me.him188.ani.app.domain.mediasource.MediaListFilters.removeSpecials
import me.him188.ani.test.TestFactory
import me.him188.ani.test.runDynamicTests
import kotlin.test.Test
import kotlin.test.assertEquals


class StringMatcherTest {

    @Test
    fun `test empty strings`() {
        val result = StringMatcher.calculateMatchRate("", "")
        assertEquals(100, result, "Empty strings should have 100% match")
    }

    @Test
    fun `test identical strings`() {
        val result = StringMatcher.calculateMatchRate("kotlin", "kotlin")
        assertEquals(100, result, "Identical strings should have 100% match")
    }

    @Test
    fun `test single character difference`() {
        val result = StringMatcher.calculateMatchRate("kotlin", "kotli")
        // 'kotli' is missing the last character 'n', distance = 1, 
        // maxLen = 6 => similarity = 1 - (1/6) => ~83.33
        // After multiplying by 100 => ~83
        assertEquals(83, result, "Strings with one character difference should have around 83% match")
    }

    @Test
    fun `test partial match`() {
        val result = StringMatcher.calculateMatchRate("hello", "hallo")
        // For reference: distance(hello, hallo) is 1 (substituting 'e' for 'a')
        // maxLen = 5 => similarity = 1 - (1/5) => 0.8 => 80
        assertEquals(80, result, "Expected around 80% similarity for 'hello' and 'hallo'")
    }

    @Test
    fun `test completely different strings`() {
        val result = StringMatcher.calculateMatchRate("abcd", "wxyz")
        // distance can be up to 4 if all different
        // maxLen = 4 => similarity = 1 - (4/4) = 0 => 0%
        assertEquals(0, result, "Completely different strings should have 0% match")
    }

    @Test
    fun `test one string empty`() {
        val result = StringMatcher.calculateMatchRate("kotlin", "")
        // distance is length of "kotlin", i.e., 6
        // maxLen = 6 => similarity = 1 - (6/6) = 0 => 0%
        assertEquals(0, result, "Non-empty vs. empty string should have 0% match")
    }

    @TestFactory
    fun `test matches`() = runDynamicTests {
        infix fun String.matches(another: String): Pair<String, String> = this to another
        infix fun Pair<String, String>.at(expected: Int) {
            add("$first matches $second at $expected") {
                val result = StringMatcher.calculateMatchRate(first, second)
                assertEquals(expected, result)
            }
        }

        // 语法: 有错误的名字 matches 正确名称 at 匹配率 

        // 错了一个字
        "哥特萝莉侦探事件薄" matches "哥特萝莉侦探事件簿" at 88

        // 这说明 <80% 可能会匹配到第二季
        "哥特萝莉侦探事件簿" matches "哥特萝莉侦探事件簿第二季" at 75

        // #780
        "败犬女主太多了" matches "败犬女主太多啦" at 85

        // 此方法无法识别这种区别
        "别当欧尼酱了" matches "别当哥哥了" at 50

        "地狱少女第一季" matches "地。 ―关于地球的运动―" at 8
        "地狱少女第一季" matches "地。" at 14
        val chiRemoved = removeSpecials(
            "地。 ―关于地球的运动―",
            removeWhitespace = true,
            replaceNumbers = true,
        )
        "地狱少女第一季" matches chiRemoved at 11
        "地狱少女" matches "地。" at 25
    }

    @Test
    fun `test GOSICK2`() {
        val result = StringMatcher.calculateMatchRate("哥特萝莉侦探事件簿", "哥特萝莉侦探事件薄")
        // distance is length of "kotlin", i.e., 6
        // maxLen = 6 => similarity = 1 - (6/6) = 0 => 0%
        assertEquals(88, result)
    }
}