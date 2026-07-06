/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tools.datasourcetestmcp.mcp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StdioMcpServerTest {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        prettyPrint = true
    }

    private val echoTool = McpToolRegistration(
        McpTool(
            name = "echo",
            description = "echo",
            inputSchema = buildJsonObject { put("type", "object") },
        ),
    ) { args -> args }

    private fun runServer(input: String): String {
        val output = ByteArrayOutputStream()
        StdioMcpServer(
            input = ByteArrayInputStream(input.toByteArray()),
            output = output,
            registrations = listOf(echoTool),
            json = json,
        ).run()
        return output.toString(Charsets.UTF_8)
    }

    @Test
    fun `newline framing - responses are single lines`() {
        val output = runServer(
            """
            {"jsonrpc":"2.0","id":1,"method":"initialize"}
            {"jsonrpc":"2.0","id":2,"method":"tools/list"}
            """.trimIndent() + "\n",
        )

        val lines = output.trim().lines()
        assertEquals(2, lines.size, "expected one line per response, got: $output")

        val initialize = json.parseToJsonElement(lines[0]).jsonObject
        assertEquals("1", initialize.getValue("id").jsonPrimitive.content)
        assertEquals(
            "animeko-datasource-test-mcp",
            initialize.getValue("result").jsonObject
                .getValue("serverInfo").jsonObject
                .getValue("name").jsonPrimitive.content,
        )

        val toolsList = json.parseToJsonElement(lines[1]).jsonObject
        val tools = toolsList.getValue("result").jsonObject.getValue("tools").jsonArray
        assertEquals(1, tools.size)
        assertEquals("echo", tools[0].jsonObject.getValue("name").jsonPrimitive.content)
    }

    @Test
    fun `newline framing - tools call returns structured content`() {
        val output = runServer(
            """{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"echo","arguments":{"hello":"world"}}}""" + "\n",
        )

        val lines = output.trim().lines()
        assertEquals(1, lines.size)
        val response = json.parseToJsonElement(lines[0]).jsonObject
        val result = response.getValue("result").jsonObject
        assertEquals(
            "world",
            result.getValue("structuredContent").jsonObject.getValue("hello").jsonPrimitive.content,
        )
        assertEquals("false", result.getValue("isError").jsonPrimitive.content)
    }

    @Test
    fun `newline framing - unknown tool is an rpc error`() {
        val output = runServer(
            """{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"nope"}}""" + "\n",
        )
        val response = json.parseToJsonElement(output.trim()).jsonObject
        assertEquals("-32602", response.getValue("error").jsonObject.getValue("code").jsonPrimitive.content)
    }

    @Test
    fun `legacy content-length framing is detected and mirrored`() {
        val body = """{"jsonrpc":"2.0","id":1,"method":"ping"}"""
        val output = runServer("Content-Length: ${body.toByteArray().size}\r\n\r\n$body")

        assertTrue(output.startsWith("Content-Length: "), "expected Content-Length response, got: $output")
        val responseBody = output.substringAfter("\r\n\r\n")
        val response = json.parseToJsonElement(responseBody).jsonObject
        assertEquals("1", response.getValue("id").jsonPrimitive.content)
    }
}
