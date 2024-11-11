/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.platform

import kotlin.test.Test
import kotlin.test.assertEquals

// Code written by Copilot, tests written by ChatGPT
class StringsTest {
    @Test
    fun deleteMatches() {
        val text = StringBuilder("This is a test teest string")
        val result = text.deleteMatches(Regex("t(e)*st"))
        assertEquals("This is a   string", result.toString())
    }

    @Test
    fun `deleteMatches no match`() {
        val text = StringBuilder("This is a test teest string")
        val result = text.deleteMatches(Regex("foot(e)*st"))
        assertEquals("This is a test teest string", result.toString())
    }

    @Test
    fun `deleteMatches full match`() {
        val text = StringBuilder("This is a test teest string")
        val result = text.deleteMatches(Regex(".+"))
        assertEquals("", result.toString())
    }


    @Test
    fun `replaceMatches replaces all numeric matches with mapped values`() {
        val sb = StringBuilder("abc123def456ghi")
        sb.replaceMatches(Regex("\\d+")) { "NUM" }
        assertEquals("abcNUMdefNUMghi", sb.toString())
    }

    @Test
    fun `replaceMatches handles no matches gracefully`() {
        val sb = StringBuilder("abcdefghi")
        sb.replaceMatches(Regex("\\d+")) { "NUM" }
        assertEquals("abcdefghi", sb.toString())
    }

    @Test
    fun `replaceMatches replaces special characters`() {
        val sb = StringBuilder("hello@world! how's it going?")
        sb.replaceMatches(Regex("[@!'?]")) { "_" }
        assertEquals("hello_world_ how_s it going_", sb.toString())
    }

    @Test
    fun `replaceMatches handles replacement with match content`() {
        val sb = StringBuilder("apple orange banana")
        sb.replaceMatches(Regex("\\b[a-z]+\\b")) { match -> match.value.uppercase() }
        assertEquals("APPLE ORANGE BANANA", sb.toString())
    }

    @Test
    fun `replaceMatches replaces overlapping patterns correctly`() {
        val sb = StringBuilder("ababa")
        sb.replaceMatches(Regex("aba")) { "X" }
        assertEquals("Xba", sb.toString())
    }

    @Test
    fun `replaceMatches handles empty StringBuilder`() {
        val sb = StringBuilder("")
        sb.replaceMatches(Regex("\\d+")) { "NUM" }
        assertEquals("", sb.toString())
    }

    @Test
    fun `replaceMatches with single character matches`() {
        val sb = StringBuilder("a1b2c3")
        sb.replaceMatches(Regex("\\d")) { "X" }
        assertEquals("aXbXcX", sb.toString())
    }


    @Test
    fun `deletePrefix removes the prefix when it exists`() {
        val sb = StringBuilder("HelloWorld")
        sb.deletePrefix("Hello")
        assertEquals("World", sb.toString())
    }

    @Test
    fun `deletePrefix does nothing when prefix is not present`() {
        val sb = StringBuilder("HelloWorld")
        sb.deletePrefix("Test")
        assertEquals("HelloWorld", sb.toString())
    }

    @Test
    fun `deletePrefix handles empty prefix`() {
        val sb = StringBuilder("HelloWorld")
        sb.deletePrefix("")
        assertEquals("HelloWorld", sb.toString())
    }

    @Test
    fun `deletePrefix on empty StringBuilder does nothing`() {
        val sb = StringBuilder("")
        sb.deletePrefix("Hello")
        assertEquals("", sb.toString())
    }

    @Test
    fun `deleteSuffix removes the suffix when it exists`() {
        val sb = StringBuilder("HelloWorld")
        sb.deleteSuffix("World")
        assertEquals("Hello", sb.toString())
    }

    @Test
    fun `deleteSuffix does nothing when suffix is not present`() {
        val sb = StringBuilder("HelloWorld")
        sb.deleteSuffix("Test")
        assertEquals("HelloWorld", sb.toString())
    }

    @Test
    fun `deleteSuffix handles empty suffix`() {
        val sb = StringBuilder("HelloWorld")
        sb.deleteSuffix("")
        assertEquals("HelloWorld", sb.toString())
    }

    @Test
    fun `deleteSuffix on empty StringBuilder does nothing`() {
        val sb = StringBuilder("")
        sb.deleteSuffix("World")
        assertEquals("", sb.toString())
    }

    @Test
    fun `deleteInfix removes the first occurrence of infix when it exists`() {
        val sb = StringBuilder("HelloBeautifulWorld")
        sb.deleteInfix("Beautiful")
        assertEquals("HelloWorld", sb.toString())
    }

    @Test
    fun `deleteInfix does nothing when infix is not present`() {
        val sb = StringBuilder("HelloWorld")
        sb.deleteInfix("Test")
        assertEquals("HelloWorld", sb.toString())
    }

    @Test
    fun `deleteInfix removes only the first occurrence of infix when it appears multiple times`() {
        val sb = StringBuilder("HelloWorldHelloWorld")
        sb.deleteInfix("World")
        assertEquals("HelloHelloWorld", sb.toString())
    }

    @Test
    fun `deleteInfix handles empty infix`() {
        val sb = StringBuilder("HelloWorld")
        sb.deleteInfix("")
        assertEquals("HelloWorld", sb.toString())
    }

    @Test
    fun `deleteInfix on empty StringBuilder does nothing`() {
        val sb = StringBuilder("")
        sb.deleteInfix("Hello")
        assertEquals("", sb.toString())
    }

    @Test
    fun `trimSB removes leading and trailing whitespace`() {
        val sb = StringBuilder("   Hello World   ")
        sb.trimSB()
        assertEquals("Hello World", sb.toString())
    }

    @Test
    fun `trimSB removes only leading whitespace`() {
        val sb = StringBuilder("   LeadingWhitespace")
        sb.trimSB()
        assertEquals("LeadingWhitespace", sb.toString())
    }

    @Test
    fun `trimSB removes only trailing whitespace`() {
        val sb = StringBuilder("TrailingWhitespace   ")
        sb.trimSB()
        assertEquals("TrailingWhitespace", sb.toString())
    }

    @Test
    fun `trimSB does nothing on string without whitespace`() {
        val sb = StringBuilder("NoWhitespace")
        sb.trimSB()
        assertEquals("NoWhitespace", sb.toString())
    }

    @Test
    fun `trimSB on empty StringBuilder does nothing`() {
        val sb = StringBuilder("")
        sb.trimSB()
        assertEquals("", sb.toString())
    }

    @Test
    fun `trimSB on StringBuilder with only whitespace`() {
        val sb = StringBuilder("   ")
        sb.trimSB()
        assertEquals("", sb.toString())
    }

    @Test
    fun `trimSB on StringBuilder with mixed whitespace and text`() {
        val sb = StringBuilder("\n\t   Mixed Whitespace\t \n")
        sb.trimSB()
        assertEquals("Mixed Whitespace", sb.toString())
    }

    @Test
    fun `trimSB preserves inner whitespace`() {
        val sb = StringBuilder("   Inner   Whitespace   ")
        sb.trimSB()
        assertEquals("Inner   Whitespace", sb.toString())
    }
}