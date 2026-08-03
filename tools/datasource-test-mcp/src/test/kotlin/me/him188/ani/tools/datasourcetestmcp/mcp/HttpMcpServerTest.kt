/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tools.datasourcetestmcp.mcp

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HttpMcpServerTest {
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

    private fun withMcpServer(block: suspend (client: HttpClient) -> Unit) = testApplication {
        application {
            mcpServerModule(McpRequestHandler(listOf(echoTool), json))
        }
        block(client)
    }

    private suspend fun HttpClient.postRpc(body: String, path: String = "/mcp"): HttpResponse =
        post(path) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    private suspend fun HttpResponse.rpcBody(): JsonObject =
        json.parseToJsonElement(bodyAsText()).jsonObject

    @Test
    fun `initialize echoes supported protocol version and returns server info`() = withMcpServer { client ->
        val response = client.postRpc(
            """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26"}}""",
        )
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.contentType()?.match(ContentType.Application.Json) == true)

        val result = response.rpcBody().getValue("result").jsonObject
        assertEquals("2025-03-26", result.getValue("protocolVersion").jsonPrimitive.content)
        assertEquals(
            "animeko-datasource-test-mcp",
            result.getValue("serverInfo").jsonObject.getValue("name").jsonPrimitive.content,
        )
    }

    @Test
    fun `initialize falls back to latest version when requested one is unsupported`() = withMcpServer { client ->
        val response = client.postRpc(
            """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"1999-01-01"}}""",
        )
        val result = response.rpcBody().getValue("result").jsonObject
        assertEquals("2025-06-18", result.getValue("protocolVersion").jsonPrimitive.content)
    }

    @Test
    fun `tools list returns registered tools`() = withMcpServer { client ->
        val response = client.postRpc("""{"jsonrpc":"2.0","id":2,"method":"tools/list"}""")
        assertEquals(HttpStatusCode.OK, response.status)

        val tools = response.rpcBody().getValue("result").jsonObject.getValue("tools").jsonArray
        assertEquals(1, tools.size)
        assertEquals("echo", tools[0].jsonObject.getValue("name").jsonPrimitive.content)
    }

    @Test
    fun `tools call returns structured content`() = withMcpServer { client ->
        val response = client.postRpc(
            """{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"echo","arguments":{"hello":"world"}}}""",
        )
        assertEquals(HttpStatusCode.OK, response.status)

        val result = response.rpcBody().getValue("result").jsonObject
        assertEquals(
            "world",
            result.getValue("structuredContent").jsonObject.getValue("hello").jsonPrimitive.content,
        )
        assertEquals("false", result.getValue("isError").jsonPrimitive.content)
    }

    @Test
    fun `tools call propagates coroutine cancellation`() = runTest {
        val cancellingTool = McpToolRegistration(
            McpTool(
                name = "cancel",
                description = "cancel",
                inputSchema = buildJsonObject { put("type", "object") },
            ),
        ) { awaitCancellation() }
        val handler = McpRequestHandler(listOf(cancellingTool), json)
        val request = RpcRequest(
            id = JsonPrimitive(1),
            method = "tools/call",
            params = buildJsonObject {
                put("name", "cancel")
                put("arguments", buildJsonObject {})
            },
        )

        assertFailsWith<TimeoutCancellationException> {
            withTimeout(1) { handler.handle(request) }
        }
    }

    @Test
    fun `tool timing out internally is reported as a structured error`() = runTest {
        // 工具内部漏网的 withTimeout 抛的是 TimeoutCancellationException, 表示这次调用超时而非请求被取消,
        // 不能让它冒到 transport 层把整个响应打掉.
        val timingOutTool = McpToolRegistration(
            McpTool(
                name = "slow",
                description = "slow",
                inputSchema = buildJsonObject { put("type", "object") },
            ),
        ) { withTimeout(1) { awaitCancellation() } }
        val handler = McpRequestHandler(listOf(timingOutTool), json)
        val request = RpcRequest(
            id = JsonPrimitive(1),
            method = "tools/call",
            params = buildJsonObject {
                put("name", "slow")
                put("arguments", buildJsonObject {})
            },
        )

        val result = assertNotNull(handler.handle(request)).result
        val content = assertNotNull(result).jsonObject
        assertEquals("true", content.getValue("isError").jsonPrimitive.content)
        val structured = content.getValue("structuredContent").jsonObject
        assertEquals("false", structured.getValue("ok").jsonPrimitive.content)
        assertTrue(
            structured.getValue("summary").jsonPrimitive.content.contains("TimeoutCancellationException"),
            structured.getValue("summary").jsonPrimitive.content,
        )
    }

    @Test
    fun `unknown tool is an rpc error`() = withMcpServer { client ->
        val response = client.postRpc("""{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"nope"}}""")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(
            "-32602",
            response.rpcBody().getValue("error").jsonObject.getValue("code").jsonPrimitive.content,
        )
    }

    @Test
    fun `root path is an alias of the mcp endpoint`() = withMcpServer { client ->
        val response = client.postRpc("""{"jsonrpc":"2.0","id":1,"method":"ping"}""", path = "/")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("1", response.rpcBody().getValue("id").jsonPrimitive.content)
    }

    @Test
    fun `notification returns 202 with no body`() = withMcpServer { client ->
        val response = client.postRpc("""{"jsonrpc":"2.0","method":"notifications/initialized"}""")
        assertEquals(HttpStatusCode.Accepted, response.status)
        assertEquals("", response.bodyAsText())
    }

    @Test
    fun `malformed json is a parse error`() = withMcpServer { client ->
        val response = client.postRpc("""{"jsonrpc":""")
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(
            "-32700",
            response.rpcBody().getValue("error").jsonObject.getValue("code").jsonPrimitive.content,
        )
    }

    @Test
    fun `batch requests are rejected`() = withMcpServer { client ->
        val response = client.postRpc("""[{"jsonrpc":"2.0","id":1,"method":"ping"}]""")
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(
            "-32600",
            response.rpcBody().getValue("error").jsonObject.getValue("code").jsonPrimitive.content,
        )
    }

    @Test
    fun `get is not allowed - no sse support`() = withMcpServer { client ->
        val response = client.get("/mcp")
        assertEquals(HttpStatusCode.MethodNotAllowed, response.status)
        assertEquals("POST", response.headers[HttpHeaders.Allow])
    }

    @Test
    fun `non-local origin is rejected`() = withMcpServer { client ->
        val response = client.post("/mcp") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Origin, "https://evil.example.com")
            setBody("""{"jsonrpc":"2.0","id":1,"method":"ping"}""")
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `local origin is accepted`() = withMcpServer { client ->
        val response = client.post("/mcp") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Origin, "http://localhost:5173")
            setBody("""{"jsonrpc":"2.0","id":1,"method":"ping"}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }
}
