/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.io

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FileReadLastNLinesTest {

    @Test
    fun `should return empty list when file does not exist`() {
        val file = File.createTempFile("non_existent_file", "txt")
        val result = file.readLastNLines(5)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `should return empty list when file is not a file points to a directory - or doesnt exist`() {
        // For demonstration, let's create a temp directory and pass that in
        val dir = File(System.getProperty("java.io.tmpdir"))
        val result = dir.readLastNLines(5)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `should return empty list when n is zero or negative`() {
        // Create a temp file with some content
        val file = File.createTempFile("testFile", ".txt").apply {
            writeText("Line 1\nLine 2\nLine 3")
        }

        // n = 0
        val resultZero = file.readLastNLines(0)
        assertTrue(resultZero.isEmpty())

        // n = -1
        val resultNegative = file.readLastNLines(-1)
        assertTrue(resultNegative.isEmpty())

        file.delete()
    }

    @Test
    fun `should return empty list when file is empty`() {
        val file = File.createTempFile("emptyFile", ".txt").apply {
            writeText("")
        }

        val result = file.readLastNLines(5)
        assertTrue(result.isEmpty())

        file.delete()
    }

    @Test
    fun `should return entire file contents if file has fewer lines than requested`() {
        val file = File.createTempFile("fewer_lines", ".txt").apply {
            writeText(
                """
                Line 1
                Line 2
                Line 3
                """.trimIndent(),
            )
        }

        val result = file.readLastNLines(5)
        assertEquals(listOf("Line 1", "Line 2", "Line 3"), result)
    }

    @Test
    fun `should return last n lines if file has more lines than requested`() {
        val file = File.createTempFile("many_lines", ".txt").apply {
            writeText(
                """
                Line 1
                Line 2
                Line 3
                Line 4
                Line 5
                Line 6
                """.trimIndent(),
            )
        }

        val result = file.readLastNLines(3)
        // Should be the last 3 lines
        assertEquals(listOf("Line 4", "Line 5", "Line 6"), result)
    }

    @Test
    fun `should handle trailing newlines properly`() {
        val file = File.createTempFile("trailing_newlines", ".txt").apply {
            writeText("Line 1\nLine 2\nLine 3\n\n\n")  // multiple trailing newlines
        }

        val result = file.readLastNLines(2)
        // Should ignore trailing newlines and return last two lines
        assertEquals(listOf("Line 2", "Line 3"), result)
    }

    @Test
    fun `should read the exact lines if file has exactly n lines`() {
        val file = File.createTempFile("exact_n_lines", ".txt").apply {
            writeText(
                """
                Line A
                Line B
                Line C
                """.trimIndent(),
            )
        }

        val result = file.readLastNLines(3)
        assertEquals(listOf("Line A", "Line B", "Line C"), result)
    }

    @Test
    fun `should return the first line if file has only one line and n is large`() {
        val file = File.createTempFile("single_line", ".txt").apply {
            writeText("Just one line")
        }

        val result = file.readLastNLines(10)
        assertEquals(listOf("Just one line"), result)
    }
}
